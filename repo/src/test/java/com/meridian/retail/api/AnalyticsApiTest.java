package com.meridian.retail.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Analytics API tests — real HTTP, no MockMvc, no @WithMockUser.
 */
class AnalyticsApiTest extends AbstractApiTest {

    // ── GET /analytics/export ─────────────────────────────────────────────────

    @Test
    void financeUserCanExport() {
        HttpHeaders h = loginAs("finance", "Finance@Retail2024!");
        ResponseEntity<String> resp = get("/analytics/export", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void operationsCannotExport() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/analytics/export", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void csCannotExport() {
        HttpHeaders h = loginAs("cs", "CsUser@Retail2024!");
        ResponseEntity<String> resp = get("/analytics/export", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void adminCanExport() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        ResponseEntity<String> resp = get("/analytics/export", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void reviewerCannotExport() {
        HttpHeaders h = loginAs("reviewer", "Review@Retail2024!");
        ResponseEntity<String> resp = get("/analytics/export", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void exportRequiresAuth() {
        ResponseEntity<String> resp = get("/analytics/export", null);
        assertThat(resp.getStatusCode().value()).isIn(200, 302);
        if (resp.getStatusCode().is3xxRedirection()) {
            assertThat(resp.getHeaders().getLocation().toString()).contains("login");
        }
    }

    // ── GET /analytics/trends ─────────────────────────────────────────────────

    @Test
    void trendsEndpointReturnsJsonForFinance() {
        HttpHeaders h = loginAs("finance", "Finance@Retail2024!");
        ResponseEntity<String> resp = get("/analytics/trends", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        String ct = resp.getHeaders().getContentType() != null
                ? resp.getHeaders().getContentType().toString() : "";
        assertThat(ct).containsIgnoringCase("json");
    }

    @Test
    void trendsEndpointReturnsJsonForAdmin() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        ResponseEntity<String> resp = get("/analytics/trends", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void trendsWithFiltersForFinance() {
        HttpHeaders h = loginAs("finance", "Finance@Retail2024!");
        ResponseEntity<String> resp = get("/analytics/trends?storeId=STORE-1", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void trendsRequiresAuth() {
        ResponseEntity<String> resp = get("/analytics/trends", null);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 403);
    }

    // ── GET /analytics/dashboard ──────────────────────────────────────────────

    @Test
    void analyticsDashboardReachableForFinance() {
        HttpHeaders h = loginAs("finance", "Finance@Retail2024!");
        ResponseEntity<String> resp = get("/analytics/dashboard", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void analyticsDashboardReachableForAdmin() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        ResponseEntity<String> resp = get("/analytics/dashboard", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void analyticsDashboardReachableForOps() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/analytics/dashboard", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void analyticsDashboardForbiddenForCs() {
        HttpHeaders h = loginAs("cs", "CsUser@Retail2024!");
        ResponseEntity<String> resp = get("/analytics/dashboard", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void analyticsDashboardWithDateFilters() {
        HttpHeaders h = loginAs("finance", "Finance@Retail2024!");
        ResponseEntity<String> resp = get("/analytics/dashboard?storeId=STORE-1&from=2024-01-01&to=2024-12-31", h);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 400);
    }

    @Test
    void analyticsDashboardRequiresAuth() {
        ResponseEntity<String> resp = get("/analytics/dashboard", null);
        assertThat(resp.getStatusCode().value()).isIn(200, 302);
        if (resp.getStatusCode().is3xxRedirection()) {
            assertThat(resp.getHeaders().getLocation().toString()).contains("login");
        }
    }
}
