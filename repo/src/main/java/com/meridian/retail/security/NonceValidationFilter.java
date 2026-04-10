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
 * Applies to: POST /admin/**, POST /approval/dual-approve/**,
 *             POST /approval/{id}/approve-first, POST /approval/{id}/approve-second.
 *
 * The two approve-first/second paths are the actual UI completion endpoints for the
 * dual-approval workflow (R4 audit HIGH #1). They are protected without any form-
 * encoded bypass — the browser fetches a nonce from {@code GET /approval/nonce} and
 * submits the form with X-Nonce + X-Timestamp headers via JS (templates/approval/queue.html).
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

        // Browser form submissions cannot easily set custom headers without going
        // through fetch() (which loses RedirectAttributes flash messages). We accept
        // the same nonce/timestamp from either headers (programmatic clients) OR form
        // parameters _nonce / _timestamp (browser forms enriched by JS before submit).
        String nonce = request.getHeader("X-Nonce");
        if (nonce == null || nonce.isBlank()) nonce = request.getParameter("_nonce");
        String timestampStr = request.getHeader("X-Timestamp");
        if (timestampStr == null || timestampStr.isBlank()) timestampStr = request.getParameter("_timestamp");

        if (nonce == null || nonce.isBlank() || timestampStr == null || timestampStr.isBlank()) {
            reject(response, "Missing X-Nonce or X-Timestamp (header or form field)");
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
        // Sign-form endpoints issue the very nonce/signature this filter validates,
        // so they must be excluded to avoid a chicken-and-egg loop. They are still
        // gated by Spring Security role checks.
        if (uri.equals("/admin/sign-form") || uri.equals("/approval/sign-form")) return false;
        if (uri.startsWith("/admin/")) return true;
        if (uri.startsWith("/approval/dual-approve/")) return true;
        // R4 HIGH #1: cover the UI completion endpoints for dual approval. Pattern is
        // /approval/{id}/approve-first or /approval/{id}/approve-second.
        return uri.startsWith("/approval/")
                && (uri.endsWith("/approve-first") || uri.endsWith("/approve-second"));
    }

    /**
     * R4 HIGH #2 fix: the previous broad form-encoded bypass for /admin/** has been
     * removed. Privileged browser POSTs now carry _nonce / _timestamp hidden fields
     * injected by static/js/nonce-form.js, so the same anti-replay validation runs for
     * both browser and API callers. RequestSigningFilter still keeps a form-encoded
     * bypass because HMAC signing requires a shared secret that cannot live in the
     * browser — see SecurityDesignDecisions.md for the threat-model rationale.
     *
     * The shouldNotFilter override therefore intentionally returns the OncePerRequestFilter
     * default (false) — every request flows through doFilterInternal, which itself
     * filters by HTTP method and URL via {@link #appliesTo(HttpServletRequest)}.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return false;
    }

    private void reject(HttpServletResponse response, String reason) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + reason + "\"}");
    }
}
