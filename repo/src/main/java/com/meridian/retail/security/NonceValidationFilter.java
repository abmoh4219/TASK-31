package com.meridian.retail.security;

import com.meridian.retail.entity.UsedNonce;
import com.meridian.retail.repository.UsedNonceRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Anti-replay nonce + timestamp validation for privileged POST endpoints.
 *
 * Applies to: POST /admin/**  AND POST /approval/dual-approve/**.
 * Required headers:
 *   X-Nonce      — caller-generated unique value (UUID is fine)
 *   X-Timestamp  — epoch milliseconds when the request was constructed
 *
 * Rules:
 *   - The timestamp must be within ±5 minutes of server time.
 *   - The nonce must not exist in used_nonces (if it does -> replay).
 *   - On success the nonce is persisted with expires_at = now + 10 minutes so the same
 *     value cannot be reused. A scheduled task (UsedNonceCleanupTask) prunes expired rows.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NonceValidationFilter extends OncePerRequestFilter {

    private static final long ALLOWED_SKEW_MS = 5 * 60 * 1000L;
    private static final long NONCE_TTL_MINUTES = 10;

    private final UsedNonceRepository usedNonceRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        if (!appliesTo(request)) {
            chain.doFilter(request, response);
            return;
        }

        String nonce = request.getHeader("X-Nonce");
        String timestampStr = request.getHeader("X-Timestamp");

        if (nonce == null || nonce.isBlank() || timestampStr == null || timestampStr.isBlank()) {
            reject(response, "Missing X-Nonce or X-Timestamp header");
            return;
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            reject(response, "Invalid X-Timestamp value");
            return;
        }

        long now = System.currentTimeMillis();
        if (Math.abs(now - timestamp) > ALLOWED_SKEW_MS) {
            reject(response, "Request timestamp outside allowed window (±5 minutes)");
            return;
        }

        if (usedNonceRepository.existsByNonce(nonce)) {
            log.warn("Nonce replay detected: {}", nonce);
            reject(response, "Nonce already used (replay)");
            return;
        }

        // Persist as used; expiry slightly longer than the skew so the cleanup job can prune it.
        UsedNonce row = UsedNonce.builder()
                .nonce(nonce)
                .usedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(NONCE_TTL_MINUTES))
                .build();
        usedNonceRepository.save(row);

        chain.doFilter(request, response);
    }

    private boolean appliesTo(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) return false;
        String uri = request.getRequestURI();
        return uri.startsWith("/admin/") || uri.startsWith("/approval/dual-approve/");
    }

    private void reject(HttpServletResponse response, String reason) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + reason + "\"}");
    }
}
