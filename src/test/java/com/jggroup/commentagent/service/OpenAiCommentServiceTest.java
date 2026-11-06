package com.jggroup.commentagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jggroup.commentagent.config.AppProperties;
import com.jggroup.commentagent.dto.DraftResult;
import com.jggroup.commentagent.dto.VideoContext;
import com.jggroup.commentagent.exception.ConfigMissingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * OpenAI 댓글 생성 OpenAiCommentService 테스트.
 * MockRestServiceServer 로 Chat Completions 응답을 흉내 내,
 * 성공/재생성/JSON 추출/키검증을 실제 호출 없이 확인한다.
 */
class OpenAiCommentServiceTest {

    private static final ObjectMapper M = new ObjectMapper();

    private AppProperties props;
    private MockRestServiceServer server;
    private OpenAiCommentService service;

    @BeforeEach
    void setUp() {
        props = new AppProperties();
        props.getOpenai().setApiKey("test-key");
        CommentRuleService rule = new CommentRuleService(props);
        CommentPromptFactory factory = new CommentPromptFactory(props);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new OpenAiCommentService(props, rule, factory, builder);
    }

    /** OpenAI chat 응답 봉투: choices[0].message.content = 모델이 낸 문자열. */
    private String chat(String content) {
        try {
            ObjectNode root = M.createObjectNode();
            ObjectNode msg = root.putArray("choices").addObject().putObject("message");
            msg.put("content", content);
            return M.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private VideoContext ctx(boolean withTranscript) {
        return new VideoContext("VID0000001", "제목", "채널", "설명",
                withTranscript ? "자막내용" : null, List.of("인기댓글"));
    }

    @Test
    @DisplayName("키가 없으면 호출 전에 ConfigMissingException")
    void noKey_throws() {
        AppProperties empty = new AppProperties();
        OpenAiCommentService svc = new OpenAiCommentService(
                empty, new CommentRuleService(empty), new CommentPromptFactory(empty), RestClient.builder());
        assertThatThrownBy(() -> svc.generate(ctx(false)))
                .isInstanceOf(ConfigMissingException.class);
    }

    @Test
    @DisplayName("규칙을 만족하는 응답이면 1회 시도로 초안 반환")
    void generate_firstTrySuccess() {
        String comment = "가".repeat(60);
        server.expect(requestTo(containsString("/v1/chat/completions")))
                .andRespond(withSuccess(
                        chat("{\"concern\":\"연애 고민\",\"comment\":\"" + comment + "\"}"),
                        MediaType.APPLICATION_JSON));

        DraftResult r = service.generate(ctx(true));

        server.verify();
        assertThat(r.concernLine()).isEqualTo("연애 고민");
        assertThat(r.comment()).isEqualTo(comment);
        assertThat(r.charCount()).isEqualTo(60);
        assertThat(r.attempts()).isEqualTo(1);
        assertThat(r.usedTranscript()).isTrue();   // 자막 있는 컨텍스트
    }

    @Test
    @DisplayName("규칙 위반이면 재생성한다(2번째 응답으로 통과, attempts=2)")
    void generate_retriesOnRuleViolation() {
        String good = "나".repeat(60);
        // 1차: 너무 짧아 규칙 위반 → 2차: 정상
        server.expect(requestTo(containsString("/v1/chat/completions")))
                .andRespond(withSuccess(chat("{\"concern\":\"c\",\"comment\":\"짧음\"}"),
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/v1/chat/completions")))
                .andRespond(withSuccess(chat("{\"concern\":\"c\",\"comment\":\"" + good + "\"}"),
                        MediaType.APPLICATION_JSON));

        DraftResult r = service.generate(ctx(false));

        server.verify();
        assertThat(r.comment()).isEqualTo(good);
        assertThat(r.attempts()).isEqualTo(2);
        assertThat(r.usedTranscript()).isFalse();
    }

    @Test
    @DisplayName("응답에 앞뒤 잡텍스트가 있어도 JSON 객체만 뽑아 파싱한다")
    void generate_extractsJsonFromNoisyContent() {
        String comment = "다".repeat(55);
        String noisy = "설명: 아래가 결과입니다 ```json {\"concern\":\"고민\",\"comment\":\""
                + comment + "\"} ``` 끝";
        server.expect(requestTo(containsString("/v1/chat/completions")))
                .andRespond(withSuccess(chat(noisy), MediaType.APPLICATION_JSON));

        DraftResult r = service.generate(ctx(false));

        server.verify();
        assertThat(r.comment()).isEqualTo(comment);
        assertThat(r.concernLine()).isEqualTo("고민");
    }
}
