package io.tutoros.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Global CORS — permits any origin to call the tutor-api REST surface.
 *
 * Individual controllers also carry @CrossOrigin today; this config makes
 * future controllers "just work" without having to remember the annotation,
 * which is how QuizController briefly became non-functional for direct
 * calls in dev (the Vite proxy hid the bug for same-origin users).
 *
 * Tighten `allowedOriginPatterns` before shipping to production.
 */
@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .exposedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
