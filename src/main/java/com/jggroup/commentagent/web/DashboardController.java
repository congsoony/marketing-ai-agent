package com.jggroup.commentagent.web;

import com.jggroup.commentagent.config.AppProperties;
import com.jggroup.commentagent.dto.*;
import com.jggroup.commentagent.service.ClaudeCommentService;
import com.jggroup.commentagent.service.PostingService;
import com.jggroup.commentagent.service.YoutubeService;
import com.jggroup.commentagent.util.VideoIdExtractor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 대시보드 백엔드 API.
 *  ① GET /api/videos          최신 영상 목록
 *    GET /api/trending        '연애' 뜨기직전 영상(가점)
 *  ② GET /api/draft           영상 선택 → 고민 한 줄 + 댓글 초안 (링크 직접 입력 지원)
 *  ③ POST /api/post           게시 시작(운영규칙 강제)
 *    GET /api/post/{id}       게시 진행 상태 폴링
 *    GET /api/logs            기록(최신순)
 *    GET /api/config          화면 표시용 설정(규칙·프로필·키 설정 여부)
 */
@RestController
@RequestMapping("/api")
public class DashboardController {

    private final AppProperties props;
    private final YoutubeService youtube;
    private final ClaudeCommentService claude;
    private final PostingService posting;

    public DashboardController(AppProperties props, YoutubeService youtube,
                               ClaudeCommentService claude, PostingService posting) {
        this.props = props;
        this.youtube = youtube;
        this.claude = claude;
        this.posting = posting;
    }

    // ① 영상 불러오기
    @GetMapping("/videos")
    public List<VideoSummary> videos(@RequestParam(required = false) String query,
                                     @RequestParam(defaultValue = "20") int max) {
        return youtube.listLatest(query, max);
    }

    // 가점: 뜨기 직전 영상
    @GetMapping("/trending")
    public List<VideoSummary> trending() {
        return youtube.findTrending();
    }

    // ② 영상 선택 → 고민 한 줄 + 댓글 초안 (videoId 또는 url)
    @GetMapping("/draft")
    public DraftResult draft(@RequestParam(required = false) String videoId,
                             @RequestParam(required = false) String url) {
        String id = VideoIdExtractor.extract(videoId != null ? videoId : url);
        VideoContext ctx = youtube.buildContext(id);
        return claude.generate(ctx);
    }

    // ③ 게시 시작
    @PostMapping("/post")
    public PostStatusView post(@Valid @RequestBody PostRequest req) {
        return posting.start(req);
    }

    // 게시 진행 상태
    @GetMapping("/post/{id}")
    public PostStatusView status(@PathVariable Long id) {
        return posting.getStatus(id);
    }

    // 기록
    @GetMapping("/logs")
    public List<PostStatusView> logs() {
        return posting.recentLogs();
    }

    // 화면 표시용 설정
    @GetMapping("/config")
    public Map<String, Object> config() {
        AppProperties.Comment c = props.getComment();
        return Map.of(
                "brand", c.getBrand(),
                "brandDesc", c.getBrandDesc(),
                "minChars", c.getMinChars(),
                "maxChars", c.getMaxChars(),
                "dailyMax", props.getLimits().getDailyMaxComments(),
                "profiles", props.getProfiles().stream().map(AppProperties.Profile::getName).toList(),
                "youtubeKeyConfigured", props.getYoutube().isConfigured(),
                "claudeKeyConfigured", props.getClaude().isConfigured()
        );
    }
}
