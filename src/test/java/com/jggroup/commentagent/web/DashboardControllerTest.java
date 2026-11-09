package com.jggroup.commentagent.web;

import com.jggroup.commentagent.config.AppProperties;
import com.jggroup.commentagent.dto.DraftResult;
import com.jggroup.commentagent.dto.VideoContext;
import com.jggroup.commentagent.dto.VideoSummary;
import com.jggroup.commentagent.exception.ConfigMissingException;
import com.jggroup.commentagent.exception.NotFoundException;
import com.jggroup.commentagent.exception.RuleViolationException;
import com.jggroup.commentagent.service.OpenAiCommentService;
import com.jggroup.commentagent.service.PostingService;
import com.jggroup.commentagent.service.ProfileLoginService;
import com.jggroup.commentagent.service.YoutubeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 대시보드 REST API + 전역 예외 매핑(ApiExceptionHandler) 슬라이스 테스트.
 * 서비스는 목으로 대체하고, 요청 라우팅과 예외→HTTP 상태/타입 매핑을 검증한다.
 */
@WebMvcTest(DashboardController.class)
@Import(DashboardControllerTest.TestConfig.class)
// secret.yml 등 외부 import를 끄고 키를 비워, 설정값을 테스트에서 고정한다.
@TestPropertySource(properties = {
        "spring.config.import=",
        "app.youtube.api-key=",
        "app.openai.api-key="
})
class DashboardControllerTest {

    @org.springframework.boot.test.context.TestConfiguration
    @EnableConfigurationProperties(AppProperties.class)
    static class TestConfig {
        // AppProperties 는 application.yml 기본값 + 위 @TestPropertySource(빈 키)로 바인딩됨
    }

    @Autowired MockMvc mvc;

    @MockitoBean YoutubeService youtube;
    @MockitoBean OpenAiCommentService commentService;
    @MockitoBean PostingService posting;
    @MockitoBean ProfileLoginService profileLogin;

    @Test
    @DisplayName("GET /api/config: 화면 표시용 설정을 내려준다")
    void config() throws Exception {
        mvc.perform(get("/api/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brand").value("운명원"))
                .andExpect(jsonPath("$.minChars").value(40))
                .andExpect(jsonPath("$.maxChars").value(120))
                .andExpect(jsonPath("$.dailyMax").value(10))
                .andExpect(jsonPath("$.youtubeKeyConfigured").value(false))
                .andExpect(jsonPath("$.openaiKeyConfigured").value(false));
    }

    @Test
    @DisplayName("GET /api/videos: 영상 목록 JSON 배열 반환")
    void videos() throws Exception {
        when(youtube.listLatest(any(), anyInt()))
                .thenReturn(List.of(VideoSummary.basic("VID00000001", "제목", "채널", "http://t", "2026-11-06T00:00:00Z")));
        mvc.perform(get("/api/videos").param("query", "연애"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].videoId").value("VID00000001"))
                .andExpect(jsonPath("$[0].title").value("제목"));
    }

    @Test
    @DisplayName("GET /api/draft: videoId로 고민 한 줄 + 댓글 초안 반환")
    void draft() throws Exception {
        when(youtube.buildContext("VID00000001"))
                .thenReturn(new VideoContext("VID00000001", "제목", "채널", "설명", null, List.of()));
        when(commentService.generate(any()))
                .thenReturn(new DraftResult("VID00000001", "연애 고민", "댓글초안", 60, false, 1));
        mvc.perform(get("/api/draft").param("videoId", "VID00000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.concernLine").value("연애 고민"))
                .andExpect(jsonPath("$.comment").value("댓글초안"));
    }

    @Test
    @DisplayName("GET /api/draft: 잘못된 링크면 400 INPUT")
    void draft_invalidLink() throws Exception {
        mvc.perform(get("/api/draft").param("url", "not-a-youtube-link"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("INPUT"));
    }

    @Test
    @DisplayName("예외 매핑: 설정 누락 → 503 CONFIG")
    void mapsConfigMissing() throws Exception {
        when(youtube.listLatest(any(), anyInt())).thenThrow(new ConfigMissingException("키 없음"));
        mvc.perform(get("/api/videos"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value("CONFIG"));
    }

    @Test
    @DisplayName("예외 매핑: 운영규칙 위반 → 409 RULE")
    void mapsRuleViolation() throws Exception {
        when(posting.start(any())).thenThrow(new RuleViolationException("영상당 1개"));
        String body = "{\"videoId\":\"VID00000001\",\"comment\":\"" + "가".repeat(60) + "\"}";
        mvc.perform(post("/api/post").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("RULE"));
    }

    @Test
    @DisplayName("예외 매핑: 없는 작업 → 404 NOT_FOUND")
    void mapsNotFound() throws Exception {
        when(posting.getStatus(999L)).thenThrow(new NotFoundException("작업 없음"));
        mvc.perform(get("/api/post/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("@Valid: 빈 댓글이면 400 INPUT")
    void postValidation() throws Exception {
        String body = "{\"videoId\":\"VID00000001\",\"comment\":\"\"}";
        mvc.perform(post("/api/post").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("INPUT"));
    }
}
