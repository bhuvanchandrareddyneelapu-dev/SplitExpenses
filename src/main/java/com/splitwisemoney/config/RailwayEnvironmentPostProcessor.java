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

        // Intercept and normalize SPRING_DATASOURCE_URL
        String url = environment.getProperty("SPRING_DATASOURCE_URL");
        if (url != null && url.startsWith("postgresql://")) {
            overrides.put("spring.datasource.url", "jdbc:" + url);
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
