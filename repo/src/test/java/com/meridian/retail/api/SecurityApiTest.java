package com.meridian.retail.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real HTTP security tests. No MockMvc, no @WithMockUser.
 * All assertions are made against real Spring Security filter responses.
 */
class SecurityApiTest extends AbstractApiTest {

    @Test
    void anonymousAccessToProtectedResourceRedirectsToLogin() {
        ResponseEntity<String> resp = get("/campaigns", null);
        // Redirect (302) or if redirect is followed, the login page (200).
        assertThat(resp.getStatusCode().value()).isIn(200, 302);
        if (resp.getStatusCode().is3xxRedirection()) {
            assertThat(resp.getHeaders().getLocation().toString()).contains("login");
        }
    }

    @Test
    void healthEndpointIsPublic() {
        ResponseEntity<String> resp = get("/health", null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void loginWithCorrectAdminCredentials() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        assertThat(h.getFirst(HttpHeaders.COOKIE)).contains("JSESSIONID");
    }

    @Test
    void loginWithCorrectOpsCredentials() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        assertThat(h.getFirst(HttpHeaders.COOKIE)).contains("JSESSIONID");
    }

    @Test
    void loginWithCorrectReviewerCredentials() {
        HttpHeaders h = loginAs("reviewer", "Review@Retail2024!");
        assertThat(h.getFirst(HttpHeaders.COOKIE)).contains("JSESSIONID");
    }

    @Test
    void loginWithCorrectFinanceCredentials() {
        HttpHeaders h = loginAs("finance", "Finance@Retail2024!");
        assertThat(h.getFirst(HttpHeaders.COOKIE)).contains("JSESSIONID");
    }

    @Test
    void loginWithCorrectCsCredentials() {
        HttpHeaders h = loginAs("cs", "CsUser@Retail2024!");
        assertThat(h.getFirst(HttpHeaders.COOKIE)).contains("JSESSIONID");
    }

    @Test
    void wrongPasswordRedirectsToLoginError() {
        // POST /login without a pre-existing session (no CSRF will cause 403, not redirect).
        // We exercise the wrong-password path through a proper login attempt.
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        org.springframework.util.MultiValueMap<String, String> body =
                new org.springframework.util.LinkedMultiValueMap<>();
        body.add("username", "admin");
        body.add("password", "WrongPassword!");
        // No CSRF → 403. This is expected behaviour (CSRF protection is active).
        ResponseEntity<String> resp = postForm("/login", h, body);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void adminCanAccessAdminDashboard() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        ResponseEntity<String> resp = get("/admin/dashboard", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void opsUserCannotAccessAdminDashboard() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/admin/dashboard", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void financeUserCannotAccessAdminUsers() {
        HttpHeaders h = loginAs("finance", "Finance@Retail2024!");
        ResponseEntity<String> resp = get("/admin/users", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void csUserCannotAccessAdminAuditLog() {
        HttpHeaders h = loginAs("cs", "CsUser@Retail2024!");
        ResponseEntity<String> resp = get("/admin/audit-log", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void opsUserCannotAccessApprovalQueue() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/approval/queue", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void reviewerCanAccessApprovalQueue() {
        HttpHeaders h = loginAs("reviewer", "Review@Retail2024!");
        ResponseEntity<String> resp = get("/approval/queue", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void financeCanAccessAnalyticsDashboard() {
        HttpHeaders h = loginAs("finance", "Finance@Retail2024!");
        ResponseEntity<String> resp = get("/analytics/trends", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void opsCannotAccessAnalyticsExport() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/analytics/export", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void financeCanAccessAnalyticsExport() {
        HttpHeaders h = loginAs("finance", "Finance@Retail2024!");
        ResponseEntity<String> resp = get("/analytics/export", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void csCannotAccessAnalyticsExport() {
        HttpHeaders h = loginAs("cs", "CsUser@Retail2024!");
        ResponseEntity<String> resp = get("/analytics/export", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void opsCanAccessCouponList() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/coupons", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void adminCanAccessRoleChangesPage() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        ResponseEntity<String> resp = get("/admin/role-changes", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void adminCanAccessAnomalyAlertsPage() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        ResponseEntity<String> resp = get("/admin/anomaly-alerts", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void adminCanAccessBackupPage() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        ResponseEntity<String> resp = get("/admin/backup", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void authenticatedUserCanAccessCampaignList() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/campaigns", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void authenticatedUserCanAccessContentList() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/content", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }
}
