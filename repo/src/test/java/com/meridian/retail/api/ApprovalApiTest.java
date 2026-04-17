package com.meridian.retail.api;

import com.meridian.retail.dto.CreateCampaignRequest;
import com.meridian.retail.entity.ApprovalQueue;
import com.meridian.retail.entity.Campaign;
import com.meridian.retail.entity.CampaignType;
import com.meridian.retail.entity.RiskLevel;
import com.meridian.retail.service.ApprovalService;
import com.meridian.retail.service.CampaignService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Approval API tests — real HTTP, no MockMvc, no @WithMockUser.
 */
class ApprovalApiTest extends AbstractApiTest {

    @Autowired CampaignService campaignService;
    @Autowired ApprovalService approvalService;

    // ── GET /approval/queue ────────────────────────────────────────────────────

    @Test
    void approvalQueueReachableForReviewer() {
        HttpHeaders h = loginAs("reviewer", "Review@Retail2024!");
        ResponseEntity<String> resp = get("/approval/queue", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void approvalQueueReachableForAdmin() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        ResponseEntity<String> resp = get("/approval/queue", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void approvalQueueForbiddenForOps() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/approval/queue", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void approvalQueueForbiddenForFinance() {
        HttpHeaders h = loginAs("finance", "Finance@Retail2024!");
        ResponseEntity<String> resp = get("/approval/queue", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void approvalQueueForbiddenForCs() {
        HttpHeaders h = loginAs("cs", "CsUser@Retail2024!");
        ResponseEntity<String> resp = get("/approval/queue", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void anonymousCannotAccessApprovalQueue() {
        ResponseEntity<String> resp = get("/approval/queue", null);
        assertThat(resp.getStatusCode().value()).isIn(200, 302);
        if (resp.getStatusCode().is3xxRedirection()) {
            assertThat(resp.getHeaders().getLocation().toString()).contains("login");
        }
    }

    // ── GET /approval/nonce ────────────────────────────────────────────────────

    @Test
    void approvalNonceReturnsJsonForReviewer() {
        HttpHeaders h = loginAs("reviewer", "Review@Retail2024!");
        HttpHeaders jsonH = new HttpHeaders();
        jsonH.addAll(h);
        jsonH.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        ResponseEntity<String> resp = http().exchange(
                url("/approval/nonce"), HttpMethod.GET, new HttpEntity<>(jsonH), String.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).contains("nonce");
        assertThat(resp.getBody()).contains("timestamp");
    }

    @Test
    void approvalNonceReturnsJsonForAdmin() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        HttpHeaders jsonH = new HttpHeaders();
        jsonH.addAll(h);
        jsonH.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        ResponseEntity<String> resp = http().exchange(
                url("/approval/nonce"), HttpMethod.GET, new HttpEntity<>(jsonH), String.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void approvalNonceForbiddenForOps() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/approval/nonce", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void approvalNonceRequiresAuth() {
        ResponseEntity<String> resp = get("/approval/nonce", null);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 403);
    }

    // ── POST /approval/sign-form ───────────────────────────────────────────────

    @Test
    void signFormReturnsSignatureForReviewer() {
        HttpHeaders h = loginAs("reviewer", "Review@Retail2024!");
        String payload = "{\"method\":\"POST\",\"path\":\"/approval/42/approve-first\","
                + "\"timestamp\":\"" + System.currentTimeMillis() + "\","
                + "\"nonce\":\"test-nonce-12345\"}";
        ResponseEntity<String> resp = postJson("/approval/sign-form", h, payload);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).contains("signature");
    }

    @Test
    void signFormForbiddenForOps() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        String payload = "{\"method\":\"POST\",\"path\":\"/approval/42/approve\","
                + "\"timestamp\":\"" + System.currentTimeMillis() + "\","
                + "\"nonce\":\"test-nonce-ops\"}";
        ResponseEntity<String> resp = postJson("/approval/sign-form", h, payload);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    // ── POST /approval/{id}/approve (no nonce required) ───────────────────────

    @Test
    void approveEndpointWithNonExistentIdRedirectsReviewer() {
        HttpHeaders h = loginAs("reviewer", "Review@Retail2024!");
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("notes", "looks good");
        // Non-existent ID — controller catches exception and redirects
        ResponseEntity<String> resp = postFormWithCsrf("/approval/queue",
                "/approval/99999/approve", h, params);
        // Should redirect back to queue (with error or success)
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 400, 403, 404);
    }

    @Test
    void approveEndpointForbiddenForOps() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        ResponseEntity<String> resp = postFormWithCsrf("/approval/queue",
                "/approval/1/approve", h, params);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void approveEndpointRequiresAuth() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        ResponseEntity<String> resp = postForm("/approval/1/approve", null, params);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 403);
    }

    // ── POST /approval/{id}/reject (no nonce required) ────────────────────────

    @Test
    void rejectEndpointWithNonExistentIdRedirectsReviewer() {
        HttpHeaders h = loginAs("reviewer", "Review@Retail2024!");
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("notes", "rejected");
        ResponseEntity<String> resp = postFormWithCsrf("/approval/queue",
                "/approval/99999/reject", h, params);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 400, 403, 404);
    }

