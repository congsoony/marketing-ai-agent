package com.jggroup.commentagent.dto;

public record PostResult(
        boolean success,
        String failReason,
        String screenshotPath,
        String postedComment
) {
    public static PostResult ok(String screenshot, String comment) {
        return new PostResult(true, null, screenshot, comment);
    }
    public static PostResult fail(String reason, String screenshot, String comment) {
        return new PostResult(false, reason, screenshot, comment);
    }
}
