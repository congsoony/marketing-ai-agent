package com.jggroup.commentagent.exception;

/**
 * 요청한 리소스를 찾을 수 없음(없는 영상 ID · 없는 게시 작업 등).
 * {@code ApiExceptionHandler} 에서 404 NOT_FOUND 로 매핑된다.
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
