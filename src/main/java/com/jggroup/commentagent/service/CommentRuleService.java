package com.jggroup.commentagent.service;

import com.jggroup.commentagent.config.AppProperties;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * 댓글 규칙 검증 (요구사항 §2 "댓글 제약"):
 *  - 40~120자 (설정값)
 *  - 금지: 링크 · 해시태그 · '운명원을 직접 이용해 본 것처럼 지어낸 후기'
 * 생성 후 코드에서 2차 강제 → 통과 못 하면 재생성.
 */
@Service
public class CommentRuleService {

    private static final Pattern URL = Pattern.compile(
            "(https?://|www\\.|\\b\\w+\\.(com|net|kr|co|io|me|ly|gg|link)\\b)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern HASHTAG = Pattern.compile("#\\S+");

    // 운명원을 '직접 써본 후기'처럼 지어낸 1인칭 경험 표현
    private static final Pattern FAKE_REVIEW = Pattern.compile(
            "(갔다왔|갔다 왔|가봤|가 봤|다녀왔|받아봤|받아 봤|봐봤|봐 봤|이용했|이용해봤|해봤더니|" +
            "후기|리뷰|예약했|상담받|상담 받|사주 봤|사주봤|점 봤|점봤|맞췄|족집게|정확했)");

    private final AppProperties props;

    public CommentRuleService(AppProperties props) {
        this.props = props;
    }

    public record Validation(boolean valid, String reason) {
        public static Validation ok() { return new Validation(true, null); }
        public static Validation fail(String r) { return new Validation(false, r); }
    }

    /** 글자 수는 코드포인트 기준(이모지/한글 안전). */
    public int charCount(String text) {
        return text == null ? 0 : text.codePointCount(0, text.length());
    }

    public Validation validate(String comment) {
        if (comment == null || comment.isBlank()) {
            return Validation.fail("빈 댓글");
        }
        String c = comment.strip();
        AppProperties.Comment rule = props.getComment();

        int len = charCount(c);
        if (len < rule.getMinChars() || len > rule.getMaxChars()) {
            return Validation.fail(
                    "글자 수 규칙 위반: %d자 (허용 %d~%d자)".formatted(len, rule.getMinChars(), rule.getMaxChars()));
        }
        if (URL.matcher(c).find()) {
            return Validation.fail("링크 포함 금지");
        }
        if (HASHTAG.matcher(c).find()) {
            return Validation.fail("해시태그 포함 금지");
        }
        String brand = rule.getBrand();
        if (c.contains(brand) && FAKE_REVIEW.matcher(c).find()) {
            return Validation.fail("'" + brand + "'을(를) 직접 이용해 본 것처럼 지어낸 후기 금지");
        }
        return Validation.ok();
    }
}
