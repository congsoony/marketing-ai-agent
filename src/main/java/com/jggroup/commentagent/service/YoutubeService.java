package com.jggroup.commentagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.jggroup.commentagent.config.AppProperties;
import com.jggroup.commentagent.dto.VideoContext;
import com.jggroup.commentagent.dto.VideoSummary;
import com.jggroup.commentagent.exception.ConfigMissingException;
import com.jggroup.commentagent.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * YouTube Data API v3 연동.
 *  - ① 영상 불러오기: 최신 영상 목록
 *  - ② 재료 수집: 영상 메타(제목·설명) + 인기 댓글
 *  - 가점: '연애' 뜨기 직전 영상 찾기 (조회 1,000~5,000 · 시간당 150↑)
 */
@Slf4j
@Service
public class YoutubeService {

    private static final String BASE = "https://www.googleapis.com/youtube/v3";

    private final AppProperties props;
    private final RestClient client;
    private final TranscriptService transcriptService;

    public YoutubeService(AppProperties props, TranscriptService transcriptService) {
        this.props = props;
        this.transcriptService = transcriptService;
        this.client = RestClient.builder().baseUrl(BASE).build();
    }

    private void requireKey() {
        if (!props.getYoutube().isConfigured()) {
            throw new ConfigMissingException("YOUTUBE_API_KEY 가 설정되지 않았습니다. 환경변수로 주입하세요.");
        }
    }

    /** ① 최신 영상 목록 (썸네일·제목·채널·올린 날짜). */
    public List<VideoSummary> listLatest(String query, int maxResults) {
        requireKey();
        String q = (query == null || query.isBlank()) ? props.getYoutube().getDefaultQuery() : query;
        JsonNode root = client.get()
                .uri(uri -> uri.path("/search")
                        .queryParam("key", props.getYoutube().getApiKey())
                        .queryParam("part", "snippet")
                        .queryParam("type", "video")
                        .queryParam("order", "date")
                        .queryParam("q", q)
                        .queryParam("regionCode", props.getYoutube().getRegion())
                        .queryParam("relevanceLanguage", props.getYoutube().getRelevanceLanguage())
                        .queryParam("maxResults", Math.min(maxResults, 50))
                        .build())
                .retrieve()
                .body(JsonNode.class);

        List<VideoSummary> out = new ArrayList<>();
        if (root == null) return out;
        for (JsonNode item : root.path("items")) {
            String videoId = item.path("id").path("videoId").asText(null);
            if (videoId == null) continue;
            JsonNode sn = item.path("snippet");
            out.add(VideoSummary.basic(
                    videoId,
                    sn.path("title").asText(""),
                    sn.path("channelTitle").asText(""),
                    thumbnail(sn),
                    sn.path("publishedAt").asText("")));
        }
        return out;
    }

    /** 단일 영상의 제목·채널 (링크 직접 입력 가점 경로에서 사용). */
    public VideoSummary getSummary(String videoId) {
        requireKey();
        JsonNode item = fetchVideoNode(videoId);
        if (item == null) throw new NotFoundException("영상을 찾을 수 없습니다: " + videoId);
        JsonNode sn = item.path("snippet");
        return VideoSummary.basic(
                videoId,
                sn.path("title").asText(""),
                sn.path("channelTitle").asText(""),
                thumbnail(sn),
                sn.path("publishedAt").asText(""));
    }

    /** ② AI 입력 재료: 제목·설명 + 자막(best-effort, 없으면 null) + 인기 댓글. */
    public VideoContext buildContext(String videoId) {
        requireKey();
        JsonNode item = fetchVideoNode(videoId);
        if (item == null) throw new NotFoundException("영상을 찾을 수 없습니다: " + videoId);
        JsonNode sn = item.path("snippet");
        String title = sn.path("title").asText("");
        String channel = sn.path("channelTitle").asText("");
        String description = sn.path("description").asText("");

        String transcript = transcriptService.fetchBestEffort(videoId);
        List<String> topComments = fetchTopComments(videoId, 15);

        return new VideoContext(videoId, title, channel, description, transcript, topComments);
    }

