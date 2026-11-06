package com.jggroup.commentagent.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** AI 입력 컨텍스트 VideoContext.hasTranscript 판정 테스트. */
class VideoContextTest {

    private VideoContext withTranscript(String t) {
        return new VideoContext("vid", "제목", "채널", "설명", t, List.of());
    }

    @Test
    @DisplayName("자막이 실제 내용이면 true")
    void hasTranscript_true() {
        assertThat(withTranscript("자막 내용").hasTranscript()).isTrue();
    }

    @Test
    @DisplayName("자막이 null 또는 공백이면 false")
    void hasTranscript_false() {
        assertThat(withTranscript(null).hasTranscript()).isFalse();
        assertThat(withTranscript("   ").hasTranscript()).isFalse();
    }
}
