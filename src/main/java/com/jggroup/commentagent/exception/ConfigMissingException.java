package com.jggroup.commentagent.exception;

/**
 * 실행에 필요한 설정 누락(YOUTUBE_API_KEY / OPENAI_API_KEY 미주입 등).
 * {@code ApiExceptionHandler} 에서 503 SERVICE_UNAVAILABLE 로 매핑된다.
 */
public class ConfigMissingException extends RuntimeException {
    public ConfigMissingException(String message) {
        super(message);
    }
}
