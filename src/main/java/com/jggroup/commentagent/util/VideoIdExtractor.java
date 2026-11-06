package com.jggroup.commentagent.util;

import com.jggroup.commentagent.exception.InvalidInputException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 유튜브 링크/ID 에서 11자 videoId 를 뽑아내는 유틸(링크 직접 입력 가점에 사용).
 * 이미 11자 ID 면 그대로 반환하고, 일반 링크·공유 링크·쇼츠·임베드 형식을 지원한다.
 */
public final class VideoIdExtractor {

    private static final Pattern ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");
    private static final Pattern FROM_URL = Pattern.compile(
            "(?:v=|/shorts/|youtu\\.be/|/embed/)([A-Za-z0-9_-]{11})");

    private VideoIdExtractor() {}

    public static String extract(String input) {
        if (input == null || input.isBlank()) {
            throw new InvalidInputException("videoId 또는 링크가 필요합니다.");
        }
        String s = input.strip();
        if (ID.matcher(s).matches()) return s;
        Matcher m = FROM_URL.matcher(s);
        if (m.find()) return m.group(1);
        throw new InvalidInputException("유효한 유튜브 영상 링크/ID가 아닙니다: " + input);
    }
}
