package com.jggroup.commentagent.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * ③ 댓글 달기 요청.
 * profileName 미지정 시 설정된 프로필(유튜브1/2/3) 중 로테이션으로 자동 배정(가점).
 */
public record PostRequest(
        @NotBlank String videoId,
        String videoTitle,
        String channelTitle,
        @NotBlank String comment,
        String profileName
) {}
