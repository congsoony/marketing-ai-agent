package com.jggroup.commentagent.service;

import com.jggroup.commentagent.config.AppProperties;
import com.jggroup.commentagent.dto.ProfileView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 각 크롬 프로필의 유튜브(구글) 로그인 여부를 가볍게 판별한다.
 * 브라우저를 띄우지 않고, 프로필의 Cookies 파일에 구글 인증 쿠키 이름이
 * 들어있는지(평문으로 저장되는 쿠키 '이름'만 확인)로 추정한다.
 */
@Slf4j
@Service
public class ProfileLoginService {

    /** 로그인 시 존재하는 구글/유튜브 인증 쿠키 이름들(값이 아니라 이름만 확인). */
    private static final String[] AUTH_COOKIE_NAMES = {"SAPISID", "__Secure-3PSID", "LOGIN_INFO"};

    private final AppProperties props;

    public ProfileLoginService(AppProperties props) {
        this.props = props;
    }

    public List<ProfileView> profilesWithStatus() {
        return props.getProfiles().stream()
                .map(p -> new ProfileView(p.getName(), isLoggedIn(p)))
                .toList();
    }

    /** Cookies 파일에 구글 인증 쿠키 이름이 있으면 로그인된 것으로 간주. */
    public boolean isLoggedIn(AppProperties.Profile profile) {
        try {
            // 신버전 크롬: Default/Network/Cookies, 구버전: Default/Cookies
            Path base = Paths.get(profile.getUserDataDir()).toAbsolutePath();
            Path cookies = base.resolve(Paths.get("Default", "Network", "Cookies"));
            if (!Files.exists(cookies)) cookies = base.resolve(Paths.get("Default", "Cookies"));
            if (!Files.exists(cookies)) return false;

            byte[] data = Files.readAllBytes(cookies);
            // 쿠키 '이름'은 SQLite 파일 안에 평문으로 저장됨 → 바이트를 문자열로 보고 탐색
            String text = new String(data, StandardCharsets.ISO_8859_1);
            for (String name : AUTH_COOKIE_NAMES) {
                if (text.contains(name)) return true;
            }
            return false;
        } catch (Exception e) {
            // 파일 잠김(해당 프로필 크롬 열림) 등 → 판단 불가, 미로그인으로 처리
            log.debug("프로필 로그인 판별 실패 {}: {}", profile.getName(), e.getMessage());
            return false;
        }
    }
}
