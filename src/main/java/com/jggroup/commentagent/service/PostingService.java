package com.jggroup.commentagent.service;

import com.jggroup.commentagent.config.AppProperties;
import com.jggroup.commentagent.domain.PostLog;
import com.jggroup.commentagent.domain.PostStatus;
import com.jggroup.commentagent.dto.PostRequest;
import com.jggroup.commentagent.dto.PostResult;
import com.jggroup.commentagent.dto.PostStatusView;
import com.jggroup.commentagent.exception.NotFoundException;
import com.jggroup.commentagent.exception.RuleViolationException;
import com.jggroup.commentagent.repository.PostLogRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 게시 오케스트레이터 — ③ 게시의 운영 규칙(요구사항 §4)을 코드에서 강제한다.
 *  - 영상당 댓글 1개 (이미 성공 게시된 영상이면 거부)
 *  - 하루 게시 상한 (오늘 성공 건수 >= limit 이면 거부)
 *  - 프로필 로테이션 자동 배정(가점) 또는 지정 프로필 사용
 *  - 브라우저 작업은 단일 스레드로 순차 실행(프로필 잠금 충돌 방지) + 진행상태/기록 저장
 */
@Slf4j
@Service
public class PostingService {

    private final AppProperties props;
    private final PostLogRepository repo;
    private final BrowserPostService browser;

    /** 브라우저 게시는 한 번에 하나씩(프로필 lock 충돌 방지). */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "post-worker");
        t.setDaemon(true);
        return t;
    });

    private final AtomicInteger rotation = new AtomicInteger(0);
    private final Map<Long, String> noteMap = new ConcurrentHashMap<>();
    /** 현재 진행 중인 영상(중복 제출 방지). */
    private final Map<String, Boolean> inFlight = new ConcurrentHashMap<>();

    public PostingService(AppProperties props, PostLogRepository repo, BrowserPostService browser) {
        this.props = props;
        this.repo = repo;
        this.browser = browser;
    }

    /** ③ 게시 시작 — 규칙 검증 후 비동기 작업 등록. 즉시 현재 상태(OPENING)를 반환. */
    public PostStatusView start(PostRequest req) {
        String videoId = req.videoId();

        // ── 운영 규칙 강제 ──────────────────────────────
        if (repo.existsByVideoIdAndStatus(videoId, PostStatus.SUCCESS)) {
            throw new RuleViolationException("이미 이 영상에 댓글을 단 기록이 있습니다(영상당 1개).");
        }
        if (inFlight.putIfAbsent(videoId, Boolean.TRUE) != null) {
            throw new RuleViolationException("이 영상에 대한 게시가 이미 진행 중입니다.");
        }
        try {
            int todayCount = (int) repo.countByStatusAndCreatedAtAfter(PostStatus.SUCCESS, startOfToday());
            int dailyMax = props.getLimits().getDailyMaxComments();
            if (todayCount >= dailyMax) {
                throw new RuleViolationException(
                        "오늘 게시 상한에 도달했습니다 (%d/%d).".formatted(todayCount, dailyMax));
            }

            AppProperties.Profile profile = selectProfile(req.profileName());

            PostLog logEntry = PostLog.opening(
                    videoId, req.videoTitle(), req.channelTitle(), profile.getName(), req.comment());
            logEntry = repo.save(logEntry);
            final Long id = logEntry.getId();
            noteMap.put(id, "프로필 '" + profile.getName() + "' 여는 중…");

            submitJob(id, profile, videoId, req.comment());
            return toView(repo.findById(id).orElseThrow());
        } catch (RuntimeException e) {
            inFlight.remove(videoId);
            throw e;
        }
    }

    private void submitJob(Long id, AppProperties.Profile profile, String videoId, String comment) {
        PostProgressListener listener = (status, note, shot) -> {
            noteMap.put(id, note);
            repo.findById(id).ifPresent(l -> {
                l.setStatus(status);
                if (shot != null) l.setScreenshotPath(shot);
                repo.save(l);
            });
        };

        executor.submit(() -> {
            try {
                PostResult r = browser.post(profile, videoId, comment, listener);
                repo.findById(id).ifPresent(l -> {
                    if (r.success()) {
                        l.setStatus(PostStatus.SUCCESS);
                        noteMap.put(id, "게시 완료 · 내 댓글 확인됨");
                    } else {
                        l.setStatus(PostStatus.FAILED);
                        l.setFailReason(r.failReason());
                        noteMap.put(id, "실패: " + r.failReason());
                    }
                    if (r.screenshotPath() != null) l.setScreenshotPath(r.screenshotPath());
                    repo.save(l);
                });
            } catch (Exception e) {
                log.error("게시 작업 예외 id={}", id, e);
                repo.findById(id).ifPresent(l -> {
                    l.setStatus(PostStatus.FAILED);
                    l.setFailReason("예외: " + e.getMessage());
                    repo.save(l);
                });
                noteMap.put(id, "실패: " + e.getMessage());
            } finally {
                inFlight.remove(videoId);
            }
        });
    }

    /** 지정 프로필 또는 로테이션 자동 배정. */
    private AppProperties.Profile selectProfile(String requestedName) {
        List<AppProperties.Profile> profiles = props.getProfiles();
        if (profiles == null || profiles.isEmpty()) {
            throw new RuleViolationException("설정된 크롬 프로필이 없습니다(app.profiles).");
        }
        if (requestedName != null && !requestedName.isBlank()) {
            return profiles.stream()
                    .filter(p -> requestedName.equals(p.getName()))
                    .findFirst()
                    .orElseThrow(() -> new RuleViolationException("프로필을 찾을 수 없습니다: " + requestedName));
        }
        int idx = Math.floorMod(rotation.getAndIncrement(), profiles.size());
        return profiles.get(idx);
    }

    public PostStatusView getStatus(Long id) {
        return repo.findById(id).map(this::toView)
                .orElseThrow(() -> new NotFoundException("작업을 찾을 수 없습니다: " + id));
    }

    public List<PostStatusView> recentLogs() {
        return repo.findAllByOrderByCreatedAtDesc().stream().map(this::toView).toList();
    }

    private PostStatusView toView(PostLog l) {
        return PostStatusView.of(l, noteMap.get(l.getId()), toShotUrl(l.getScreenshotPath()));
    }

    /** 절대 경로 → 정적 서빙 URL(/shots/파일명). */
    private String toShotUrl(String path) {
        if (path == null || path.isBlank()) return null;
        return "/shots/" + Paths.get(path).getFileName();
    }

    private java.time.Instant startOfToday() {
        ZoneId zone = ZoneId.systemDefault();
        return LocalDate.now(zone).atStartOfDay(zone).toInstant();
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
