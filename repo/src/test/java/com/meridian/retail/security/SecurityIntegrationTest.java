package com.meridian.retail.security;

import com.meridian.retail.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Security integration tests — exercise the actual filter chain against a real DB.
 *
 *   - GET /campaigns anonymous   -> 302 redirect to /login
 *   - POST /login wrong password -> 302 to /login?error
 *   - POST /campaigns no CSRF    -> 403
 *   - OPS user GET /admin/...    -> 403
 */
@AutoConfigureMockMvc
class SecurityIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    void anonymousAccessRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/campaigns"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void wrongPasswordRedirectsWithError() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "admin")
                        .param("password", "wrong-password")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    @WithMockUser(username = "ops", roles = {"OPERATIONS"})
    void opsUserCannotAccessAdmin() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "finance", roles = {"FINANCE"})
    void financeUserCannotAccessAdminUsers() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    void postWithoutCsrfRejected() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "admin")
                        .param("password", "Admin@Retail2024!"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void adminCanReachAdminDashboard() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "cs", roles = {"CUSTOMER_SERVICE"})
    void csUserCannotAccessAdminAuditLog() throws Exception {
        mockMvc.perform(get("/admin/audit-log"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "ops", roles = {"OPERATIONS"})
    void opsUserCannotReachApprovalQueue() throws Exception {
        mockMvc.perform(get("/approval/queue"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "finance", roles = {"FINANCE"})
    void financeUserCannotReachContentMerge() throws Exception {
        mockMvc.perform(post("/content/merge")
                        .param("masterId", "1")
                        .param("duplicateIds", "2")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "ops", roles = {"OPERATIONS"})
    void opsUserCannotExportAnalytics() throws Exception {
        mockMvc.perform(get("/analytics/export"))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthEndpointPublic() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk());
    }

    /**
     * R4 HIGH #2 — admin form POSTs WITH _nonce/_timestamp/_signature form fields (the
     * browser path enriched by {@code static/js/nonce-form.js}) must pass through both
     * NonceValidationFilter and RequestSigningFilter and reach the controller layer.
     * POSTs WITHOUT those fields must be rejected — see {@link AdminFilterBypassTest}.
     */
    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void adminFormPostWithSignedNonceFieldsPassesAllFilters() throws Exception {
        String nonce = java.util.UUID.randomUUID().toString();
        String timestamp = String.valueOf(System.currentTimeMillis());
        String path = "/admin/does-not-exist";
        // Compute the form-mode signature with the same secret as application.yml.
        String signature = signFormCanonical("POST", path, timestamp, nonce);

        mockMvc.perform(post(path)
                        .contentType("application/x-www-form-urlencoded")
                        .param("foo", "bar")
                        .param("_nonce", nonce)
                        .param("_timestamp", timestamp)
                        .param("_signature", signature)
                        .with(csrf()))
                .andExpect(status().isNotFound()); // controller path doesn't exist, but filters passed
    }

    /** Negative path through the integration stack: admin form POST without nonce -> 400. */
    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void adminFormPostWithoutNonceRejectedByFilter() throws Exception {
        mockMvc.perform(post("/admin/does-not-exist")
                        .contentType("application/x-www-form-urlencoded")
                        .param("foo", "bar")
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    /** Negative: admin form POST with nonce but without signature -> 403 from signing filter. */
    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void adminFormPostWithoutSignatureRejectedBySigningFilter() throws Exception {
        mockMvc.perform(post("/admin/does-not-exist")
                        .contentType("application/x-www-form-urlencoded")
                        .param("foo", "bar")
                        .param("_nonce", java.util.UUID.randomUUID().toString())
                        .param("_timestamp", String.valueOf(System.currentTimeMillis()))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    private String signFormCanonical(String method, String path, String timestamp, String nonce)
            throws Exception {
        String secret = "retail-campaign-hmac-signing-key!!";
        String canonical = method + "\n" + path + "\n" + timestamp + "\n" + nonce;
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(
                secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] sig = mac.doFinal(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(sig);
    }

    /** HIGH #6: coupon list must be reachable for authenticated users. */
    @Test
    @WithMockUser(username = "ops", roles = {"OPERATIONS"})
    void couponListReachableForOps() throws Exception {
        mockMvc.perform(get("/coupons"))
                .andExpect(status().isOk());
    }

    /** HIGH #6: coupon new form must be reachable for admin. */
    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void couponNewFormReachableForAdmin() throws Exception {
        mockMvc.perform(get("/coupons/new"))
                .andExpect(status().isOk());
    }

    /** HIGH #7: role-changes page reachable for admin. */
    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void roleChangesPageReachableForAdmin() throws Exception {
        mockMvc.perform(get("/admin/role-changes"))
                .andExpect(status().isOk());
    }

    /** HIGH #9: trends endpoint returns JSON for analytics users. */
    @Test
    @WithMockUser(username = "finance", roles = {"FINANCE"})
    void analyticsTrendsReachable() throws Exception {
        mockMvc.perform(get("/analytics/trends"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }
}
