package com.jggroup.commentagent.web;

import com.jggroup.commentagent.exception.ConfigMissingException;
import com.jggroup.commentagent.exception.InvalidInputException;
import com.jggroup.commentagent.exception.NotFoundException;
import com.jggroup.commentagent.exception.RuleViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * API 공통 예외 → JSON {error, type} 응답.
 * 도메인 예외(exception 패키지)를 HTTP 상태로 매핑하고,
 * 그 밖의 JDK 예외는 안전한 기본값으로 떨어뜨린다.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** 운영 규칙 위반(영상당 1개·하루 상한 등) → 409. */
    @ExceptionHandler(RuleViolationException.class)
    public ResponseEntity<Map<String, Object>> rule(RuleViolationException e) {
        return build(HttpStatus.CONFLICT, e.getMessage(), "RULE");
    }

    /** 리소스 없음(없는 영상 ID·없는 작업) → 404. */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(NotFoundException e) {
        return build(HttpStatus.NOT_FOUND, e.getMessage(), "NOT_FOUND");
    }

    /** 잘못된 입력(링크/videoId 형식 오류) → 400. */
    @ExceptionHandler({InvalidInputException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> badInput(RuntimeException e) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage(), "INPUT");
    }

    /** @Valid 바디 검증 실패 → 400. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> invalidBody(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .orElse("잘못된 요청 본문입니다.");
        return build(HttpStatus.BAD_REQUEST, msg, "INPUT");
    }

    /** 설정 누락(API 키 미설정 등) → 503. */
    @ExceptionHandler({ConfigMissingException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, Object>> config(RuntimeException e) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), "CONFIG");
    }

    /** 그 외 — YouTube/Claude 호출 실패 등 → 500. */
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
