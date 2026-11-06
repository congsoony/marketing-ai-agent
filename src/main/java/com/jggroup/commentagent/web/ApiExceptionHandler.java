package com.jggroup.commentagent.web;

import com.jggroup.commentagent.service.RuleViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** API 공통 예외 → JSON {error, type} 응답. */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** 운영 규칙 위반(영상당 1개·하루 상한 등). */
    @ExceptionHandler(RuleViolationException.class)
    public ResponseEntity<Map<String, Object>> rule(RuleViolationException e) {
        return build(HttpStatus.CONFLICT, e.getMessage(), "RULE");
    }

    /** 잘못된 입력(링크/videoId 오류, 없는 작업 등). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badInput(IllegalArgumentException e) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage(), "INPUT");
    }

    /** 설정 누락(API 키 미설정 등). */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> state(IllegalStateException e) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), "CONFIG");
    }

    /** 그 외 — YouTube/Claude 호출 실패 등. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> generic(Exception e) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), "ERROR");
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String msg, String type) {
        return ResponseEntity.status(status).body(Map.of(
                "error", msg == null ? status.getReasonPhrase() : msg,
                "type", type));
    }
}
