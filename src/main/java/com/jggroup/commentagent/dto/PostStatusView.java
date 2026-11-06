package com.jggroup.commentagent.dto;

import com.jggroup.commentagent.domain.PostLog;
import com.jggroup.commentagent.domain.PostStatus;

import java.time.Instant;

/**
 * 게시 작업 진행 상태 응답 (대시보드 폴링용).
 * note = 현재 단계 설명(여는 중/입력 중 등), screenshotUrl = 정적 서빙 경로.
 */
public record PostStatusView(
        Long id,
        String videoId,
        String videoTitle,
        String profileName,
        String comment,
        PostStatus status,
        String note,
        String failReason,
        String screenshotUrl,
        Instant createdAt,
        Instant updatedAt
) {
    public static PostStatusView of(PostLog log, String note, String screenshotUrl) {
        return new PostStatusView(
                log.getId(), log.getVideoId(), log.getVideoTitle(), log.getProfileName(),
                log.getCommentText(), log.getStatus(), note, log.getFailReason(),
                screenshotUrl, log.getCreatedAt(), log.getUpdatedAt());
    }
}
