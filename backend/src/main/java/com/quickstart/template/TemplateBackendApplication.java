package com.quickstart.template;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@SpringBootApplication
public class TemplateBackendApplication {

    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    public static void main(String[] args) {
        SpringApplication.run(TemplateBackendApplication.class, args);
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                String[] origins = parseAllowedOrigins();
                applyAllowedOrigins(registry.addMapping("/api/**"), origins)
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true);
                applyAllowedOrigins(registry.addMapping("/uploads/**"), origins)
                        .allowedMethods("GET")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
            
            @Override
            public void addResourceHandlers(org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry registry) {
                registry.addResourceHandler("/uploads/images/**")
                        .addResourceLocations("file:uploads/images/")
                        .setCachePeriod(3600);
            }
        };
    }

    private String[] parseAllowedOrigins() {
        return Arrays.stream(allowedOrigins.split("\\s*,\\s*"))
                .filter(StringUtils::hasText)
                .toArray(String[]::new);
    }

    private CorsRegistration applyAllowedOrigins(CorsRegistration registration, String[] origins) {
        // Spring rejects allowCredentials(true) together with allowedOrigins("*"), so switch to patterns for that opt-in case.
        if (origins.length == 1 && "*".equals(origins[0])) {
            return registration.allowedOriginPatterns("*");
        }
        return registration.allowedOrigins(origins);
    }
}
