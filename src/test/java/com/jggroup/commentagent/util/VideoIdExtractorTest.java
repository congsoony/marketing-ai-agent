package com.jggroup.commentagent.util;

import com.jggroup.commentagent.exception.InvalidInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 링크 직접 입력(가점)에서 videoId 추출 단위 테스트.
 * 11자 ID는 그대로, 일반/공유/쇼츠/임베드 링크는 ID만 뽑아내고, 형식 오류는 예외.
 */
class VideoIdExtractorTest {

    private static final String ID = "u2i_xdKW1JU"; // 11자, [-_A-Za-z0-9] 포함

    @Test
    @DisplayName("이미 11자 ID면 그대로 반환")
    void rawId() {
        assertThat(VideoIdExtractor.extract(ID)).isEqualTo(ID);
    }

    @Test
    @DisplayName("일반 watch 링크에서 추출(뒤에 파라미터가 더 있어도)")
    void watchUrl() {
        assertThat(VideoIdExtractor.extract("https://www.youtube.com/watch?v=" + ID)).isEqualTo(ID);
        assertThat(VideoIdExtractor.extract("https://www.youtube.com/watch?v=" + ID + "&t=30s&hl=ko")).isEqualTo(ID);
    }

    @Test
    @DisplayName("공유(youtu.be) · 쇼츠 · 임베드 링크에서 추출")
    void otherFormats() {
        assertThat(VideoIdExtractor.extract("https://youtu.be/" + ID)).isEqualTo(ID);
        assertThat(VideoIdExtractor.extract("https://www.youtube.com/shorts/" + ID)).isEqualTo(ID);
        assertThat(VideoIdExtractor.extract("https://www.youtube.com/embed/" + ID)).isEqualTo(ID);
    }

    @Test
    @DisplayName("앞뒤 공백은 무시")
    void trimsWhitespace() {
        assertThat(VideoIdExtractor.extract("  " + ID + "  ")).isEqualTo(ID);
    }

    @Test
    @DisplayName("null/빈 값은 InvalidInputException")
    void blank() {
        assertThatThrownBy(() -> VideoIdExtractor.extract(null)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> VideoIdExtractor.extract("   ")).isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("유튜브 링크/ID 형식이 아니면 InvalidInputException")
    void invalid() {
        assertThatThrownBy(() -> VideoIdExtractor.extract("hello"))
                .isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> VideoIdExtractor.extract("https://naver.com/watch?v=short"))
                .isInstanceOf(InvalidInputException.class);
    }
}
