package com.jggroup.commentagent.service;

import com.jggroup.commentagent.config.AppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 프로필 로그인 판별 — Cookies 파일에 구글 인증 쿠키 이름이 있으면 로그인으로 본다.
 * 실제 크롬 없이 임시 디렉터리에 Cookies 파일을 흉내 내 검증.
 */
class ProfileLoginServiceTest {

    private ProfileLoginService service() {
        return new ProfileLoginService(new AppProperties());
    }

    private AppProperties.Profile profile(Path dir) {
        AppProperties.Profile p = new AppProperties.Profile();
        p.setName("테스트");
        p.setUserDataDir(dir.toString());
        return p;
    }

    @Test
    @DisplayName("Cookies 에 SAPISID 가 있으면 로그인됨으로 판정")
    void loggedIn_whenAuthCookiePresent(@TempDir Path dir) throws Exception {
        Path cookies = dir.resolve("Default").resolve("Network").resolve("Cookies");
        Files.createDirectories(cookies.getParent());
        Files.write(cookies, ("SQLite format 3\u0000 ... SAPISID ... other").getBytes(StandardCharsets.UTF_8));

        assertThat(service().isLoggedIn(profile(dir))).isTrue();
    }

    @Test
    @DisplayName("Cookies 에 인증 쿠키가 없으면 미로그인")
    void notLoggedIn_whenNoAuthCookie(@TempDir Path dir) throws Exception {
        Path cookies = dir.resolve("Default").resolve("Network").resolve("Cookies");
        Files.createDirectories(cookies.getParent());
        Files.write(cookies, "SQLite format 3\u0000 ... VISITOR_INFO ... ".getBytes(StandardCharsets.UTF_8));

        assertThat(service().isLoggedIn(profile(dir))).isFalse();
    }

    @Test
    @DisplayName("Cookies 파일 자체가 없으면 미로그인")
    void notLoggedIn_whenNoCookieFile(@TempDir Path dir) {
        assertThat(service().isLoggedIn(profile(dir))).isFalse();
    }
}
