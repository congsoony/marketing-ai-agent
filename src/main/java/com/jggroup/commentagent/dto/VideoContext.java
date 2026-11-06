package com.jggroup.commentagent.dto;

import java.util.List;

/**
 * AI 댓글 생성 입력 재료.
 * 자막이 없으면 transcript 는 null/blank → 제목·설명으로 대체(요구사항 §2).
 */
public record VideoContext(
        String videoId,
        String title,
        String channelTitle,
        String description,
        String transcript,
        List<String> topComments
) {
    public boolean hasTranscript() {
        return transcript != null && !transcript.isBlank();
    }
}
