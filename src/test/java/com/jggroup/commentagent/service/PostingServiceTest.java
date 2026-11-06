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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 게시 오케스트레이터 PostingService 테스트.
 * 요구사항 §4 운영규칙(영상당 1개 · 하루 상한 · 프로필 로테이션)을
 * 코드가 '시작 전에' 강제하는지 Mockito 로 검증(브라우저 실제 구동 없음).
 */
class PostingServiceTest {

    private AppProperties props;
    private PostLogRepository repo;
    private BrowserPostService browser;
    private PostingService service;

    @BeforeEach
    void setUp() {
        props = new AppProperties();
        repo = mock(PostLogRepository.class);
        browser = mock(BrowserPostService.class);
        service = new PostingService(props, repo, browser);
    }

    private PostRequest req(String videoId, String profileName) {
        return new PostRequest(videoId, "제목", "채널", "가".repeat(60), profileName);
    }

    private void addProfiles(String... names) {
        for (String n : names) {
            AppProperties.Profile p = new AppProperties.Profile();
            p.setName(n);
            p.setUserDataDir("./chrome-profiles/" + n);
            props.getProfiles().add(p);
        }
    }

    /** repo.save/findById 가 id를 부여하고 되돌려주도록(실제 DB 없이). */
    private void stubPersistence() {
        AtomicLong seq = new AtomicLong();
        ConcurrentHashMap<Long, PostLog> store = new ConcurrentHashMap<>();
        when(repo.save(any(PostLog.class))).thenAnswer(inv -> {
            PostLog l = inv.getArgument(0);
            if (l.getId() == null) ReflectionTestUtils.setField(l, "id", seq.incrementAndGet());
            store.put(l.getId(), l);
            return l;
        });
        when(repo.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(store.get(inv.getArgument(0))));
    }

    @Test
    @DisplayName("영상당 1개: 이미 성공 게시된 영상이면 거부하고 브라우저를 열지 않는다")
    void rejectsDuplicateVideo() {
        addProfiles("유튜브1");
        when(repo.existsByVideoIdAndStatus("VID1", PostStatus.SUCCESS)).thenReturn(true);

        assertThatThrownBy(() -> service.start(req("VID1", null)))
                .isInstanceOf(RuleViolationException.class)
                .hasMessageContaining("영상당 1개");

        verify(repo, never()).save(any());
        verifyNoInteractions(browser);
    }

    @Test
    @DisplayName("하루 상한: 오늘 성공 건수가 상한 이상이면 거부")
    void rejectsWhenDailyLimitReached() {
        addProfiles("유튜브1");
        props.getLimits().setDailyMaxComments(10);
        when(repo.existsByVideoIdAndStatus(anyString(), eq(PostStatus.SUCCESS))).thenReturn(false);
        when(repo.countByStatusAndCreatedAtAfter(eq(PostStatus.SUCCESS), any(Instant.class))).thenReturn(10L);

        assertThatThrownBy(() -> service.start(req("VID2", null)))
                .isInstanceOf(RuleViolationException.class)
                .hasMessageContaining("상한");

        verifyNoInteractions(browser);
    }

    @Test
    @DisplayName("프로필 미설정이면 거부")
    void rejectsWhenNoProfiles() {
        // addProfiles 호출 안 함 → 빈 목록
        when(repo.existsByVideoIdAndStatus(anyString(), eq(PostStatus.SUCCESS))).thenReturn(false);
        when(repo.countByStatusAndCreatedAtAfter(eq(PostStatus.SUCCESS), any(Instant.class))).thenReturn(0L);

        assertThatThrownBy(() -> service.start(req("VID3", null)))
                .isInstanceOf(RuleViolationException.class)
                .hasMessageContaining("프로필");
    }

    @Test
    @DisplayName("지정한 프로필 이름이 없으면 거부")
    void rejectsUnknownProfileName() {
        addProfiles("유튜브1");
        when(repo.existsByVideoIdAndStatus(anyString(), eq(PostStatus.SUCCESS))).thenReturn(false);
        when(repo.countByStatusAndCreatedAtAfter(eq(PostStatus.SUCCESS), any(Instant.class))).thenReturn(0L);

        assertThatThrownBy(() -> service.start(req("VID4", "없는프로필")))
                .isInstanceOf(RuleViolationException.class)
                .hasMessageContaining("프로필");
    }

    @Test
    @DisplayName("getStatus: 없는 작업 id면 NotFoundException")
    void getStatus_notFound() {
        when(repo.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getStatus(999L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("정상 시작: OPENING 기록 저장 후 상태 뷰 반환, 비동기 게시 실행")
    void startsSuccessfully() {
        addProfiles("유튜브1");
        when(repo.existsByVideoIdAndStatus(anyString(), eq(PostStatus.SUCCESS))).thenReturn(false);
        when(repo.countByStatusAndCreatedAtAfter(eq(PostStatus.SUCCESS), any(Instant.class))).thenReturn(0L);
        when(browser.post(any(), anyString(), anyString(), any()))
                .thenReturn(PostResult.ok(null, "가".repeat(60)));
        stubPersistence();

        PostStatusView view = service.start(req("VID5", null));

        assertThat(view.videoId()).isEqualTo("VID5");
        assertThat(view.profileName()).isEqualTo("유튜브1");
        assertThat(view.comment()).isEqualTo("가".repeat(60));
        // 비동기 작업이 브라우저 게시를 실제로 호출했는지(최대 2초 대기)
        verify(browser, timeout(2000)).post(any(), eq("VID5"), anyString(), any());
    }

    @Test
    @DisplayName("프로필 로테이션(가점): 이름 미지정 시 유튜브1→2→3 순환")
    void rotatesProfiles() {
        addProfiles("유튜브1", "유튜브2", "유튜브3");
        when(repo.existsByVideoIdAndStatus(anyString(), eq(PostStatus.SUCCESS))).thenReturn(false);
        when(repo.countByStatusAndCreatedAtAfter(eq(PostStatus.SUCCESS), any(Instant.class))).thenReturn(0L);
        when(browser.post(any(), anyString(), anyString(), any()))
                .thenReturn(PostResult.ok(null, "c"));
        stubPersistence();

        String p1 = service.start(req("A", null)).profileName();
        String p2 = service.start(req("B", null)).profileName();
        String p3 = service.start(req("C", null)).profileName();

        assertThat(p1).isEqualTo("유튜브1");
        assertThat(p2).isEqualTo("유튜브2");
        assertThat(p3).isEqualTo("유튜브3");
    }
}
