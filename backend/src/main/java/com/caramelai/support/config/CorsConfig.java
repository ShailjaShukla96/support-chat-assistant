package com.caramelai.support.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Allows only the configured web origin to call the API from a browser. */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final AppProperties props;

    public CorsConfig(AppProperties props) {
        this.props = props;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(props.cors().allowedOrigin())
                .allowedMethods("GET", "POST", "PUT")
                .allowedHeaders("Content-Type", "X-Agent-Token", "X-Request-Id")
                .exposedHeaders("X-Request-Id");
    }
}
