package com.meridian.retail.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-user rate limiter using Bucket4j (in-memory token buckets).
 *
 * SPEC.md:
 *   - Standard endpoints: 60 requests/minute per user
 *   - Export endpoints (/analytics/export/**): 10 requests/minute per user
 *
 * This is intentionally a simple in-memory implementation. For a single-tenant on-prem
 * deployment that's adequate. The bucket key is "<scope>:<username>" so the standard
 * and export quotas are tracked independently.
 *
 * On exhaustion the filter writes HTTP 429 + Retry-After: 60 and a small JSON body and
 * does NOT continue the chain.
 */
@Component
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Value("${app.rate-limit.standard:60}")
    private long standardLimit;

    @Value("${app.rate-limit.export:10}")
    private long exportLimit;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        // Static resources and infrastructure paths are excluded — only page/API requests count.
        String uri = request.getRequestURI();
        if (uri.startsWith("/css/") || uri.startsWith("/js/") || uri.startsWith("/vendor/")
                || uri.startsWith("/actuator") || uri.startsWith("/error")
                || uri.startsWith("/captcha/")) {
            chain.doFilter(request, response);
            return;
        }

        // Anonymous traffic is excluded — login attempts have their own IP-based throttling.
        String username = currentUsername();
        if (username == null) {
            chain.doFilter(request, response);
            return;
        }

        boolean isExport = uri.startsWith("/analytics/export/download");
        long capacity = isExport ? exportLimit : standardLimit;
        String bucketKey = (isExport ? "export:" : "standard:") + username;

        Bucket bucket = buckets.computeIfAbsent(bucketKey, k ->
                Bucket.builder()
                      .addLimit(Bandwidth.simple(capacity, Duration.ofMinutes(1)))
                      .build()
        );

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded for user={} bucket={}", username, bucketKey);
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"error\":\"Rate limit exceeded. Try again in 60 seconds.\"}"
            );
        }
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return auth.getName();
    }
}
