package com.jggroup.commentagent.service;

import com.jggroup.commentagent.config.AppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 댓글 규칙 검증(요구사항 §2) 단위 테스트.
 * 글자수(40~120, 코드포인트 기준) · 링크/해시태그 금지 · 운명원 가짜후기 금지.
 */
class CommentRuleServiceTest {

    private final CommentRuleService rule = new CommentRuleService(new AppProperties());

    @Test
    @DisplayName("글자수는 코드포인트 기준으로 센다(이모지 1자)")
    void charCount_usesCodePoints() {
        assertThat(rule.charCount("가나다")).isEqualTo(3);
        assertThat(rule.charCount("👍👍")).isEqualTo(2);   // 서로게이트 쌍이어도 2자
        assertThat(rule.charCount(null)).isZero();
    }

    @Test
    @DisplayName("40~120자이고 금지요소 없으면 통과")
    void valid_whenWithinRangeAndClean() {
        String ok = "가".repeat(40);
        assertThat(rule.validate(ok).valid()).isTrue();
    }

    @Test
    @DisplayName("경계값 40자/120자는 통과, 39자/121자는 실패")
    void boundary() {
        assertThat(rule.validate("가".repeat(40)).valid()).isTrue();
        assertThat(rule.validate("가".repeat(120)).valid()).isTrue();
        assertThat(rule.validate("가".repeat(39)).valid()).isFalse();
        assertThat(rule.validate("가".repeat(121)).valid()).isFalse();
    }

    @Test
    @DisplayName("빈 댓글은 실패")
    void blank_fails() {
        assertThat(rule.validate("   ").valid()).isFalse();
        assertThat(rule.validate(null).valid()).isFalse();
    }

    @Test
    @DisplayName("링크(URL)가 들어가면 실패")
    void url_fails() {
        assertThat(rule.validate("가".repeat(40) + " http://example.com").valid()).isFalse();
        assertThat(rule.validate("가".repeat(40) + " 운명원 www.test.kr").valid()).isFalse();
    }

    @Test
    @DisplayName("해시태그가 들어가면 실패")
    void hashtag_fails() {
        assertThat(rule.validate("가".repeat(40) + " #연애사주").valid()).isFalse();
    }

    @Test
    @DisplayName("운명원을 직접 이용해 본 것처럼 지어낸 후기는 실패")
    void fakeReview_fails() {
        // "운명원" + 1인칭 경험 표현(갔다왔) → 길이는 40~120 안
        String fake = "운명원 갔다왔어요 ".repeat(5);   // 50자
        assertThat(rule.charCount(fake)).isBetween(40, 120);
        assertThat(rule.validate(fake).valid()).isFalse();
    }

    @Test
    @DisplayName("운명원을 '궁금한' 톤으로만 언급하면 통과")
    void brandCuriousTone_passes() {
        String curious = "운명원 궁금하네요 ".repeat(5);  // 50자, 가짜후기 표현 없음
        assertThat(rule.charCount(curious)).isBetween(40, 120);
        assertThat(rule.validate(curious).valid()).isTrue();
    }

    @Test
    @DisplayName("실패 시 사유 문자열을 제공한다")
    void providesReason() {
        assertThat(rule.validate("가".repeat(10)).reason()).contains("글자");
        assertThat(rule.validate("가".repeat(40) + " #태그").reason()).contains("해시태그");
    }
}
