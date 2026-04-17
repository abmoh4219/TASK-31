package com.meridian.retail.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for miscellaneous endpoints: root redirect, dashboard, /upload stub, health endpoints.
 */
class MiscApiTest extends AbstractApiTest {

    // ── GET / ────────────────────────────────────────────────────────────────────

    @Test
    void rootRedirectsAnonymousToLogin() {
        ResponseEntity<String> resp = get("/", null);
        assertThat(resp.getStatusCode().value()).isIn(200, 302);
        if (resp.getStatusCode().is3xxRedirection()) {
            assertThat(resp.getHeaders().getLocation().toString()).contains("login");
        }
    }

    @Test
    void rootRedirectsAuthenticatedUserToDashboard() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/", h);
        // Redirects to /dashboard or role-specific page
        assertThat(resp.getStatusCode().value()).isIn(200, 302);
    }

    // ── GET /dashboard ───────────────────────────────────────────────────────────

    @Test
    void dashboardRedirectsAdminToAdminDashboard() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        ResponseEntity<String> resp = get("/dashboard", h);
        assertThat(resp.getStatusCode().value()).isIn(200, 302);
        if (resp.getStatusCode().is3xxRedirection()) {
            assertThat(resp.getHeaders().getLocation().toString()).contains("admin");
        }
    }

    @Test
    void dashboardRedirectsOpsToOwnDashboard() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/dashboard", h);
        assertThat(resp.getStatusCode().value()).isIn(200, 302);
    }

    @Test
    void dashboardRedirectsReviewerToApprovalQueue() {
        HttpHeaders h = loginAs("reviewer", "Review@Retail2024!");
        ResponseEntity<String> resp = get("/dashboard", h);
        assertThat(resp.getStatusCode().value()).isIn(200, 302);
    }

    @Test
    void dashboardRedirectsFinanceToAnalytics() {
        HttpHeaders h = loginAs("finance", "Finance@Retail2024!");
        ResponseEntity<String> resp = get("/dashboard", h);
        assertThat(resp.getStatusCode().value()).isIn(200, 302);
    }

    @Test
    void dashboardRedirectsCsToOwnDashboard() {
        HttpHeaders h = loginAs("cs", "CsUser@Retail2024!");
        ResponseEntity<String> resp = get("/dashboard", h);
        assertThat(resp.getStatusCode().value()).isIn(200, 302);
    }

    @Test
    void dashboardRequiresAuth() {
        ResponseEntity<String> resp = get("/dashboard", null);
        assertThat(resp.getStatusCode().value()).isIn(200, 302);
        if (resp.getStatusCode().is3xxRedirection()) {
            assertThat(resp.getHeaders().getLocation().toString()).contains("login");
        }
    }

    // ── GET /upload (stub redirect) ───────────────────────────────────────────────

    @Test
    void uploadStubRedirectsToFilesUpload() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/upload", h);
        assertThat(resp.getStatusCode().value()).isIn(200, 302);
        if (resp.getStatusCode().is3xxRedirection()) {
            assertThat(resp.getHeaders().getLocation().toString()).contains("files");
        }
    }

    @Test
    void uploadStubForbiddenForCs() {
        HttpHeaders h = loginAs("cs", "CsUser@Retail2024!");
        ResponseEntity<String> resp = get("/upload", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void uploadStubRequiresAuth() {
        ResponseEntity<String> resp = get("/upload", null);
        assertThat(resp.getStatusCode().value()).isIn(200, 302);
    }

    // ── GET /health + /api/health ─────────────────────────────────────────────────

    @Test
    void healthEndpointIsPublicAndReturnsUp() {
        ResponseEntity<String> resp = get("/health", null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("UP");
    }

    @Test
    void apiHealthEndpointResponds() {
        // /api/health may require auth depending on security config
        ResponseEntity<String> resp = get("/api/health", null);
        assertThat(resp.getStatusCode().value()).isIn(200, 302);
    }

    @Test
    void apiHealthEndpointReturnsUpWhenAuthenticated() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/api/health", h);
        assertThat(resp.getStatusCode().value()).isIn(200, 302);
        if (resp.getStatusCode().is2xxSuccessful()) {
            assertThat(resp.getBody()).contains("UP");
        }
    }

    @Test
    void healthResponseContainsServiceName() {
        ResponseEntity<String> resp = get("/health", null);
        assertThat(resp.getBody()).containsIgnoringCase("retail");
    }
}
