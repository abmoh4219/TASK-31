package com.meridian.retail.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * HMAC-SHA256 request-signing filter for privileged endpoints.
 *
 * Applies to: POST /admin/**.
 *
 * Two canonical-string modes are accepted:
 *
 *   1. Header mode (programmatic / JSON / multipart callers).
 *      Header X-Signature carries the hex HMAC-SHA256 of:
 *          HTTP_METHOD + "\n" + REQUEST_PATH + "\n" + X-Timestamp + "\n" + SHA256(body, hex)
 *
 *   2. Form mode (browser POSTs enriched by static/js/nonce-form.js).
 *      Form parameter _signature carries the hex HMAC-SHA256 of:
 *          HTTP_METHOD + "\n" + REQUEST_PATH + "\n" + _timestamp + "\n" + _nonce
 *      The body hash is intentionally omitted in form mode because the body itself
 *      contains the _signature parameter (chicken-and-egg). Replay protection comes
 *      from the single-use nonce — see {@link NonceValidationFilter} which runs before
 *      this filter and consumes the nonce. Browser-side computation of HMAC is avoided
 *      by routing the JS through {@code RequestSigningService.sign(...)} via
 *      {@code POST /admin/sign-form} (and {@code /approval/sign-form}), which performs
 *      the signing server-side using the same secret.
 *
 * R4 audit HIGH #2 fix: the previous {@code application/x-www-form-urlencoded} bypass
 * for /admin/** is removed. Browser form POSTs flow through this filter and must carry
 * a valid {@code _signature} form parameter or be rejected.
 *
 * The HMAC key is the value of {@code app.signing.secret} in application.yml — hardcoded
 * locally so QA can build and run with zero configuration. In a real production deployment
 * this would be sourced from a secret manager.
 *
 * Wrapping the request with {@link ContentCachingRequestWrapper} lets us read the body for
 * hashing without consuming it from the downstream chain.
 */
@Component
@Slf4j
public class RequestSigningFilter extends OncePerRequestFilter {

    @Value("${app.signing.secret}")
    private String signingSecret;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        if (!appliesTo(request)) {
            chain.doFilter(request, response);
            return;
        }

        // ---- Mode 1: header-based signing for programmatic / JSON / multipart callers ----
        String headerSig = request.getHeader("X-Signature");
        String headerTs = request.getHeader("X-Timestamp");
        if (headerSig != null && !headerSig.isBlank()) {
            if (headerTs == null || headerTs.isBlank()) {
                forbid(response, "Missing X-Timestamp header");
                return;
            }
            ContentCachingRequestWrapper wrapped = new ContentCachingRequestWrapper(request);
            // Force the chain to read the body before we sign it; we still pass `wrapped` down.
            wrapped.getInputStream().readAllBytes();
            byte[] body = wrapped.getContentAsByteArray();
            String bodyHashHex = sha256Hex(body);

            String canonical = request.getMethod() + "\n"
                    + request.getRequestURI() + "\n"
                    + headerTs + "\n"
                    + bodyHashHex;
            String expected = hmacSha256Hex(canonical, signingSecret);
            if (!constantTimeEquals(expected, headerSig)) {
                log.warn("Request signature mismatch (header mode) on {}", request.getRequestURI());
                forbid(response, "Invalid request signature");
                return;
            }
            chain.doFilter(wrapped, response);
            return;
        }

        // ---- Mode 2: form-parameter signing for browser POSTs ----
        // Browser forms are enriched by static/js/nonce-form.js with _nonce / _timestamp /
        // _signature hidden fields fetched from the server-side sign-form endpoint.
        String formSig = request.getParameter("_signature");
        String formTs = request.getParameter("_timestamp");
        String formNonce = request.getParameter("_nonce");
        if (formSig != null && !formSig.isBlank()) {
            if (formTs == null || formTs.isBlank() || formNonce == null || formNonce.isBlank()) {
                forbid(response, "Missing _timestamp or _nonce form parameter alongside _signature");
                return;
            }
            String canonical = request.getMethod() + "\n"
                    + request.getRequestURI() + "\n"
                    + formTs + "\n"
                    + formNonce;
            String expected = hmacSha256Hex(canonical, signingSecret);
            if (!constantTimeEquals(expected, formSig)) {
                log.warn("Request signature mismatch (form mode) on {}", request.getRequestURI());
                forbid(response, "Invalid request signature");
                return;
            }
            chain.doFilter(request, response);
            return;
        }

        forbid(response, "Missing X-Signature header or _signature form parameter");
    }

    /**
     * Compute the form-mode signature for a (method, path, timestamp, nonce) tuple.
     * Used by {@link com.meridian.retail.controller.NonceController} so the JS layer
     * can fetch a server-issued signature without ever holding the HMAC secret.
     */
    public String signFormCanonical(String method, String path, String timestamp, String nonce) {
        String canonical = method + "\n" + path + "\n" + timestamp + "\n" + nonce;
        return hmacSha256Hex(canonical, signingSecret);
    }

    private boolean appliesTo(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) return false;
        String uri = request.getRequestURI();
        // Sign-form endpoints issue the very signature this filter validates — exclude
        // them to avoid a chicken-and-egg loop. Spring Security role checks still gate them.
        if (uri.equals("/admin/sign-form") || uri.equals("/approval/sign-form")) return false;
        if (uri.startsWith("/admin/")) return true;
        // Approval completion endpoints: POST /approval/{id}/approve-first,
        // POST /approval/{id}/approve-second, POST /approval/dual-approve/**.
        if (uri.startsWith("/approval/dual-approve/")) return true;
        return uri.startsWith("/approval/")
                && (uri.endsWith("/approve-first") || uri.endsWith("/approve-second"));
    }

    /**
     * R4 audit HIGH #2 fix: form-encoded bypass for /admin/** has been removed.
     * Browser forms now reach this filter and validate via form-mode (_signature
     * parameter, server-issued through {@code POST /admin/sign-form}). The override
     * therefore returns false — every request flows through doFilterInternal, which
     * applies its own method/URL filter via {@link #appliesTo(HttpServletRequest)}.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return false;
    }

    private void forbid(HttpServletResponse response, String reason) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + reason + "\"}");
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String hmacSha256Hex(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    /** Constant-time comparison to avoid timing attacks. */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
