package com.jggroup.commentagent.service;

import com.jggroup.commentagent.config.AppProperties;
import com.jggroup.commentagent.dto.VideoContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 프롬프트 작성 CommentPromptFactory 단위 테스트.
 * 시스템 프롬프트의 규칙 반영, 자막/설명 분기, 재료 포함·절단, 재생성 피드백을 검증.
 */
class CommentPromptFactoryTest {

    private final AppProperties props = new AppProperties();
    private final CommentPromptFactory factory = new CommentPromptFactory(props);

    @Test
    @DisplayName("시스템 프롬프트에 브랜드·글자수·금지규칙·JSON 형식이 들어간다")
    void system_containsRules() {
        String sys = factory.system();
        assertThat(sys).contains("운명원");                       // 브랜드
        assertThat(sys).contains("연애 사주 잘 봐주는 곳");        // 브랜드 설명
        assertThat(sys).contains("40~120");                       // 글자수 규칙
        assertThat(sys).contains("해시태그").contains("링크");     // 금지
        assertThat(sys).contains("\"concern\"").contains("\"comment\""); // JSON 형식
    }

    @Test
    @DisplayName("자막이 있으면 [자막 발췌]로 넣는다")
    void user_withTranscript() {
        VideoContext ctx = new VideoContext("vid", "제목", "채널", "설명문",
                "이건 자막 내용입니다", List.of());
        String user = factory.user(ctx);
        assertThat(user).contains("제목").contains("채널");
        assertThat(user).contains("[자막 발췌]").contains("이건 자막 내용입니다");
        assertThat(user).doesNotContain("자막 없음");
    }

    @Test
    @DisplayName("자막이 없으면 설명으로 대체한다")
    void user_withoutTranscript_fallsBackToDescription() {
        VideoContext ctx = new VideoContext("vid", "제목", "채널", "설명문입니다",
                null, List.of());
        String user = factory.user(ctx);
        assertThat(user).contains("자막 없음").contains("설명문입니다");
        assertThat(user).doesNotContain("[자막 발췌]");
    }

    @Test
    @DisplayName("인기 댓글은 최대 12개까지만 넣는다")
    void user_limitsTopCommentsTo12() {
        List<String> comments = IntStream.range(0, 15)
                .mapToObj(i -> String.format("COMMENT_%02d", i)).toList();
        VideoContext ctx = new VideoContext("vid", "제목", "채널", "설명", null, comments);
        String user = factory.user(ctx);
        assertThat(user).contains("COMMENT_00").contains("COMMENT_11"); // 0~11 = 12개
        assertThat(user).doesNotContain("COMMENT_12");                  // 13번째부터 제외
    }

    @Test
    @DisplayName("긴 자막은 4000자로 절단한다")
    void user_truncatesLongTranscript() {
        String longText = "가".repeat(5000);
        VideoContext ctx = new VideoContext("vid", "제목", "채널", "설명", longText, List.of());
        String user = factory.user(ctx);
        assertThat(user).contains("가".repeat(4000));       // 4000자까지는 포함
        assertThat(user).doesNotContain("가".repeat(4001)); // 그 이상은 잘림
    }

    @Test
    @DisplayName("재생성 피드백에 위반 사유와 글자수 규칙이 담긴다")
    void retryFeedback() {
        String fb = factory.retryFeedback("해시태그 포함 금지");
        assertThat(fb).contains("해시태그 포함 금지").contains("40~120");
    }
}
