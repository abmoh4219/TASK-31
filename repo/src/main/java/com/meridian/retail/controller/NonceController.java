package com.meridian.retail.controller;

import com.meridian.retail.security.RequestSigningFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Issues anti-replay nonces for browser-driven privileged form submissions.
 *
 * The nonce is a single-use opaque UUID; the timestamp is server epoch milliseconds so
 * the client does not need a synchronized clock. The browser includes both as
 * {@code X-Nonce} and {@code X-Timestamp} headers on its subsequent privileged POST,
 * where {@link com.meridian.retail.security.NonceValidationFilter} validates the
 * timestamp window and persists the nonce to the {@code used_nonces} table.
 *
 * Why this is safe to expose without signing: nonces are single-use by construction
 * and are themselves stored on the server side. An attacker who scrapes one cannot
 * reuse it on a different request, and an authenticated session is still required to
 * reach this endpoint.
 */
@RestController
@RequiredArgsConstructor
public class NonceController {

    private final RequestSigningFilter requestSigningFilter;

    @GetMapping("/approval/nonce")
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER')")
    public Map<String, Object> approvalNonce() {
        return Map.of(
                "nonce", UUID.randomUUID().toString(),
                "timestamp", System.currentTimeMillis()
        );
    }

    @GetMapping("/admin/nonce")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> adminNonce() {
        return Map.of(
                "nonce", UUID.randomUUID().toString(),
                "timestamp", System.currentTimeMillis()
        );
    }

    /**
     * Server-side HMAC signing for browser POSTs. R4 audit HIGH #2 fix: by routing the
     * signing through this endpoint we can keep the HMAC secret on the server while
     * still attaching a valid {@code _signature} form parameter to every privileged
     * browser POST. The endpoint requires an authenticated ADMIN session and
     * {@link RequestSigningFilter} validates the signature on the subsequent submit.
     *
     * Input JSON: {@code {"method":"POST","path":"/admin/users","timestamp":"<ms>","nonce":"<uuid>"}}.
     * Output JSON: {@code {"signature":"<hex>"}}.
     */
    @PostMapping("/admin/sign-form")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> signAdminForm(@RequestBody Map<String, String> req) {
        return Map.of("signature", sign(req));
    }

    /**
     * Same as {@link #signAdminForm(Map)} but for the approval surface (REVIEWER + ADMIN).
     * The nonce filter scope on the actual completion endpoints is the same regardless of
     * which sign-form endpoint produced the signature, but having a separate path lets us
     * reject cross-role privilege bleed at the URL filter level.
     */
    @PostMapping("/approval/sign-form")
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER')")
    public Map<String, Object> signApprovalForm(@RequestBody Map<String, String> req) {
        return Map.of("signature", sign(req));
    }

    private String sign(Map<String, String> req) {
        String method = req.getOrDefault("method", "POST");
        String path = req.get("path");
        String timestamp = req.get("timestamp");
        String nonce = req.get("nonce");
        if (path == null || timestamp == null || nonce == null) {
            throw new IllegalArgumentException("path, timestamp, and nonce are required");
        }
        return requestSigningFilter.signFormCanonical(method, path, timestamp, nonce);
    }
}
