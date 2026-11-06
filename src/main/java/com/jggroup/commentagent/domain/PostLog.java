package com.jggroup.commentagent.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 기록(요구사항 §4): 어느 영상에 · 어느 프로필로 · 무슨 댓글을 · 성공/실패를 시간순으로.
 * 또한 운영 규칙 강제에 사용: 영상당 1개(SUCCESS 기준), 하루 게시 상한.
 */
@Entity
@Table(name = "post_log", indexes = {
        @Index(name = "idx_video", columnList = "videoId"),
        @Index(name = "idx_created", columnList = "createdAt")
})
@Getter
@Setter
@NoArgsConstructor
public class PostLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String videoId;

    @Column(length = 500)
    private String videoTitle;

    private String channelTitle;

    /** 사용한 크롬 프로필 이름 (유튜브1/2/3). */
    private String profileName;

    @Column(length = 1000)
    private String commentText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PostStatus status;

    /** 실패 사유. */
    @Column(length = 2000)
    private String failReason;

    /** 실패/완료 시 화면 캡처 경로. */
    private String screenshotPath;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public static PostLog opening(String videoId, String videoTitle, String channelTitle,
                                  String profileName, String commentText) {
        PostLog log = new PostLog();
        log.videoId = videoId;
        log.videoTitle = videoTitle;
        log.channelTitle = channelTitle;
        log.profileName = profileName;
        log.commentText = commentText;
        log.status = PostStatus.OPENING;
        return log;
    }
}
