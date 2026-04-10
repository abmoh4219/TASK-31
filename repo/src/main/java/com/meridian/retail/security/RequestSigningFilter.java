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
 * Required header: X-Signature (hex-encoded HMAC-SHA256).
 *
 * The signed canonical string is:
 *     HTTP_METHOD + "\n" + REQUEST_PATH + "\n" + X-Timestamp + "\n" + SHA256(body, hex)
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

        String provided = request.getHeader("X-Signature");
        String timestamp = request.getHeader("X-Timestamp");
        if (provided == null || provided.isBlank() || timestamp == null || timestamp.isBlank()) {
            forbid(response, "Missing X-Signature or X-Timestamp header");
            return;
        }

        ContentCachingRequestWrapper wrapped = new ContentCachingRequestWrapper(request);
        // Force the chain to read the body before we sign it; we still pass `wrapped` down.
        // For correct content caching we need to invoke the wrapped reader at least once.
        wrapped.getInputStream().readAllBytes(); // populates the cache

        byte[] body = wrapped.getContentAsByteArray();
        String bodyHashHex = sha256Hex(body);

        String canonical = request.getMethod() + "\n"
                + request.getRequestURI() + "\n"
                + timestamp + "\n"
                + bodyHashHex;

        String expected = hmacSha256Hex(canonical, signingSecret);
        if (!constantTimeEquals(expected, provided)) {
            log.warn("Request signature mismatch on {}", request.getRequestURI());
            forbid(response, "Invalid request signature");
            return;
        }

        chain.doFilter(wrapped, response);
    }

    private boolean appliesTo(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && request.getRequestURI().startsWith("/admin/");
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
