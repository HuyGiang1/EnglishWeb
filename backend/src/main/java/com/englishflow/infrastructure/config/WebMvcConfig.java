package com.englishflow.infrastructure.config;

import com.englishflow.content.audio.AudioManifestService;
import com.englishflow.content.audio.AudioMediaSecurityInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AudioMediaSecurityInterceptor audioMediaSecurityInterceptor;
    private final AudioManifestService audioManifestService;

    @Value("${englishflow.media.storage-root:}")
    private String storageRoot;

    public WebMvcConfig(
        AudioMediaSecurityInterceptor audioMediaSecurityInterceptor,
        AudioManifestService audioManifestService
    ) {
        this.audioMediaSecurityInterceptor = audioMediaSecurityInterceptor;
        this.audioManifestService = audioManifestService;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        var registration = registry.addResourceHandler("/api/v1/media/audio/**");

        if (storageRoot != null && !storageRoot.isBlank()) {
            String normalizedRoot = storageRoot.endsWith("/") ? storageRoot : storageRoot + "/";
            registration.addResourceLocations("file:" + normalizedRoot);
        }

        if (!audioManifestService.isProductionMode()) {
            registration.addResourceLocations(
                "classpath:/static/media/audio/",
                "file:src/main/resources/static/media/audio/",
                "file:backend/src/main/resources/static/media/audio/"
            );
        }

        registration.resourceChain(false)
            .addResolver(new org.springframework.web.servlet.resource.AbstractResourceResolver() {
                @Override
                protected org.springframework.core.io.Resource resolveResourceInternal(
                    jakarta.servlet.http.HttpServletRequest request,
                    String requestPath,
                    java.util.List<? extends org.springframework.core.io.Resource> locations,
                    org.springframework.web.servlet.resource.ResourceResolverChain chain
                ) {
                    java.io.File boundDir = audioManifestService.getBoundCuratedDir();
                    if (boundDir != null && boundDir.exists()) {
                        String fileName = AudioManifestService.extractFileName(requestPath);
                        java.io.File candidate = new java.io.File(boundDir, fileName);
                        if (candidate.exists() && candidate.isFile()) {
                            return new org.springframework.core.io.FileSystemResource(candidate);
                        }
                    }
                    String root = audioManifestService.getStorageRoot();
                    if (root != null && !root.isBlank()) {
                        java.io.File candidate = new java.io.File(root, requestPath);
                        if (candidate.exists() && candidate.isFile()) {
                            return new org.springframework.core.io.FileSystemResource(candidate);
                        }
                    }
                    return chain.resolveResource(request, requestPath, locations);
                }

                @Override
                protected String resolveUrlPathInternal(
                    String resourceUrlPath,
                    java.util.List<? extends org.springframework.core.io.Resource> locations,
                    org.springframework.web.servlet.resource.ResourceResolverChain chain
                ) {
                    return chain.resolveUrlPath(resourceUrlPath, locations);
                }
            });

        registration.setCachePeriod(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(audioMediaSecurityInterceptor)
            .addPathPatterns("/api/v1/media/audio/**");
    }
}
