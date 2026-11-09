package com.jggroup.commentagent.service;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.jggroup.commentagent.config.AppProperties;
import com.jggroup.commentagent.domain.PostStatus;
import com.jggroup.commentagent.dto.PostResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Playwright 로 실제 유튜브 댓글 게시 (요구사항 §3).
 *  - 지정한 user-data-dir 로 persistent context 를 열어 '기본 프로필 자동제어'를 피함.
 *  - 유튜브 '한국어 화면'(hl=ko, locale ko-KR)에서 동작.
 *  - 댓글창 → 한 글자씩 타이핑 → 게시 → 내 댓글 보이는지 확인.
 *  - 단계별 진행 상태 콜백, 실패 시 사유 + 스크린샷.
 */
@Slf4j
@Service
public class BrowserPostService {

    private final AppProperties props;

    public BrowserPostService(AppProperties props) {
        this.props = props;
    }

    public PostResult post(AppProperties.Profile profile, String videoId,
                           String commentText, PostProgressListener listener) {
        AppProperties.Browser b = props.getBrowser();
        String url = "https://www.youtube.com/watch?v=" + videoId + "&hl=ko";
        String shotBase = profile.getName() + "_" + videoId + "_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        Path userDataDir = Paths.get(profile.getUserDataDir()).toAbsolutePath();
        ensureDir(userDataDir);
        ensureDir(Paths.get(b.getScreenshotDir()).toAbsolutePath());

        listener.onProgress(PostStatus.OPENING, "프로필 '" + profile.getName() + "' 여는 중…");

        try (Playwright playwright = Playwright.create()) {
            BrowserType.LaunchPersistentContextOptions opts =
                    new BrowserType.LaunchPersistentContextOptions()
                            .setHeadless(b.isHeadless())
                            .setChannel(b.getChannel())          // 설치된 크롬 사용
                            .setLocale("ko-KR")
                            .setViewportSize(1360, 900)
                            .setArgs(List.of("--lang=ko-KR", "--start-maximized"));

            try (BrowserContext context = playwright.chromium()
                    .launchPersistentContext(userDataDir, opts)) {

                Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
                page.setDefaultTimeout(b.getNavTimeoutMs());

                page.navigate(url, new Page.NavigateOptions().setTimeout(b.getNavTimeoutMs()));
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                dismissConsent(page);

                if (!isLoggedIn(page)) {
                    String shot = screenshot(page, shotBase + "_notlogin");
                    return PostResult.fail(
                            "프로필 '" + profile.getName() + "' 이(가) 구글/유튜브에 로그인되어 있지 않습니다. "
                                    + "해당 user-data-dir 로 크롬을 열어 먼저 로그인해 주세요.",
                            shot, commentText);
                }

                // 댓글 영역 로드: 아래로 스크롤
                loadComments(page);

                Locator box = openCommentBox(page);
                if (box == null) {
                    String shot = screenshot(page, shotBase + "_nocommentbox");
                    return PostResult.fail("댓글창을 찾지 못했습니다(댓글 사용 중지 또는 UI 변경 가능).",
                            shot, commentText);
                }

                // 한 글자씩 타이핑
                listener.onProgress(PostStatus.TYPING, "한 글자씩 입력 중…");
                box.click();
                page.waitForTimeout(400);
                Locator editable = page.locator("#contenteditable-root");
                editable.click();
                editable.pressSequentially(commentText,
                        new Locator.PressSequentiallyOptions().setDelay(b.getTypeDelayMs()));
                page.waitForTimeout(500);

                // 게시 버튼 클릭
                if (!clickSubmit(page)) {
                    String shot = screenshot(page, shotBase + "_nosubmit");
                    return PostResult.fail("게시 버튼을 찾지 못했습니다.", shot, commentText);
                }

                // 게시 반영 대기 + 내 댓글 확인
                page.waitForTimeout(3000);
                boolean confirmed = verifyPosted(page, commentText);
                String shot = screenshot(page, shotBase + (confirmed ? "_success" : "_unconfirmed"));

                if (confirmed) {
                    listener.onProgress(PostStatus.SUCCESS, "게시 완료 · 내 댓글 확인됨", shot);
                    return PostResult.ok(shot, commentText);
                }
                return PostResult.fail("게시는 시도했으나 화면에서 내 댓글을 확인하지 못했습니다.",
                        shot, commentText);
            }
        } catch (Exception e) {
            log.error("게시 실패 videoId={} profile={}", videoId, profile.getName(), e);
            return PostResult.fail("예외: " + e.getMessage(), null, commentText);
        }
    }

