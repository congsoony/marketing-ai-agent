package com.jggroup.commentagent.service;

import com.jggroup.commentagent.config.AppProperties;
import com.jggroup.commentagent.dto.VideoContext;
import com.jggroup.commentagent.dto.VideoSummary;
import com.jggroup.commentagent.exception.ConfigMissingException;
import com.jggroup.commentagent.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * YouTube Data API 연동 YoutubeService 테스트.
 * MockRestServiceServer 로 googleapis 응답을 흉내 내 파싱·필터·예외를 검증(실제 호출 없음).
 */
class YoutubeServiceTest {

    private AppProperties props;
    private TranscriptService transcript;
    private MockRestServiceServer server;
    private YoutubeService service;

    @BeforeEach
    void setUp() {
        props = new AppProperties();
        props.getYoutube().setApiKey("test-key");
        transcript = mock(TranscriptService.class);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new YoutubeService(props, transcript, builder);
    }

    @Test
    @DisplayName("키가 없으면 호출 전에 ConfigMissingException")
    void noKey_throws() {
        AppProperties empty = new AppProperties();  // api-key 미설정
        YoutubeService svc = new YoutubeService(empty, transcript, RestClient.builder());
        assertThatThrownBy(() -> svc.listLatest("연애", 5))
                .isInstanceOf(ConfigMissingException.class);
    }

    @Test
    @DisplayName("listLatest: videoId 있는 항목만 파싱하고 썸네일/워치URL을 채운다")
    void listLatest_parsesItems() {
        String body = """
                {"items":[
                  {"id":{"videoId":"VID0000001"},"snippet":{"title":"제목1","channelTitle":"채널1",
                   "publishedAt":"2026-11-06T00:00:00Z","thumbnails":{"medium":{"url":"http://thumb/1"}}}},
                  {"id":{"kind":"youtube#channel"},"snippet":{"title":"채널결과"}}
                ]}""";
        server.expect(requestTo(containsString("/youtube/v3/search")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<VideoSummary> out = service.listLatest("연애", 5);

        server.verify();
        assertThat(out).hasSize(1);
        VideoSummary v = out.get(0);
        assertThat(v.videoId()).isEqualTo("VID0000001");
        assertThat(v.title()).isEqualTo("제목1");
        assertThat(v.channelTitle()).isEqualTo("채널1");
        assertThat(v.thumbnailUrl()).isEqualTo("http://thumb/1");
        assertThat(v.url()).contains("watch?v=VID0000001");
    }

    @Test
    @DisplayName("getSummary: 항목이 없으면 NotFoundException")
    void getSummary_notFound() {
        server.expect(requestTo(containsString("/youtube/v3/videos")))
                .andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.getSummary("VID0000001"))
                .isInstanceOf(NotFoundException.class);
        server.verify();
    }

    @Test
    @DisplayName("fetchTopComments: 댓글 API가 실패해도 예외 없이 빈 목록")
    void fetchTopComments_errorReturnsEmpty() {
        server.expect(requestTo(containsString("/youtube/v3/commentThreads")))
                .andRespond(withServerError());

        List<String> comments = service.fetchTopComments("VID0000001", 10);

        server.verify();
        assertThat(comments).isEmpty();
    }

    @Test
    @DisplayName("buildContext: 영상 메타 + (자막 폴백) + 인기 댓글을 모은다")
    void buildContext_combines() {
        // 1) /videos (메타)
        server.expect(requestTo(containsString("/youtube/v3/videos")))
                .andRespond(withSuccess("""
                        {"items":[{"snippet":{"title":"제목","channelTitle":"채널","description":"설명문"}}]}""",
                        MediaType.APPLICATION_JSON));
        // 2) /commentThreads (인기 댓글)
        server.expect(requestTo(containsString("/youtube/v3/commentThreads")))
                .andRespond(withSuccess("""
                        {"items":[{"snippet":{"topLevelComment":{"snippet":{"textOriginal":"좋은 영상!"}}}}]}""",
                        MediaType.APPLICATION_JSON));
        when(transcript.fetchBestEffort("VID0000001")).thenReturn(null); // 자막 없음

        VideoContext ctx = service.buildContext("VID0000001");

        server.verify();
        assertThat(ctx.title()).isEqualTo("제목");
        assertThat(ctx.description()).isEqualTo("설명문");
        assertThat(ctx.hasTranscript()).isFalse();
        assertThat(ctx.topComments()).containsExactly("좋은 영상!");
    }

    @Test
    @DisplayName("findTrending: 조회수 밴드 + 시간당 증가율 조건에 맞는 영상만 남긴다")
    void findTrending_filters() {
        String recentIso = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        // 1) /search → 후보 2개
        server.expect(requestTo(containsString("/youtube/v3/search")))
                .andRespond(withSuccess("""
                        {"items":[
                          {"id":{"videoId":"RISING00001"},"snippet":{"title":"뜨는중","channelTitle":"C1",
                           "publishedAt":"%s","thumbnails":{"medium":{"url":"http://t/1"}}}},
                          {"id":{"videoId":"TOOBIG0001"},"snippet":{"title":"이미큼","channelTitle":"C2",
                           "publishedAt":"%s","thumbnails":{"medium":{"url":"http://t/2"}}}}
                        ]}""".formatted(recentIso, recentIso), MediaType.APPLICATION_JSON));
        // 2) /videos → 통계 (RISING: 3000/1h = 밴드·증가율 OK, TOOBIG: 100000 = 밴드 초과)
        server.expect(requestTo(containsString("/youtube/v3/videos")))
                .andRespond(withSuccess("""
                        {"items":[
                          {"id":"RISING00001","snippet":{"title":"뜨는중","channelTitle":"C1",
                           "publishedAt":"%s","thumbnails":{"medium":{"url":"http://t/1"}}},
                           "statistics":{"viewCount":"3000"}},
                          {"id":"TOOBIG0001","snippet":{"title":"이미큼","channelTitle":"C2",
                           "publishedAt":"%s","thumbnails":{"medium":{"url":"http://t/2"}}},
                           "statistics":{"viewCount":"100000"}}
                        ]}""".formatted(recentIso, recentIso), MediaType.APPLICATION_JSON));

        List<VideoSummary> trending = service.findTrending();

        server.verify();
        assertThat(trending).hasSize(1);
        assertThat(trending.get(0).videoId()).isEqualTo("RISING00001");
        assertThat(trending.get(0).viewCount()).isEqualTo(3000);
        assertThat(trending.get(0).viewsPerHour()).isNotNull();
    }
}
