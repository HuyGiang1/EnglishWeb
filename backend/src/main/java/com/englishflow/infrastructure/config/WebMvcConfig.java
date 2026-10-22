package com.englishflow.infrastructure.config;

import com.englishflow.content.audio.AudioMediaSecurityInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AudioMediaSecurityInterceptor audioMediaSecurityInterceptor;

    public WebMvcConfig(AudioMediaSecurityInterceptor audioMediaSecurityInterceptor) {
        this.audioMediaSecurityInterceptor = audioMediaSecurityInterceptor;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/api/v1/media/audio/**")
            .addResourceLocations(
                "classpath:/static/media/audio/",
                "file:src/main/resources/static/media/audio/",
                "file:backend/src/main/resources/static/media/audio/"
            )
            .setCachePeriod(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(audioMediaSecurityInterceptor)
            .addPathPatterns("/api/v1/media/audio/**");
    }
}