    // ── 단계별 헬퍼 ────────────────────────────────────────────

    private void dismissConsent(Page page) {
        try {
            for (String txt : List.of("모두 수락", "모두 거부", "동의", "수락")) {
                Locator btn = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(txt));
                if (btn.count() > 0 && btn.first().isVisible()) {
                    btn.first().click();
                    page.waitForTimeout(500);
                    break;
                }
            }
        } catch (Exception ignored) { }
    }

    /**
     * 유튜브 로그인 여부.
     * 유튜브 헤더(아바타/로그인 버튼)는 비동기로 늦게 렌더되므로, 즉시 판정하지 않고
     * 둘 중 하나가 나타날 때까지 최대 ~6초 폴링한다(성급한 미로그인 오판 방지).
     */
    private boolean isLoggedIn(Page page) {
        String signInSel = "a[aria-label='로그인'], a[href*='ServiceLogin'], "
                + "a[href*='accounts.google.com'], ytd-button-renderer a:has-text('로그인')";
        for (int i = 0; i < 12; i++) {
            try {
                if (page.locator("#avatar-btn").count() > 0) return true;   // 아바타 → 로그인됨
                Locator signIn = page.locator(signInSel);
                if (signIn.count() > 0 && signIn.first().isVisible()) return false; // 로그인 버튼 → 미로그인
            } catch (Exception ignored) { }
            page.waitForTimeout(500);
        }
        // 끝까지 불명확하면 아바타 유무로 최종 판정
        try {
            return page.locator("#avatar-btn").count() > 0;
        } catch (Exception e) {
            return true;
        }
    }

    private void loadComments(Page page) {
        for (int i = 0; i < 6; i++) {
            page.mouse().wheel(0, 1200);
            page.waitForTimeout(700);
            if (page.locator("#simplebox-placeholder").count() > 0) break;
        }
        try {
            page.locator("#comments").scrollIntoViewIfNeeded();
        } catch (Exception ignored) { }
        page.waitForTimeout(800);
    }

    private Locator openCommentBox(Page page) {
        Locator placeholder = page.locator("#simplebox-placeholder");
        if (placeholder.count() > 0) return placeholder.first();
        Locator byText = page.getByText("댓글 추가...", new Page.GetByTextOptions().setExact(false));
        if (byText.count() > 0) return byText.first();
        return null;
    }

    private boolean clickSubmit(Page page) {
        try {
            Locator submit = page.locator("#submit-button button, ytd-button-renderer#submit-button button");
            if (submit.count() > 0) {
                submit.first().click();
                return true;
            }
            Locator byRole = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("댓글"));
            if (byRole.count() > 0) {
                byRole.last().click();
                return true;
            }
        } catch (Exception e) {
            log.warn("게시 버튼 클릭 실패: {}", e.getMessage());
        }
        return false;
    }

    private boolean verifyPosted(Page page, String commentText) {
        try {
            String probe = commentText.strip();
            String needle = probe.length() > 20 ? probe.substring(0, 20) : probe;
            // 게시된 댓글 목록에서 내 텍스트 조각을 탐색
            Locator mine = page.locator("#contents #content-text:has-text(\"" + escape(needle) + "\")");
            mine.first().waitFor(new Locator.WaitForOptions().setTimeout(6000));
            return mine.count() > 0;
        } catch (Exception e) {
            // 입력창이 비워졌으면 성공으로 간주(폴백)
            try {
                String remaining = page.locator("#contenteditable-root").innerText();
                return remaining == null || remaining.isBlank();
            } catch (Exception ex) {
                return false;
            }
        }
    }

    private String escape(String s) {
        return s.replace("\"", "\\\"");
    }

    private String screenshot(Page page, String name) {
        try {
            Path dir = Paths.get(props.getBrowser().getScreenshotDir()).toAbsolutePath();
            ensureDir(dir);
            Path file = dir.resolve(name + ".png");
            page.screenshot(new Page.ScreenshotOptions().setPath(file).setFullPage(false));
            return file.toString();
        } catch (Exception e) {
            log.warn("스크린샷 실패: {}", e.getMessage());
            return null;
        }
    }

    private void ensureDir(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            throw new RuntimeException("디렉터리 생성 실패: " + dir, e);
        }
    }
}
