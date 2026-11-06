package com.jggroup.commentagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 자막 best-effort 추출.
 * watch 페이지의 captionTracks 에서 (가능하면 한국어) baseUrl 을 찾아 timedtext XML 을 가져옴.
 * 실패하면 null 반환 → 호출부는 제목·설명으로 대체(요구사항 §2 "자막 없으면 제목·설명").
 */
@Slf4j
@Service
public class TranscriptService {

    private static final Pattern CAPTION_TRACKS =
            Pattern.compile("\"captionTracks\":(\\[.*?\\])");
    private static final Pattern XML_TEXT =
            Pattern.compile("<text[^>]*>(.*?)</text>", Pattern.DOTALL);

    private static final int MAX_CHARS = 6000;

    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient client;

    public TranscriptService(RestClient.Builder clientBuilder) {
        this.client = clientBuilder
                .defaultHeader("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome/125.0 Safari/537.36")
                .defaultHeader("Accept-Language", "ko-KR,ko;q=0.9")
                .build();
    }

    public String fetchBestEffort(String videoId) {
        try {
            String html = client.get()
                    .uri("https://www.youtube.com/watch?v={id}", videoId)
                    .retrieve()
                    .body(String.class);
            if (html == null) return null;

            Matcher m = CAPTION_TRACKS.matcher(html);
            if (!m.find()) {
                log.info("자막 트랙 없음 videoId={} → 제목·설명으로 대체", videoId);
                return null;
            }
            JsonNode tracks = mapper.readTree(m.group(1));
            String baseUrl = pickTrackUrl(tracks);
            if (baseUrl == null) return null;

            String xml = client.get().uri(baseUrl).retrieve().body(String.class);
            if (xml == null) return null;

            return parseXml(xml);
        } catch (Exception e) {
            log.info("자막 추출 실패 videoId={} ({}) → 제목·설명으로 대체", videoId, e.getMessage());
            return null;
        }
    }

    private String pickTrackUrl(JsonNode tracks) {
        String fallback = null;
        for (JsonNode t : tracks) {
            String url = t.path("baseUrl").asText(null);
            if (url == null) continue;
            if (fallback == null) fallback = url;
            String lang = t.path("languageCode").asText("");
            if (lang.startsWith("ko")) return url;
        }
        return fallback;
    }

    private String parseXml(String xml) {
        StringBuilder sb = new StringBuilder();
        Matcher tm = XML_TEXT.matcher(xml);
        while (tm.find() && sb.length() < MAX_CHARS) {
            sb.append(unescape(tm.group(1))).append(' ');
        }
        String result = sb.toString().trim();
        return result.isBlank() ? null : result;
    }

    private String unescape(String s) {
        return s.replace("&amp;", "&")
                .replace("&#39;", "'")
                .replace("&quot;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("\n", " ")
                .replaceAll("<[^>]+>", "")
                .trim();
    }
}
