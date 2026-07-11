package com.splitwisemoney.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

/**
 * Production-only DataSource configuration.
 *
 * Railway injects DATABASE_URL as: postgresql://user:pass@host:port/db
 * HikariCP (JDBC) requires:         jdbc:postgresql://host:port/db
 *
 * This bean normalises the URL automatically so both formats work.
 * Active only when SPRING_PROFILES_ACTIVE=prod.
 */
@Configuration
@Profile("prod")
public class DataSourceConfig {

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String datasourceUsername;

    @Value("${spring.datasource.password}")
    private String datasourcePassword;

    /**
     * Normalise Railway DATABASE_URL to a valid JDBC URL.
     * Handles both formats:
     *   postgresql://user:pass@host:port/db  → jdbc:postgresql://host:port/db
     *   jdbc:postgresql://...                → unchanged
     */
    private String normaliseJdbcUrl(String url) {
        if (url == null) {
            throw new IllegalStateException("SPRING_DATASOURCE_URL environment variable is not set");
        }
        // Already a valid JDBC URL
        if (url.startsWith("jdbc:postgresql://") || url.startsWith("jdbc:postgres://")) {
            return url;
        }
        // Railway short-form: postgresql://user:pass@host:port/db
        if (url.startsWith("postgresql://") || url.startsWith("postgres://")) {
            return "jdbc:" + url;
        }
        return url;
    }

    @Bean
    public DataSource dataSource() {
        String jdbcUrl = normaliseJdbcUrl(datasourceUrl);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(datasourceUsername);
        config.setPassword(datasourcePassword);
        config.setDriverClassName("org.postgresql.Driver");

        // Connection pool tuned for Railway free tier
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(20000);
        config.setIdleTimeout(300000);
        config.setMaxLifetime(1200000);

        // Validate connection on borrow
        config.setConnectionTestQuery("SELECT 1");

        return new HikariDataSource(config);
    }
}
