package com.splitwisemoney.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * Executes before the Spring ApplicationContext is created.
 * Normalizes Railway's postgresql:// URL to standard jdbc:postgresql://
 * before Spring Boot auto-configuration initializes HikariCP and Flyway.
 */
public class RailwayEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> overrides = new HashMap<>();

        // Intercept and normalize DATABASE_URL or SPRING_DATASOURCE_URL
        String url = environment.getProperty("DATABASE_URL");
        if (url == null || url.trim().isEmpty()) {
            url = environment.getProperty("SPRING_DATASOURCE_URL");
        }

        if (url != null && url.startsWith("postgresql://")) {
            try {
                java.net.URI uri = new java.net.URI(url);
                String userInfo = uri.getUserInfo();
                if (userInfo != null) {
                    String[] parts = userInfo.split(":", 2);
                    if (parts.length > 0) {
                        overrides.put("spring.datasource.username", parts[0]);
                    }
                    if (parts.length > 1) {
                        overrides.put("spring.datasource.password", parts[1]);
                    }
                }
                
                String host = uri.getHost();
                int port = uri.getPort() != -1 ? uri.getPort() : 5432;
                String path = uri.getPath();
                
                String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + (path != null ? path : "");
                overrides.put("spring.datasource.url", jdbcUrl);
            } catch (java.net.URISyntaxException e) {
                // Fallback to basic prepending if URI parsing fails
                overrides.put("spring.datasource.url", "jdbc:" + url);
            }
        }

        // If Railway assigned a PORT, we are in the cloud. Force prod profile if not explicitly set.
        if (environment.getProperty("PORT") != null) {
            String activeProfiles = environment.getProperty("spring.profiles.active");
            if (activeProfiles == null || activeProfiles.trim().isEmpty()) {
                overrides.put("spring.profiles.active", "prod");
            }
        }

        if (!overrides.isEmpty()) {
            PropertySource<?> source = new MapPropertySource("railwayOverrides", overrides);
            // Add as first priority so it overrides environment variables and application.properties
            environment.getPropertySources().addFirst(source);
        }
    }
}
