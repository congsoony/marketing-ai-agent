package com.jggroup.commentagent.web;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 유튜브 링크 직접 입력(가점)에서 videoId 추출. 이미 11자 ID면 그대로 사용. */
final class VideoIdSupport {

    private static final Pattern ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");
    private static final Pattern FROM_URL = Pattern.compile(
            "(?:v=|/shorts/|youtu\\.be/|/embed/)([A-Za-z0-9_-]{11})");

    private VideoIdSupport() {}

    static String extract(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("videoId 또는 링크가 필요합니다.");
        }
        String s = input.strip();
        if (ID.matcher(s).matches()) return s;
        Matcher m = FROM_URL.matcher(s);
        if (m.find()) return m.group(1);
        throw new IllegalArgumentException("유효한 유튜브 영상 링크/ID가 아닙니다: " + input);
    }
}
