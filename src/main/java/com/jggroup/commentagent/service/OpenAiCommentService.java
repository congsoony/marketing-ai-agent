package com.jggroup.commentagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jggroup.commentagent.config.AppProperties;
import com.jggroup.commentagent.dto.DraftResult;
import com.jggroup.commentagent.dto.VideoContext;
import com.jggroup.commentagent.exception.ConfigMissingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * OpenAI 로 「영상 속 고민 한 줄」 + 「댓글 초안」 생성.
 * 요구사항 §2:
 *  - 자막(없으면 제목·설명) + 인기 댓글을 읽고 영상이 다루는 연애 고민을 먼저 정리
 *  - 영상을 본 시청자 페르소나로, 읽은 사람이 '운명원' 사주를 보고싶어지도록 유도
 *  - 40~120자, 영상 속 구체적 장면/말 1개 이상 언급, 영상마다 다르게
 *  - 금지: 링크 · 해시태그 · 운명원을 직접 이용해 본 것처럼 지어낸 후기
 * 생성 결과는 CommentRuleService 로 2차 검증 → 실패 시 사유를 붙여 재생성.
 *
 * 프롬프트 작성은 {@link CommentPromptFactory} 가 전담하므로 이 서비스는
 * 'OpenAI 호출 · 응답 파싱 · 규칙 검증 · 재생성' 흐름에만 집중한다.
 */
@Slf4j
@Service
public class OpenAiCommentService {

    private static final int MAX_ATTEMPTS = 4;

    private final AppProperties props;
    private final CommentRuleService ruleService;
    private final CommentPromptFactory promptFactory;
    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient client;

    public OpenAiCommentService(AppProperties props, CommentRuleService ruleService,
                                CommentPromptFactory promptFactory) {
        this.props = props;
        this.ruleService = ruleService;
        this.promptFactory = promptFactory;
        this.client = RestClient.builder()
                .baseUrl(props.getOpenai().getBaseUrl())
                .build();
    }

    public DraftResult generate(VideoContext ctx) {
        if (!props.getOpenai().isConfigured()) {
            throw new ConfigMissingException("OPENAI_API_KEY 가 설정되지 않았습니다. 환경변수로 주입하세요.");
        }

        String system = promptFactory.system();
        String baseUser = promptFactory.user(ctx);

        String feedback = "";
        String lastConcern = "";
        String lastComment = "";
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String userMsg = baseUser + feedback;
            JsonNode parsed = callOpenAi(system, userMsg);
            lastConcern = parsed.path("concern").asText("").strip();
            lastComment = parsed.path("comment").asText("").strip();

            CommentRuleService.Validation v = ruleService.validate(lastComment);
            if (v.valid()) {
                return new DraftResult(ctx.videoId(), lastConcern, lastComment,
                        ruleService.charCount(lastComment), ctx.hasTranscript(), attempt);
            }
            log.info("댓글 규칙 위반(attempt {}): {} | comment=\"{}\"", attempt, v.reason(), lastComment);
            feedback = promptFactory.retryFeedback(v.reason());
        }

        // 규칙 통과 실패 — 마지막 초안 반환(사용자가 화면에서 손볼 수 있음)
        log.warn("규칙 통과 실패 — 마지막 초안 반환 videoId={}", ctx.videoId());
        return new DraftResult(ctx.videoId(), lastConcern, lastComment,
                ruleService.charCount(lastComment), ctx.hasTranscript(), MAX_ATTEMPTS);
    }

    /** OpenAI Chat Completions 호출 (JSON 모드로 {"concern","comment"} 강제). */
    private JsonNode callOpenAi(String system, String userMsg) {
        AppProperties.OpenAi c = props.getOpenai();
        Map<String, Object> body = Map.of(
                "model", c.getModel(),
                "max_tokens", c.getMaxTokens(),
                "temperature", c.getTemperature(),
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", userMsg))
        );

        JsonNode resp = client.post()
                .uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + c.getApiKey())
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (resp == null) throw new IllegalStateException("OpenAI 응답 없음");
        String content = resp.path("choices").path(0).path("message").path("content").asText("");
        return extractJson(content);
    }

    /** 응답 텍스트에서 첫 '{' ~ 마지막 '}' 구간을 JSON 으로 파싱(코드펜스 등 안전 처리). */
    private JsonNode extractJson(String text) {
        try {
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return mapper.readTree(text.substring(start, end + 1));
            }
            return mapper.createObjectNode().put("concern", "").put("comment", text.strip());
        } catch (Exception e) {
            return mapper.createObjectNode().put("concern", "").put("comment", "");
        }
    }
}
