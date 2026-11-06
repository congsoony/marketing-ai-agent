package com.jggroup.commentagent.exception;

/**
 * 잘못된 입력(빈 링크 · 형식이 틀린 유튜브 링크/ID 등).
 * {@code ApiExceptionHandler} 에서 400 BAD_REQUEST 로 매핑된다.
 */
public class InvalidInputException extends RuntimeException {
    public InvalidInputException(String message) {
        super(message);
    }
}
