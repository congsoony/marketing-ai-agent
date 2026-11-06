package com.jggroup.commentagent.dto;

/**
 * ② 영상 선택 결과 — 「영상 속 고민 한 줄」 + 「댓글 초안」.
 */
public record DraftResult(
        String videoId,
        String concernLine,   // 영상이 다루는 연애 고민 한 줄
        String comment,       // 게시 후보 댓글 초안
        int charCount,
        boolean usedTranscript,
        int attempts
) {}
