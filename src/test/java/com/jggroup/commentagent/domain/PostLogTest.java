package com.jggroup.commentagent.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 기록 엔티티 PostLog 의 opening 팩토리 단위 테스트. */
class PostLogTest {

    @Test
    @DisplayName("opening: 상태 OPENING 으로 필드를 채워 생성한다")
    void opening_setsFields() {
        PostLog log = PostLog.opening("VID00000001", "제목", "채널", "유튜브1", "댓글");

        assertThat(log.getId()).isNull();                 // 아직 미저장
        assertThat(log.getVideoId()).isEqualTo("VID00000001");
        assertThat(log.getVideoTitle()).isEqualTo("제목");
        assertThat(log.getChannelTitle()).isEqualTo("채널");
        assertThat(log.getProfileName()).isEqualTo("유튜브1");
        assertThat(log.getCommentText()).isEqualTo("댓글");
        assertThat(log.getStatus()).isEqualTo(PostStatus.OPENING);
        assertThat(log.getCreatedAt()).isNotNull();
    }
}