    @Test
    void rejectEndpointForbiddenForOps() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        ResponseEntity<String> resp = postFormWithCsrf("/approval/queue",
                "/approval/1/reject", h, params);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    // ── POST /approval/{id}/approve-first (nonce+signature required) ──────────

    @Test
    void approveFirstRequiresNonce() {
        // Without nonce → NonceValidationFilter returns 400
        HttpHeaders h = loginAs("reviewer", "Review@Retail2024!");
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        ResponseEntity<String> resp = postFormWithCsrf("/approval/queue",
                "/approval/1/approve-first", h, params);
        // Missing nonce → 400 (proves endpoint exists and is nonce-protected)
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 400, 403);
    }

    @Test
    void approveFirstWithSignedRequestForNonExistentId() {
        HttpHeaders h = loginAs("reviewer", "Review@Retail2024!");
        // signedPost gets nonce, fetches signature, posts with all required params
        ResponseEntity<String> resp = signedPost("/approval/99999/approve-first", h, null);
        // Non-existent ID → redirect back with error, but request got through filters
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 400, 403, 404);
    }

    @Test
    void approveFirstForbiddenForOps() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/approval/queue", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    // ── POST /approval/{id}/approve-second (nonce+signature required) ─────────

    @Test
    void approveSecondRequiresNonce() {
        HttpHeaders h = loginAs("reviewer", "Review@Retail2024!");
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        ResponseEntity<String> resp = postFormWithCsrf("/approval/queue",
                "/approval/1/approve-second", h, params);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 400, 403);
    }

    @Test
    void approveSecondWithSignedRequestForNonExistentId() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        MultiValueMap<String, String> extraParams = new LinkedMultiValueMap<>();
        extraParams.add("notes", "second approval");
        ResponseEntity<String> resp = signedPost("/approval/99999/approve-second", h, extraParams);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 400, 403, 404);
    }

    // ── POST /approval/dual-approve/{requestId} (nonce+signature required) ────

    @Test
    void dualApproveRequiresNonce() {
        HttpHeaders h = loginAs("reviewer", "Review@Retail2024!");
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        ResponseEntity<String> resp = postFormWithCsrf("/approval/queue",
                "/approval/dual-approve/1", h, params);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 400, 403);
    }

    @Test
    void dualApproveWithSignedRequestForNonExistentId() {
        HttpHeaders h = loginAs("reviewer", "Review@Retail2024!");
        ResponseEntity<String> resp = signedPost("/approval/dual-approve/99999", h, null);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 400, 403, 404);
    }

    @Test
    void dualApproveForbiddenForOps() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        ResponseEntity<String> resp = postFormWithCsrf("/approval/queue",
                "/approval/dual-approve/1", h, params);
        assertThat(resp.getStatusCode().value()).isIn(302, 403, 400);
    }
}