    /** 인기 댓글(관련도순) 상위 N개. */
    public List<String> fetchTopComments(String videoId, int max) {
        List<String> comments = new ArrayList<>();
        try {
            JsonNode root = client.get()
                    .uri(uri -> uri.path("/commentThreads")
                            .queryParam("key", props.getYoutube().getApiKey())
                            .queryParam("part", "snippet")
                            .queryParam("videoId", videoId)
                            .queryParam("order", "relevance")
                            .queryParam("maxResults", Math.min(max, 100))
                            .queryParam("textFormat", "plainText")
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            if (root != null) {
                for (JsonNode item : root.path("items")) {
                    String text = item.path("snippet").path("topLevelComment")
                            .path("snippet").path("textOriginal").asText("");
                    if (!text.isBlank()) comments.add(text.trim());
                }
            }
        } catch (Exception e) {
            // 댓글 비활성화 등 — 재료 없이 진행 (제목·설명으로 대체)
            log.warn("인기 댓글 수집 실패 videoId={} : {}", videoId, e.getMessage());
        }
        return comments;
    }

    /**
     * 가점: '연애' 키워드로 '뜨기 직전' 영상 찾기.
     * 조회수 min~max 사이이면서 시간당 조회 증가율이 임계치 이상인 최근 영상.
     * (초안까지만 — 게시 대상 후보로 제공)
     */
    public List<VideoSummary> findTrending() {
        requireKey();
        AppProperties.Trending t = props.getTrending();
        // 1) 최근 업로드 검색
        List<VideoSummary> recent = listLatest(t.getQuery(), 50);
        if (recent.isEmpty()) return List.of();

        // 2) 상세 통계 조회 (videos.list 는 id 최대 50개 일괄)
        String ids = String.join(",", recent.stream().map(VideoSummary::videoId).toList());
        JsonNode root = client.get()
                .uri(uri -> uri.path("/videos")
                        .queryParam("key", props.getYoutube().getApiKey())
                        .queryParam("part", "snippet,statistics")
                        .queryParam("id", ids)
                        .build())
                .retrieve()
                .body(JsonNode.class);

        List<VideoSummary> result = new ArrayList<>();
        if (root == null) return result;
        Instant now = Instant.now();
        for (JsonNode item : root.path("items")) {
            String videoId = item.path("id").asText(null);
            if (videoId == null) continue;
            JsonNode sn = item.path("snippet");
            long views = item.path("statistics").path("viewCount").asLong(0);
            String publishedAt = sn.path("publishedAt").asText("");
            double hours = hoursSince(publishedAt, now);
            if (hours <= 0) continue;
            if (hours > t.getPublishedWithinHours()) continue;

            double viewsPerHour = views / hours;
            boolean inViewBand = views >= t.getMinViews() && views <= t.getMaxViews();
            boolean risingFast = viewsPerHour >= t.getMinViewsPerHour();
            if (inViewBand && risingFast) {
                result.add(new VideoSummary(
                        videoId,
                        sn.path("title").asText(""),
                        sn.path("channelTitle").asText(""),
                        thumbnail(sn),
                        publishedAt,
                        "https://www.youtube.com/watch?v=" + videoId,
                        views,
                        Math.round(viewsPerHour * 10) / 10.0));
            }
        }
        result.sort(Comparator.comparingDouble(
                (VideoSummary v) -> v.viewsPerHour() == null ? 0 : v.viewsPerHour()).reversed());
        return result;
    }

    // ── helpers ────────────────────────────────────────────────

    private JsonNode fetchVideoNode(String videoId) {
        JsonNode root = client.get()
                .uri(uri -> uri.path("/videos")
                        .queryParam("key", props.getYoutube().getApiKey())
                        .queryParam("part", "snippet")
                        .queryParam("id", videoId)
                        .build())
                .retrieve()
                .body(JsonNode.class);
        if (root == null) return null;
        JsonNode items = root.path("items");
        return items.isArray() && !items.isEmpty() ? items.get(0) : null;
    }

    private static String thumbnail(JsonNode snippet) {
        JsonNode th = snippet.path("thumbnails");
        for (String size : List.of("medium", "high", "default", "standard", "maxres")) {
            String url = th.path(size).path("url").asText(null);
            if (url != null) return url;
        }
        return "";
    }

    private static double hoursSince(String isoPublishedAt, Instant now) {
        try {
            Instant pub = OffsetDateTime.parse(isoPublishedAt).toInstant();
            return Duration.between(pub, now).toMinutes() / 60.0;
        } catch (Exception e) {
            return -1;
        }
    }
}
