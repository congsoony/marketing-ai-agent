package com.jggroup.commentagent.service;

import com.jggroup.commentagent.domain.PostStatus;

/** 게시 진행 상태 콜백 (프로필 여는 중 → 입력 중 → 게시 완료/실패). */
@FunctionalInterface
public interface PostProgressListener {
    void onProgress(PostStatus status, String note, String screenshotPath);

    default void onProgress(PostStatus status, String note) {
        onProgress(status, note, null);
    }
}
