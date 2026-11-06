package com.jggroup.commentagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jggroup.commentagent.config.AppProperties;
import com.jggroup.commentagent.dto.DraftResult;
import com.jggroup.commentagent.dto.VideoContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Claude 로 「영상 속 고민 한 줄」 + 「댓글 초안」 생성.
 * 요구사항 §2:
 *  - 자막(없으면 제목·설명) + 인기 댓글을 읽고 영상이 다루는 연애 고민을 먼저 정리
 *  - 영상을 본 시청자 페르소나로, 읽은 사람이 '운명원' 사주를 보고싶어지도록 유도
 *  - 40~120자, 영상 속 구체적 장면/말 1개 이상 언급, 영상마다 다르게
 *  - 금지: 링크 · 해시태그 · 운명원을 직접 이용해 본 것처럼 지어낸 후기
 * 생성 결과는 CommentRuleService 로 2차 검증 → 실패 시 사유를 붙여 재생성.
 */
@Slf4j
@Service
public class ClaudeCommentService {

    private static final int MAX_ATTEMPTS = 4;

    private final AppProperties props;
    private final CommentRuleService ruleService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient client;

    public ClaudeCommentService(AppProperties props, CommentRuleService ruleService) {
        this.props = props;
        this.ruleService = ruleService;
        this.client = RestClient.builder()
                .baseUrl(props.getClaude().getBaseUrl())
                .build();
    }

    public DraftResult generate(VideoContext ctx) {
        if (!props.getClaude().isConfigured()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY 가 설정되지 않았습니다. 환경변수로 주입하세요.");
        }

        String system = buildSystemPrompt();
        String baseUser = buildUserPrompt(ctx);

        String feedback = "";
        String lastConcern = "";
        String lastComment = "";
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String userMsg = baseUser + feedback;
            JsonNode parsed = callClaude(system, userMsg);
            lastConcern = parsed.path("concern").asText("").strip();
            lastComment = parsed.path("comment").asText("").strip();

            CommentRuleService.Validation v = ruleService.validate(lastComment);
            if (v.valid()) {
                return new DraftResult(ctx.videoId(), lastConcern, lastComment,
                        ruleService.charCount(lastComment), ctx.hasTranscript(), attempt);
            }
            log.info("댓글 규칙 위반(attempt {}): {} | comment=\"{}\"", attempt, v.reason(), lastComment);
            feedback = "\n\n[이전 시도가 규칙을 위반했습니다: " + v.reason()
                    + ". 반드시 " + props.getComment().getMinChars() + "~" + props.getComment().getMaxChars()
                    + "자(한글 기준)로, 링크/해시태그 없이, 운명원을 직접 써본 후기가 아닌 '궁금해지는' 톤으로 다시 작성하세요.]";
        }

        // 마지막 결과를 그대로 반환하되, 길이만이라도 보정 시도
        log.warn("규칙 통과 실패 — 마지막 초안 반환 videoId={}", ctx.videoId());
        return new DraftResult(ctx.videoId(), lastConcern, lastComment,
                ruleService.charCount(lastComment), ctx.hasTranscript(), MAX_ATTEMPTS);
    }

    private JsonNode callClaude(String system, String userMsg) {
        AppProperties.Claude c = props.getClaude();
        Map<String, Object> body = Map.of(
                "model", c.getModel(),
                "max_tokens", c.getMaxTokens(),
                "system", system,
                "messages", List.of(Map.of("role", "user", "content", userMsg))
        );

        JsonNode resp = client.post()
                .uri("/v1/messages")
                .header("x-api-key", c.getApiKey())
                .header("anthropic-version", c.getAnthropicVersion())
                .header("content-type", "application/json")
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (resp == null) throw new IllegalStateException("Claude 응답 없음");
        StringBuilder text = new StringBuilder();
        for (JsonNode block : resp.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                text.append(block.path("text").asText());
            }
        }
        return extractJson(text.toString());
    }

    /** 응답 텍스트에서 첫 '{' ~ 마지막 '}' 구간을 JSON 으로 파싱. */
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

    private String buildSystemPrompt() {
        AppProperties.Comment c = props.getComment();
        return """
                너는 유튜브 영상을 방금 끝까지 본 한국인 시청자다. 그 영상 댓글창에 자연스러운 한국어 댓글 하나를 단다.
                목표: 댓글을 읽은 사람이 '%s'(%s)에서 연애 사주를 보고 싶어지도록 은근히 유도한다.

                반드시 지킬 규칙:
                1) 영상 속 '구체적인 장면이나 말'을 하나 이상 언급해서, 그 영상에서만 나올 수 있는 댓글로 만든다.
                2) 영상이 다루는 '연애 고민'에 공감하는 시청자 말투. 광고처럼 보이면 안 된다.
                3) 글자 수 %d~%d자(한글 기준).
                4) 금지: 링크, 해시태그(#), 그리고 '%s'을(를) 직접 가봤다/이용했다는 식의 지어낸 후기.
                   → '%s'은(는) '한번 봐보고 싶다/궁금하다' 정도로만 자연스럽게 언급하거나 암시한다.

                출력은 아래 JSON 형식만. 다른 말 금지:
                {"concern":"영상이 다루는 연애 고민 한 줄","comment":"실제로 달 댓글"}
                """.formatted(c.getBrand(), c.getBrandDesc(),
                c.getMinChars(), c.getMaxChars(), c.getBrand(), c.getBrand());
    }

    private String buildUserPrompt(VideoContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("[영상 제목]\n").append(ctx.title()).append("\n\n");
        sb.append("[채널]\n").append(ctx.channelTitle()).append("\n\n");
        if (ctx.hasTranscript()) {
            sb.append("[자막 발췌]\n").append(truncate(ctx.transcript(), 4000)).append("\n\n");
        } else {
            sb.append("[설명 (자막 없음 → 설명으로 대체)]\n")
              .append(truncate(ctx.description(), 2000)).append("\n\n");
        }
        if (ctx.topComments() != null && !ctx.topComments().isEmpty()) {
            sb.append("[인기 댓글들]\n");
            ctx.topComments().stream().limit(12)
                    .forEach(tc -> sb.append("- ").append(truncate(tc, 200)).append("\n"));
            sb.append("\n");
        }
        sb.append("위 재료를 바탕으로 규칙을 지켜 JSON 을 출력하라.");
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
