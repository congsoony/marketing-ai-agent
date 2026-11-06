package com.jggroup.commentagent.domain;

/** 게시 진행/결과 상태. 요구사항: 프로필 여는 중 → 입력 중 → 게시 완료/실패. */
public enum PostStatus {
    OPENING,    // 프로필 여는 중
    TYPING,     // 한 글자씩 입력 중
    SUCCESS,    // 게시 완료 · 내 댓글 확인됨
    FAILED      // 실패 (사유 + 스크린샷 기록)
}
