package com.jggroup.commentagent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/** 실패/완료 스크린샷 정적 서빙(/shots/**) + 대시보드 정적 리소스 캐시 끄기. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties props;

    public WebConfig(AppProperties props) {
        this.props = props;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(props.getBrowser().getScreenshotDir())
                .toAbsolutePath().toUri().toString(); // file:///C:/.../screenshots/
        registry.addResourceHandler("/shots/**")
                .addResourceLocations(location);

        // index.html / style.css / app.js 는 개발 중 자주 바뀌므로 브라우저가
        // 절대 캐시하지 않도록 no-store 지정 → 새로고침만 하면 항상 최신이 반영됨.
        registry.addResourceHandler("/", "/index.html", "/style.css", "/app.js")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noStore());
    }
}
