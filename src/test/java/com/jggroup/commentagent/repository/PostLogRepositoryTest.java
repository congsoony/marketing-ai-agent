package com.jggroup.commentagent.repository;

import com.jggroup.commentagent.domain.PostLog;
import com.jggroup.commentagent.domain.PostStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운영규칙 강제에 쓰이는 커스텀 쿼리 검증(@DataJpaTest, 임베디드 H2).
 * - existsByVideoIdAndStatus : 영상당 1개
 * - countByStatusAndCreatedAtAfter : 하루 상한
 * - findAllByOrderByCreatedAtDesc : 기록 최신순
 */
@DataJpaTest
class PostLogRepositoryTest {

    @Autowired
    PostLogRepository repo;

    private PostLog log(String videoId, PostStatus status, Instant createdAt) {
        PostLog l = new PostLog();
        l.setVideoId(videoId);
        l.setStatus(status);
        l.setCommentText("댓글");
        l.setCreatedAt(createdAt);
        l.setUpdatedAt(createdAt);
        return l;
    }

    @Test
    @DisplayName("existsByVideoIdAndStatus: 해당 영상의 성공 게시 여부를 정확히 가린다")
    void existsByVideoIdAndStatus() {
        repo.save(log("VID00000001", PostStatus.SUCCESS, Instant.now()));

        assertThat(repo.existsByVideoIdAndStatus("VID00000001", PostStatus.SUCCESS)).isTrue();
        assertThat(repo.existsByVideoIdAndStatus("VID00000001", PostStatus.FAILED)).isFalse();
        assertThat(repo.existsByVideoIdAndStatus("OTHER000001", PostStatus.SUCCESS)).isFalse();
    }

    @Test
    @DisplayName("countByStatusAndCreatedAtAfter: 기준 시각 이후의 성공 건만 센다")
    void countByStatusAndCreatedAtAfter() {
        Instant now = Instant.now();
        repo.save(log("A1", PostStatus.SUCCESS, now));                            // 오늘 성공 O
        repo.save(log("A2", PostStatus.SUCCESS, now.minus(2, ChronoUnit.DAYS)));  // 이틀 전 → 제외
        repo.save(log("A3", PostStatus.FAILED, now));                            // 실패 → 제외

        long todaySuccess = repo.countByStatusAndCreatedAtAfter(
                PostStatus.SUCCESS, now.minus(1, ChronoUnit.DAYS));

        assertThat(todaySuccess).isEqualTo(1);
    }

    @Test
    @DisplayName("findAllByOrderByCreatedAtDesc: 최신순으로 반환")
    void findAllByOrderByCreatedAtDesc() {
        Instant now = Instant.now();
        repo.save(log("OLD0000001", PostStatus.SUCCESS, now.minus(2, ChronoUnit.HOURS)));
        repo.save(log("NEW0000001", PostStatus.SUCCESS, now));

        List<PostLog> all = repo.findAllByOrderByCreatedAtDesc();

        assertThat(all).hasSize(2);
        assertThat(all.get(0).getVideoId()).isEqualTo("NEW0000001"); // 최신이 먼저
        assertThat(all.get(1).getVideoId()).isEqualTo("OLD0000001");
    }
}
