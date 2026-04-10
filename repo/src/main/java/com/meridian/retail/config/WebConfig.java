package com.meridian.retail.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web layer configuration:
 * - Registers static resource handlers for /static/** and /vendor/** so the offline-bundled
 *   Bootstrap, HTMX, Chart.js and Inter font assets are served from the JAR (no CDN).
 * - Configures multipart limits at 50 MB per file / 200 MB per request to support
 *   chunked attachment uploads (each chunk is small but a finalize call may pass metadata).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Bundled vendor JS/CSS — Bootstrap, HTMX, Chart.js, Inter, Bootstrap-Icons.
        registry.addResourceHandler("/vendor/**")
                .addResourceLocations("classpath:/static/vendor/");
        // Custom CSS/JS authored by us.
        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/");
        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/");
    }

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        // SPEC.md: default 50 MB per file, chunked uploads supported.
        factory.setMaxFileSize(DataSize.ofMegabytes(50));
        factory.setMaxRequestSize(DataSize.ofMegabytes(200));
        return factory.createMultipartConfig();
    }
}
