package com.splitwisemoney.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    private static final int MAX_REQUESTS_PER_MINUTE = 15;
    private final Map<String, UserRateLimit> rateLimitMap = new ConcurrentHashMap<>();

    private static class UserRateLimit {
        long windowStartTimestamp;
        AtomicInteger requestCount;

        UserRateLimit(long timestamp) {
            this.windowStartTimestamp = timestamp;
            this.requestCount = new AtomicInteger(1);
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Apply rate limit protection to invitation endpoints
        if (path.contains("/api/groups/") && path.contains("/invite") || path.startsWith("/api/invitations/")) {
            String clientIp = getClientIp(request);

            if (isRateLimited(clientIp)) {
                log.warn("[RateLimitingFilter] ⚠️ Rate limit exceeded for IP={} on endpoint={}", clientIp, path);
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write("""
                    {
                        "status": 429,
                        "error": "Too Many Requests",
                        "message": "Rate limit exceeded. Please wait a minute before sending more invitations."
                    }
                    """);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isRateLimited(String clientIp) {
        long currentTime = System.currentTimeMillis();
        rateLimitMap.entrySet().removeIf(entry -> currentTime - entry.getValue().windowStartTimestamp > 60_000);

        UserRateLimit limit = rateLimitMap.compute(clientIp, (key, existing) -> {
            if (existing == null || currentTime - existing.windowStartTimestamp > 60_000) {
                return new UserRateLimit(currentTime);
            }
            existing.requestCount.incrementAndGet();
            return existing;
        });

        return limit.requestCount.get() > MAX_REQUESTS_PER_MINUTE;
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwarded = request.getHeader("X-Forwarded-For");
        if (xForwarded != null && !xForwarded.isBlank()) {
            return xForwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
