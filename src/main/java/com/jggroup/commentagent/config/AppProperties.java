package com.jggroup.commentagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * application.yml 의 app.* 설정 바인딩.
 * 요구사항의 profiles / comment / limits 설정을 코드에서 강제하기 위한 단일 소스.
 */
@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Youtube youtube = new Youtube();
    private Claude claude = new Claude();
    private Comment comment = new Comment();
    private Limits limits = new Limits();
    private List<Profile> profiles = new ArrayList<>();
    private Browser browser = new Browser();
    private Trending trending = new Trending();

    @Data
    public static class Youtube {
        private String apiKey;
        private String region = "KR";
        private String relevanceLanguage = "ko";
        private String defaultQuery = "연애";

        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    @Data
    public static class Claude {
        private String apiKey;
        private String model = "claude-sonnet-4-6";
        private String baseUrl = "https://api.anthropic.com";
        private String anthropicVersion = "2023-06-01";
        private int maxTokens = 1024;

        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    @Data
    public static class Comment {
        private int minChars = 40;
        private int maxChars = 120;
        private String brand = "운명원";
        private String brandDesc = "연애 사주 잘 봐주는 곳으로 유명한 곳";
    }

    @Data
    public static class Limits {
        private int dailyMaxComments = 10;
    }

    /** 내 컴퓨터의 크롬 프로필 (유튜브1/2/3). 서로 다른 구글 계정 로그인 상태여야 함. */
    @Data
    public static class Profile {
        private String name;
        private String userDataDir;
    }

    @Data
    public static class Browser {
        private String channel = "chrome";
        private boolean headless = false;
        private int typeDelayMs = 120;
        private int navTimeoutMs = 30000;
        private String screenshotDir = "./screenshots";
    }

    @Data
    public static class Trending {
        private String query = "연애";
        private long minViews = 1000;
        private long maxViews = 5000;
        private long minViewsPerHour = 150;
        private int publishedWithinHours = 48;
    }
}
