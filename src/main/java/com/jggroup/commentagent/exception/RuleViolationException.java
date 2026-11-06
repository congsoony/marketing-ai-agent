package com.jggroup.commentagent.exception;

/**
 * 운영 규칙 위반(영상당 1개 초과 · 하루 상한 초과 · 프로필 미설정 등).
 * {@code ApiExceptionHandler} 에서 409 CONFLICT 로 매핑된다.
 */
public class RuleViolationException extends RuntimeException {
    public RuleViolationException(String message) {
        super(message);
    }
}
