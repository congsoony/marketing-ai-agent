package com.jggroup.commentagent.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 게시 결과 DTO PostResult.ok/fail 팩토리 테스트. */
class PostResultTest {

    @Test
    @DisplayName("ok: 성공 + 실패사유 없음")
    void ok() {
        PostResult r = PostResult.ok("shot.png", "댓글");
        assertThat(r.success()).isTrue();
        assertThat(r.failReason()).isNull();
        assertThat(r.screenshotPath()).isEqualTo("shot.png");
        assertThat(r.postedComment()).isEqualTo("댓글");
    }

    @Test
    @DisplayName("fail: 실패 + 사유 포함")
    void fail() {
        PostResult r = PostResult.fail("게시 버튼 없음", "shot.png", "댓글");
        assertThat(r.success()).isFalse();
        assertThat(r.failReason()).isEqualTo("게시 버튼 없음");
        assertThat(r.postedComment()).isEqualTo("댓글");
    }
}
