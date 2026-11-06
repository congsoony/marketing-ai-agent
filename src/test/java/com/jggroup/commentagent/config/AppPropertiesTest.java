package com.jggroup.commentagent.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 설정 바인딩 AppProperties 의 isConfigured 판정 + 기본값 테스트. */
class AppPropertiesTest {

    @Test
    @DisplayName("YouTube/OpenAI isConfigured: 키가 비어있으면 false, 채워지면 true")
    void isConfigured() {
        AppProperties p = new AppProperties();
        assertThat(p.getYoutube().isConfigured()).isFalse();
        assertThat(p.getOpenai().isConfigured()).isFalse();

        p.getYoutube().setApiKey("AIza-x");
        p.getOpenai().setApiKey("sk-x");
        assertThat(p.getYoutube().isConfigured()).isTrue();
        assertThat(p.getOpenai().isConfigured()).isTrue();

        p.getYoutube().setApiKey("   "); // 공백만이면 미설정으로 취급
        assertThat(p.getYoutube().isConfigured()).isFalse();
    }

    @Test
    @DisplayName("과제 기본값: 40~120자 · 운명원 · 하루 10건 · gpt-4o-mini")
    void defaults() {
        AppProperties p = new AppProperties();
        assertThat(p.getComment().getMinChars()).isEqualTo(40);
        assertThat(p.getComment().getMaxChars()).isEqualTo(120);
        assertThat(p.getComment().getBrand()).isEqualTo("운명원");
        assertThat(p.getLimits().getDailyMaxComments()).isEqualTo(10);
        assertThat(p.getOpenai().getModel()).isEqualTo("gpt-4o-mini");
    }
}
