package com.jggroup.commentagent.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 영상 목록 DTO VideoSummary.basic 팩토리 테스트. */
class VideoSummaryTest {

    @Test
    @DisplayName("basic: watch URL을 만들고 조회수 관련 필드는 null")
    void basic_buildsUrlAndNullStats() {
        VideoSummary v = VideoSummary.basic("VID00000001", "제목", "채널", "http://thumb", "2026-11-06T00:00:00Z");

        assertThat(v.videoId()).isEqualTo("VID00000001");
        assertThat(v.title()).isEqualTo("제목");
        assertThat(v.channelTitle()).isEqualTo("채널");
        assertThat(v.thumbnailUrl()).isEqualTo("http://thumb");
        assertThat(v.url()).isEqualTo("https://www.youtube.com/watch?v=VID00000001");
        assertThat(v.viewCount()).isNull();        // '뜨기 직전'에서만 채워짐
        assertThat(v.viewsPerHour()).isNull();
    }
}
