package com.jggroup.commentagent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/** 실패/완료 스크린샷을 대시보드에서 볼 수 있도록 /shots/** 로 정적 서빙. */
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
    }
}
