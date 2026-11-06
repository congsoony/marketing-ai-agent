package com.jggroup.commentagent.dto;

/**
 * ① 영상 불러오기 목록 항목 — 썸네일 · 제목 · 채널 · 올린 날짜.
 * viewCount/viewsPerHour 는 '뜨기 직전 영상 찾기'(가점) 에서만 채워짐.
 */
public record VideoSummary(
        String videoId,
        String title,
        String channelTitle,
        String thumbnailUrl,
        String publishedAt,
        String url,
        Long viewCount,
        Double viewsPerHour
) {
    public static VideoSummary basic(String videoId, String title, String channelTitle,
                                     String thumbnailUrl, String publishedAt) {
        return new VideoSummary(videoId, title, channelTitle, thumbnailUrl, publishedAt,
                "https://www.youtube.com/watch?v=" + videoId, null, null);
    }
}
