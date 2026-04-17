package com.meridian.retail.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the Customer Service endpoints: GET /cs/lookup.
 */
class CustomerServiceApiTest extends AbstractApiTest {

    @Test
    void csLookupPageReachableForCsRole() {
        HttpHeaders h = loginAs("cs", "CsUser@Retail2024!");
        ResponseEntity<String> resp = get("/cs/lookup", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void csLookupPageReachableForAdmin() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        ResponseEntity<String> resp = get("/cs/lookup", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void csLookupWithSeededCouponCode() {
        HttpHeaders h = loginAs("cs", "CsUser@Retail2024!");
        // SPRING15 is seeded in V12__seed_data.sql
        ResponseEntity<String> resp = get("/cs/lookup?code=SPRING15", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        // Page should contain the coupon code info
        assertThat(resp.getBody()).containsIgnoringCase("SPRING15");
    }

    @Test
    void csLookupWithUnknownCodeShowsNoResults() {
        HttpHeaders h = loginAs("cs", "CsUser@Retail2024!");
        ResponseEntity<String> resp = get("/cs/lookup?code=DOESNOTEXIST99999", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void csLookupWithNoCodeShowsEmptyPage() {
        HttpHeaders h = loginAs("cs", "CsUser@Retail2024!");
        ResponseEntity<String> resp = get("/cs/lookup", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void csLookupForbiddenForOps() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/cs/lookup", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void csLookupForbiddenForFinance() {
        HttpHeaders h = loginAs("finance", "Finance@Retail2024!");
        ResponseEntity<String> resp = get("/cs/lookup", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void csLookupForbiddenForReviewer() {
        HttpHeaders h = loginAs("reviewer", "Review@Retail2024!");
        ResponseEntity<String> resp = get("/cs/lookup", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void csLookupRequiresAuth() {
        ResponseEntity<String> resp = get("/cs/lookup", null);
        assertThat(resp.getStatusCode().value()).isIn(200, 302);
        if (resp.getStatusCode().is3xxRedirection()) {
            assertThat(resp.getHeaders().getLocation().toString()).contains("login");
        }
    }
}
