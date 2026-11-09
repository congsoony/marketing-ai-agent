package com.jggroup.commentagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 각 크롬 프로필의 유튜브(구글) 로그인 여부 + 로그인된 계정(이름/이메일)을 판별한다.
 * 브라우저를 띄우지 않고 프로필 파일만 읽는다.
 *  - 로그인 여부: Cookies 파일에 구글 인증 쿠키 이름(SAPISID 등) 존재 여부
 *  - 계정 이름/이메일: Default/Preferences 의 account_info 에서 추출
 */
@Slf4j
@Service
public class ProfileLoginService {

    private static final String[] AUTH_COOKIE_NAMES = {"SAPISID", "__Secure-3PSID", "LOGIN_INFO"};

    private final AppProperties props;
    private final ObjectMapper mapper = new ObjectMapper();

    public ProfileLoginService(AppProperties props) {
        this.props = props;
    }

    public List<ProfileView> profilesWithStatus() {
        return props.getProfiles().stream()
                .map(p -> {
                    String[] acct = readAccount(p);   // [이름, 이메일] (없으면 [null,null])
                    boolean loggedIn = isLoggedIn(p) || acct[1] != null;
                    return new ProfileView(p.getName(), loggedIn, acct[0], acct[1]);
                })
                .toList();
    }

    /** Cookies 파일에 구글 인증 쿠키 이름이 있으면 로그인된 것으로 간주. */
    public boolean isLoggedIn(AppProperties.Profile profile) {
        try {
            Path base = Paths.get(profile.getUserDataDir()).toAbsolutePath();
            Path cookies = base.resolve(Paths.get("Default", "Network", "Cookies"));
            if (!Files.exists(cookies)) cookies = base.resolve(Paths.get("Default", "Cookies"));
            if (!Files.exists(cookies)) return false;

            String text = new String(Files.readAllBytes(cookies), StandardCharsets.ISO_8859_1);
            for (String name : AUTH_COOKIE_NAMES) {
                if (text.contains(name)) return true;
            }
            return false;
        } catch (Exception e) {
            log.debug("프로필 로그인 판별 실패 {}: {}", profile.getName(), e.getMessage());
            return false;
        }
    }

    /** Default/Preferences 의 account_info 에서 첫 계정의 이름/이메일을 꺼낸다. */
    public String[] readAccount(AppProperties.Profile profile) {
        try {
            Path prefs = Paths.get(profile.getUserDataDir()).toAbsolutePath()
                    .resolve(Paths.get("Default", "Preferences"));
            if (!Files.exists(prefs)) return new String[]{null, null};

            JsonNode root = mapper.readTree(prefs.toFile());
            JsonNode accounts = root.path("account_info");
            if (accounts.isArray()) {
                for (JsonNode a : accounts) {
                    String email = a.path("email").asText(null);
                    if (email != null && !email.isBlank()) {
                        String name = firstNonBlank(
                                a.path("full_name").asText(null),
                                a.path("given_name").asText(null),
                                email);
                        return new String[]{name, email};
                    }
                }
            }
            return new String[]{null, null};
        } catch (Exception e) {
            log.debug("프로필 계정 정보 읽기 실패 {}: {}", profile.getName(), e.getMessage());
            return new String[]{null, null};
        }
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
