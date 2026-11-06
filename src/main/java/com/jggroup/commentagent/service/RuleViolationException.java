package com.jggroup.commentagent.service;

/** 운영 규칙 위반(영상당 1개 초과·하루 상한 초과 등). 컨트롤러에서 409로 매핑. */
public class RuleViolationException extends RuntimeException {
    public RuleViolationException(String message) {
        super(message);
    }
}
