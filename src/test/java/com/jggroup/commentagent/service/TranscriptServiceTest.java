package com.jggroup.commentagent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 자막 best-effort 추출 TranscriptService 테스트.
 * watch 페이지의 captionTracks → timedtext XML 경로를 MockRestServiceServer 로 흉내 낸다.
 * 실패 시 null(→ 호출부가 제목·설명으로 폴백)을 반환하는지도 검증.
 */
class TranscriptServiceTest {

    private MockRestServiceServer server;
    private TranscriptService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new TranscriptService(builder);
    }

    @Test
    @DisplayName("한국어 자막 트랙이 있으면 timedtext를 파싱해 텍스트로 반환")
    void returnsTranscript_whenKoCaptionExists() {
        String html = "<html>var x = {\"captionTracks\":"
                + "[{\"baseUrl\":\"https://yt.test/timedtext?lang=ko\",\"languageCode\":\"ko\"}]};</html>";
        String xml = "<transcript>"
                + "<text start=\"0\">안녕&amp;하세요</text>"
                + "<text start=\"1\">오늘 연애 고민</text>"
                + "</transcript>";
        server.expect(requestTo(containsString("/watch")))
                .andRespond(withSuccess(html, new MediaType("text", "html", StandardCharsets.UTF_8)));
        server.expect(requestTo(containsString("timedtext")))
                .andRespond(withSuccess(xml, new MediaType("application", "xml", StandardCharsets.UTF_8)));

        String out = service.fetchBestEffort("VID00000001");

        server.verify();
        assertThat(out).contains("안녕&하세요").contains("오늘 연애 고민"); // &amp; 디코딩 확인
    }

    @Test
    @DisplayName("captionTracks가 없으면 null (→ 제목·설명 폴백)")
    void returnsNull_whenNoCaptions() {
        server.expect(requestTo(containsString("/watch")))
                .andRespond(withSuccess("<html>자막 없음</html>", MediaType.TEXT_HTML));

        assertThat(service.fetchBestEffort("VID00000001")).isNull();
        server.verify();
    }

    @Test
    @DisplayName("watch 페이지 조회가 실패해도 예외 없이 null")
    void returnsNull_onError() {
        server.expect(requestTo(containsString("/watch")))
                .andRespond(withServerError());

        assertThat(service.fetchBestEffort("VID00000001")).isNull();
        server.verify();
    }
}
