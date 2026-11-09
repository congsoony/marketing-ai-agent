package com.jggroup.commentagent.service;

import com.jggroup.commentagent.config.AppProperties;
import com.jggroup.commentagent.dto.VideoContext;
import org.springframework.stereotype.Component;

/**
 * AI(OpenAI)에 보낼 프롬프트(시스템/사용자) 작성 전담.
 * {@link OpenAiCommentService} 에서 '무엇을 시킬지'(프롬프트)를 떼어내,
 * 서비스는 '호출·검증·재생성 흐름'에만 집중하도록 책임을 분리한다(단일 책임).
 */
@Component
public class CommentPromptFactory {

    private static final int TRANSCRIPT_LIMIT = 4000;
    private static final int DESCRIPTION_LIMIT = 2000;
    private static final int COMMENT_SAMPLE_LIMIT = 200;
    private static final int MAX_SAMPLE_COMMENTS = 12;

    private final AppProperties props;

    public CommentPromptFactory(AppProperties props) {
        this.props = props;
    }

    /** 역할·목표·금지 규칙·출력 형식을 정의하는 시스템 프롬프트. */
    public String system() {
        AppProperties.Comment c = props.getComment();
        return """
                너는 유튜브 영상을 방금 끝까지 본 한국인 시청자다. 그 영상 댓글창에 자연스러운 한국어 댓글 하나를 단다.
                목표: 댓글을 읽은 사람이 '%1$s'(%2$s)에서 연애 사주를 보고 싶어지도록 은근히 유도한다.

                반드시 지킬 규칙:
                1) 영상 속 '구체적인 장면이나 말'을 하나 이상 언급해서, 그 영상에서만 나올 수 있는 댓글로 만든다.
                2) 영상이 다루는 '연애 고민'에 공감하는 시청자 말투. 광고처럼 보이면 안 된다.
                3) 글자 수 %3$d~%4$d자(한글 기준).
                4) 댓글 안에 반드시 '%1$s'이라는 이름을 자연스럽게 정확히 한 번 넣는다. 어디서 보는지(%1$s)가 드러나야 한다.
                   - '%1$s'을 넣는 위치와 표현을 매번 다르게 한다. 문장 중간에 녹여도 되고,
                     항상 마지막 문장이나 "보고 싶네요/보고 싶다"로 끝맺는 똑같은 틀을 반복하지 마라.
                   - 아래는 서로 다른 녹이는 방식의 예시다. 그대로 베끼지 말고 영상 내용에 맞게 변형해라:
                     · "요즘 %1$s 연애사주 잘 본다고 소문나던데 내 인연은 어떨지 궁금해진다"
                     · "이런 건 %1$s에서 사주 보면 좀 알 수 있으려나"
                     · "나도 %1$s 가서 연애운 한번 물어볼까 싶네"
                   - '사주 보고 싶다'로만 끝내고 '%1$s'을 빠뜨리면 안 된다.
                5) 금지: 링크, 해시태그(#), 그리고 '%1$s'을(를) 직접 가봤다/이용했다는 식의 지어낸 후기.
                   → '%1$s'은(는) 아직 안 가봤지만 '한번 봐보고 싶다/궁금하다'는 톤으로만 쓴다.

                출력은 아래 JSON 형식만. 다른 말 금지:
                {"concern":"영상이 다루는 연애 고민 한 줄","comment":"실제로 달 댓글"}
                """.formatted(c.getBrand(), c.getBrandDesc(), c.getMinChars(), c.getMaxChars());
    }

    /** 영상 재료(제목·채널·자막 또는 설명·인기 댓글)를 담는 사용자 프롬프트. */
    public String user(VideoContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("[영상 제목]\n").append(ctx.title()).append("\n\n");
        sb.append("[채널]\n").append(ctx.channelTitle()).append("\n\n");
        if (ctx.hasTranscript()) {
            sb.append("[자막 발췌]\n").append(truncate(ctx.transcript(), TRANSCRIPT_LIMIT)).append("\n\n");
        } else {
            sb.append("[설명 (자막 없음 → 설명으로 대체)]\n")
              .append(truncate(ctx.description(), DESCRIPTION_LIMIT)).append("\n\n");
        }
        if (ctx.topComments() != null && !ctx.topComments().isEmpty()) {
            sb.append("[인기 댓글들]\n");
            ctx.topComments().stream().limit(MAX_SAMPLE_COMMENTS)
                    .forEach(tc -> sb.append("- ").append(truncate(tc, COMMENT_SAMPLE_LIMIT)).append("\n"));
            sb.append("\n");
        }
        sb.append("위 재료를 바탕으로 규칙을 지켜 JSON 을 출력하라.");
        return sb.toString();
    }

    /** 규칙 위반 시 다음 시도에 덧붙일 교정 피드백. */
    public String retryFeedback(String reason) {
        AppProperties.Comment c = props.getComment();
        return "\n\n[이전 시도가 규칙을 위반했습니다: " + reason
                + ". 반드시 " + c.getMinChars() + "~" + c.getMaxChars()
                + "자(한글 기준)로, '" + c.getBrand() + "' 이름을 자연스럽게 한 번 넣되(직접 써본 후기는 금지), "
                + "링크/해시태그 없이 '궁금해지는' 톤으로 다시 작성하세요.]";
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
