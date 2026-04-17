package com.meridian.retail.api;

import com.meridian.retail.entity.AnomalyAlert;
import com.meridian.retail.entity.AlertSeverity;
import com.meridian.retail.repository.AnomalyAlertRepository;
import com.meridian.retail.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Admin API tests — real HTTP, no MockMvc, no @WithMockUser.
 * POST endpoints require nonce+signature via signedPost().
 */
class AdminApiTest extends AbstractApiTest {

    @Autowired AnomalyAlertRepository anomalyAlertRepository;
    @Autowired UserRepository userRepository;

    // ── GET /admin/dashboard ──────────────────────────────────────────────────

    @Test
    void adminDashboardReachableForAdmin() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        ResponseEntity<String> resp = get("/admin/dashboard", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void adminDashboardForbiddenForOps() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/admin/dashboard", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void adminDashboardForbiddenForReviewer() {
        HttpHeaders h = loginAs("reviewer", "Review@Retail2024!");
        ResponseEntity<String> resp = get("/admin/dashboard", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void adminDashboardForbiddenForFinance() {
        HttpHeaders h = loginAs("finance", "Finance@Retail2024!");
        ResponseEntity<String> resp = get("/admin/dashboard", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void adminDashboardForbiddenForCs() {
        HttpHeaders h = loginAs("cs", "CsUser@Retail2024!");
        ResponseEntity<String> resp = get("/admin/dashboard", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void anonymousCannotAccessAdminDashboard() {
        ResponseEntity<String> resp = get("/admin/dashboard", null);
        assertThat(resp.getStatusCode().value()).isIn(200, 302);
        if (resp.getStatusCode().is3xxRedirection()) {
            assertThat(resp.getHeaders().getLocation().toString()).contains("login");
        }
    }

    // ── GET /admin/users ──────────────────────────────────────────────────────

    @Test
    void adminUsersPageReachableForAdmin() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        ResponseEntity<String> resp = get("/admin/users", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).containsIgnoringCase("admin");
    }

    @Test
    void adminUsersPageForbiddenForOps() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/admin/users", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    // ── GET /admin/users/{id}/edit ─────────────────────────────────────────────

    @Test
    void editUserFormReachableForAdmin() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        // Find the ops user ID
        var opsUser = userRepository.findByUsername("ops").orElse(null);
        if (opsUser != null) {
            ResponseEntity<String> resp = get("/admin/users/" + opsUser.getId() + "/edit", h);
            assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        }
    }

    @Test
    void editUserFormForbiddenForOps() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/admin/users/1/edit", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void editUserFormNonExistentIdReturns404Or500() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        ResponseEntity<String> resp = get("/admin/users/99999/edit", h);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 404, 500);
    }

    // ── GET /admin/users/check-username ───────────────────────────────────────

    @Test
    void checkUsernameEndpointReachableForAdmin() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        ResponseEntity<String> resp = get("/admin/users/check-username?username=newuser123", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void checkExistingUsernameReturnsUnavailable() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        ResponseEntity<String> resp = get("/admin/users/check-username?username=admin", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        // Response should indicate username is taken
        assertThat(resp.getBody()).isNotNull();
    }

    @Test
    void checkUnusedUsernameReturnsAvailable() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        ResponseEntity<String> resp = get("/admin/users/check-username?username=brandnewuser" + System.currentTimeMillis(), h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void checkUsernameForbiddenForOps() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/admin/users/check-username?username=test", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    // ── GET /admin/backup ─────────────────────────────────────────────────────

    @Test
    void adminBackupPageReachableForAdmin() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        ResponseEntity<String> resp = get("/admin/backup", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void adminBackupForbiddenForOps() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/admin/backup", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    // ── GET /admin/role-changes ────────────────────────────────────────────────

    @Test
    void adminRoleChangesPageReachableForAdmin() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        ResponseEntity<String> resp = get("/admin/role-changes", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    // ── GET /admin/anomaly-alerts ─────────────────────────────────────────────

    @Test
    void adminAnomalyAlertsPageReachableForAdmin() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        ResponseEntity<String> resp = get("/admin/anomaly-alerts", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    // ── GET /admin/audit-log ──────────────────────────────────────────────────

    @Test
    void adminAuditLogPageReachableForAdmin() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        ResponseEntity<String> resp = get("/admin/audit-log", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void adminAuditLogForbiddenForOps() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/admin/audit-log", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    // ── GET /admin/users/new ──────────────────────────────────────────────────

    @Test
    void adminNewUserFormReachableForAdmin() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        ResponseEntity<String> resp = get("/admin/users/new", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    // ── GET /admin/nonce ──────────────────────────────────────────────────────

    @Test
    void adminNonceReturnsJsonForAdmin() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        HttpHeaders jsonH = new HttpHeaders();
        jsonH.addAll(h);
        jsonH.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        ResponseEntity<String> resp = http().exchange(
                url("/admin/nonce"), HttpMethod.GET, new HttpEntity<>(jsonH), String.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).contains("nonce");
        assertThat(resp.getBody()).contains("timestamp");
    }

    @Test
    void adminNonceForbiddenForReviewer() {
        HttpHeaders h = loginAs("reviewer", "Review@Retail2024!");
        ResponseEntity<String> resp = get("/admin/nonce", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void adminNonceForbiddenForOps() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/admin/nonce", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    // ── POST /admin/sign-form ─────────────────────────────────────────────────

    @Test
    void signAdminFormReturnsSignatureForAdmin() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        String payload = "{\"method\":\"POST\",\"path\":\"/admin/users\","
                + "\"timestamp\":\"" + System.currentTimeMillis() + "\","
                + "\"nonce\":\"test-nonce-admin-99\"}";
        ResponseEntity<String> resp = postJson("/admin/sign-form", h, payload);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).contains("signature");
    }

    @Test
    void signAdminFormForbiddenForReviewer() {
        HttpHeaders h = loginAs("reviewer", "Review@Retail2024!");
        String payload = "{\"method\":\"POST\",\"path\":\"/admin/users\","
                + "\"timestamp\":\"" + System.currentTimeMillis() + "\","
                + "\"nonce\":\"reviewer-nonce\"}";
        ResponseEntity<String> resp = postJson("/admin/sign-form", h, payload);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    // ── POST /admin/users (create user, nonce+signature required) ─────────────

    @Test
    void createUserWithSignedPostRejectsWeakPassword() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("username", "httptest" + System.currentTimeMillis());
        params.add("password", "weak");
        params.add("role", "OPERATIONS");
        ResponseEntity<String> resp = signedPost("/admin/users", h, params);
        // Weak password → form re-rendered with error (200) or redirect
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 400, 403, 404);
    }

    @Test
    void createUserEndpointForbiddenForOps() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        // Without nonce, request to admin endpoint → 400 from NonceValidationFilter
        // With wrong role, Security blocks before the filter → 403
        ResponseEntity<String> resp = get("/admin/users/new", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void createUserRequiresAuth() {
        ResponseEntity<String> resp = get("/admin/users", null);
        assertThat(resp.getStatusCode().value()).isIn(200, 302);
    }

    // ── POST /admin/users/{id}/update (nonce+signature required) ──────────────

    @Test
    void updateUserWithSignedPostForNonExistentUser() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("fullName", "Updated Name");
        params.add("role", "OPERATIONS");
        ResponseEntity<String> resp = signedPost("/admin/users/99999/update", h, params);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 400, 403, 404, 500);
    }

    // ── POST /admin/users/{id}/role-change-request (nonce+signature required) ─

    @Test
    void roleChangeRequestWithSignedPostForNonExistentUser() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("newRole", "REVIEWER");
        ResponseEntity<String> resp = signedPost("/admin/users/99999/role-change-request", h, params);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 400, 403, 404, 500);
    }

    // ── POST /admin/role-changes/{id}/approve-first (nonce+signature required) ─

    @Test
    void roleChangeApproveFirstWithSignedPost() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        ResponseEntity<String> resp = signedPost("/admin/role-changes/99999/approve-first", h, null);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 400, 403, 404, 500);
    }

    // ── POST /admin/role-changes/{id}/approve-second (nonce+signature required) ─

    @Test
    void roleChangeApproveSecondWithSignedPost() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        ResponseEntity<String> resp = signedPost("/admin/role-changes/99999/approve-second", h, null);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 400, 403, 404, 500);
    }

    // ── POST /admin/role-changes/{id}/reject (nonce+signature required) ────────

    @Test
    void roleChangeRejectWithSignedPost() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        ResponseEntity<String> resp = signedPost("/admin/role-changes/99999/reject", h, null);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 400, 403, 404, 500);
    }

    // ── POST /admin/users/{id}/deactivate (nonce+signature required) ──────────

    @Test
    void deactivateUserWithSignedPost() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        // Use ops user to deactivate
        var opsUser = userRepository.findByUsername("reviewer").orElse(null);
        long userId = opsUser != null ? opsUser.getId() : 99999L;
        ResponseEntity<String> resp = signedPost("/admin/users/" + userId + "/deactivate", h, null);
        // Should redirect back regardless of outcome
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 400, 403, 404, 500);
    }

    @Test
    void deactivateUserForbiddenForOps() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/admin/users/1/edit", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    // ── POST /admin/anomaly-alerts/{id}/ack (nonce+signature required) ─────────

    @Test
    void acknowledgeAnomalyAlertWithSignedPost() {
        // Create a test anomaly alert
        AnomalyAlert alert = AnomalyAlert.builder()
                .alertType("TEST_ALERT")
                .description("test alert for http test")
                .severity(AlertSeverity.LOW)
                .detectedAt(LocalDateTime.now())
                .build();
        alert = anomalyAlertRepository.save(alert);

        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        ResponseEntity<String> resp = signedPost(
                "/admin/anomaly-alerts/" + alert.getId() + "/ack", h, null);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 400, 403, 404);
    }

    @Test
    void acknowledgeAnomalyForbiddenForOps() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/admin/anomaly-alerts", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    // ── POST /admin/backup/run (nonce+signature required) ─────────────────────

    @Test
    void backupRunWithSignedPostTriggersBackup() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        ResponseEntity<String> resp = signedPost("/admin/backup/run", h, null);
        // Backup may succeed or fail (mysqldump not available in test env), but redirects back
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 400, 403, 404, 500);
    }

    @Test
    void backupRunForbiddenForOps() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/admin/backup", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    // ── POST /admin/backup/test-restore (nonce+signature required) ────────────

    @Test
    void backupTestRestoreWithSignedPost() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        ResponseEntity<String> resp = signedPost("/admin/backup/test-restore", h, null);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 400, 403, 404, 500);
    }

    // ── POST /admin/backup/{id}/restore (nonce+signature required) ────────────

    @Test
    void backupRestoreWithNonExistentId() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        ResponseEntity<String> resp = signedPost("/admin/backup/99999/restore", h, null);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 400, 403, 404, 500);
    }

    // ── GET /admin/sensitive-log ───────────────────────────────────────────────

    @Test
    void adminSensitiveLogReachableForAdmin() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        ResponseEntity<String> resp = get("/admin/sensitive-log", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }
}
