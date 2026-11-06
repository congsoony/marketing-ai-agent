package com.jggroup.commentagent.repository;

import com.jggroup.commentagent.domain.PostLog;
import com.jggroup.commentagent.domain.PostStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface PostLogRepository extends JpaRepository<PostLog, Long> {

    /** 기록 패널: 최신순 전체. */
    List<PostLog> findAllByOrderByCreatedAtDesc();

    /** 영상당 댓글 1개 강제: 해당 영상에 성공 게시가 이미 있는가. */
    boolean existsByVideoIdAndStatus(String videoId, PostStatus status);

    /** 하루 게시 상한 강제: 오늘 성공 건수. */
    long countByStatusAndCreatedAtAfter(PostStatus status, Instant after);
}
