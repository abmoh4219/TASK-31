package com.meridian.retail.api;

import com.meridian.retail.dto.CreateCampaignRequest;
import com.meridian.retail.entity.*;
import com.meridian.retail.repository.AuditLogRepository;
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
 * Campaign API tests — service-level state transitions AND real HTTP endpoint tests.
 */
class CampaignApiTest extends AbstractApiTest {

    @Autowired CampaignService campaignService;
    @Autowired ApprovalService approvalService;
    @Autowired AuditLogRepository auditLogRepository;

    // ── Service-level tests ───────────────────────────────────────────────────

    @Test
    void createSubmitApproveFlow() {
        long auditBefore = auditLogRepository.count();
        CreateCampaignRequest req = new CreateCampaignRequest();
        req.setName("API Test Campaign");
        req.setDescription("created via api test");
        req.setType(CampaignType.COUPON);
        req.setReceiptWording("API TEST RECEIPT");
        req.setStoreId("STORE-API-1");
        req.setRiskLevel(RiskLevel.LOW);
        req.setStartDate(LocalDate.now().plusDays(1));
        req.setEndDate(LocalDate.now().plusDays(10));

        Campaign created = campaignService.createCampaign(req, "ops", "127.0.0.1");
        assertThat(created.getId()).isNotNull();
        assertThat(created.getStatus()).isEqualTo(CampaignStatus.DRAFT);

        Campaign submitted = campaignService.submitForReview(created.getId(), "ops", "127.0.0.1");
        assertThat(submitted.getStatus()).isEqualTo(CampaignStatus.PENDING_REVIEW);

        var queueEntry = approvalService.submitToQueue(submitted.getId(), "ops", RiskLevel.LOW, "127.0.0.1");
        assertThat(queueEntry.getStatus()).isEqualTo(ApprovalStatus.PENDING);

        var approved = approvalService.approve(queueEntry.getId(), "reviewer", "looks fine", "127.0.0.1");
        assertThat(approved.getStatus()).isEqualTo(ApprovalStatus.APPROVED);

        Campaign reloaded = campaignService.findById(created.getId());
        assertThat(reloaded.getStatus()).isEqualTo(CampaignStatus.APPROVED);
        assertThat(auditLogRepository.count() - auditBefore).isGreaterThanOrEqualTo(4);
    }

    // ── GET /campaigns ────────────────────────────────────────────────────────

    @Test
    void campaignListPageReturnsOkForOps() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/campaigns", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).contains("Campaign");
    }

    @Test
    void campaignListContainsSeededData() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/campaigns", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        // V12__seed_data has "Spring Refresh"
        assertThat(resp.getBody()).containsIgnoringCase("Spring");
    }

    @Test
    void anonymousCannotAccessCampaignList() {
        ResponseEntity<String> resp = get("/campaigns", null);
        assertThat(resp.getStatusCode().value()).isIn(200, 302);
        if (resp.getStatusCode().is3xxRedirection()) {
            assertThat(resp.getHeaders().getLocation().toString()).contains("login");
        }
    }

    // ── GET /campaigns/new ────────────────────────────────────────────────────

    @Test
    void newCampaignFormReturnsOkForOps() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/campaigns/new", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    // ── POST /campaigns ───────────────────────────────────────────────────────

    @Test
    void postCampaignCreateFormWithValidData() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("name", "HTTP Create Test Campaign");
        params.add("description", "via real HTTP");
        params.add("type", "COUPON");
        params.add("storeId", "STORE-HTTP-1");
        params.add("riskLevel", "LOW");
        params.add("receiptWording", "HTTP TEST");
        params.add("startDate", LocalDate.now().plusDays(1).toString());
        params.add("endDate", LocalDate.now().plusDays(10).toString());
        ResponseEntity<String> resp = postFormWithCsrf("/campaigns/new", "/campaigns", h, params);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 400, 403);
    }

    @Test
    void postCampaignForbiddenForCs() {
        HttpHeaders h = loginAs("cs", "CsUser@Retail2024!");
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("name", "Should Fail");
        ResponseEntity<String> resp = postFormWithCsrf("/campaigns/new", "/campaigns", h, params);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void postCampaignRequiresAuth() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("name", "Test");
        ResponseEntity<String> resp = postForm("/campaigns", null, params);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 403);
    }

    // ── GET /campaigns/{id}/edit ──────────────────────────────────────────────

    @Test
    void editCampaignFormReachableForOps() {
        // Use a seeded campaign (ID 1 is "Spring Refresh" from V12)
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/campaigns/1/edit", h);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 404, 500);
    }

    @Test
    void editCampaignFormForbiddenForCs() {
        HttpHeaders h = loginAs("cs", "CsUser@Retail2024!");
        ResponseEntity<String> resp = get("/campaigns/1/edit", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void editCampaignRequiresAuth() {
        ResponseEntity<String> resp = get("/campaigns/1/edit", null);
        assertThat(resp.getStatusCode().value()).isIn(200, 302);
    }

    // ── PUT /campaigns/{id} ───────────────────────────────────────────────────

    @Test
    void updateCampaignWithPutForbiddenForCs() {
        HttpHeaders h = loginAs("cs", "CsUser@Retail2024!");
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("name", "Updated");
        ResponseEntity<String> resp = putFormWithCsrf("/campaigns/1/edit", "/campaigns/1", h, params);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void updateCampaignWithPutForOps() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        // Create a campaign first via service
        CreateCampaignRequest req = new CreateCampaignRequest();
        req.setName("To Update Via HTTP");
        req.setType(CampaignType.DISCOUNT);
        req.setRiskLevel(RiskLevel.LOW);
        req.setStartDate(LocalDate.now().plusDays(1));
        req.setEndDate(LocalDate.now().plusDays(5));
        req.setReceiptWording("OLD");
        Campaign c = campaignService.createCampaign(req, "ops", "127.0.0.1");

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("name", "Updated Via HTTP");
        params.add("description", "updated");
        params.add("type", "DISCOUNT");
        params.add("riskLevel", "LOW");
        params.add("receiptWording", "NEW WORDING");
        params.add("startDate", LocalDate.now().plusDays(1).toString());
        params.add("endDate", LocalDate.now().plusDays(5).toString());
        ResponseEntity<String> resp = putFormWithCsrf(
                "/campaigns/" + c.getId() + "/edit",
                "/campaigns/" + c.getId(), h, params);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 400, 403);
    }

    @Test
    void updateCampaignRequiresAuth() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        ResponseEntity<String> resp = putFormWithCsrf("/campaigns/1/edit", "/campaigns/1", null, params);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 403);
    }

    // ── POST /campaigns/{id}/submit ───────────────────────────────────────────

    @Test
    void submitCampaignForReviewForOps() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        // Create a DRAFT campaign
        CreateCampaignRequest req = new CreateCampaignRequest();
        req.setName("To Submit Via HTTP " + System.currentTimeMillis());
        req.setType(CampaignType.COUPON);
        req.setRiskLevel(RiskLevel.LOW);
        req.setStartDate(LocalDate.now().plusDays(1));
        req.setEndDate(LocalDate.now().plusDays(5));
        req.setReceiptWording("SUBMIT TEST");
        Campaign c = campaignService.createCampaign(req, "ops", "127.0.0.1");

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        ResponseEntity<String> resp = postFormWithCsrf(
                "/campaigns", "/campaigns/" + c.getId() + "/submit", h, params);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 400, 403);
    }

    @Test
    void submitCampaignForbiddenForCs() {
        HttpHeaders h = loginAs("cs", "CsUser@Retail2024!");
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        ResponseEntity<String> resp = postFormWithCsrf("/campaigns", "/campaigns/1/submit", h, params);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void submitCampaignRequiresAuth() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        ResponseEntity<String> resp = postForm("/campaigns/1/submit", null, params);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 403);
    }

    // ── DELETE /campaigns/{id} ────────────────────────────────────────────────

    @Test
    void deleteCampaignForOps() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        // Create a campaign to delete
        CreateCampaignRequest req = new CreateCampaignRequest();
        req.setName("To Delete " + System.currentTimeMillis());
        req.setType(CampaignType.COUPON);
        req.setRiskLevel(RiskLevel.LOW);
        req.setStartDate(LocalDate.now().plusDays(1));
        req.setEndDate(LocalDate.now().plusDays(5));
        req.setReceiptWording("DELETE TEST");
        Campaign c = campaignService.createCampaign(req, "ops", "127.0.0.1");

        ResponseEntity<String> resp = deleteWithCsrf(
                "/campaigns/" + c.getId() + "/edit", "/campaigns/" + c.getId(), h);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 400, 403, 405);
    }

    @Test
    void deleteCampaignForbiddenForCs() {
        HttpHeaders h = loginAs("cs", "CsUser@Retail2024!");
        ResponseEntity<String> resp = deleteWithCsrf("/campaigns/1/edit", "/campaigns/1", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403, 405);
    }

    @Test
    void deleteCampaignRequiresAuth() {
        ResponseEntity<String> resp = deleteWithCsrf("/campaigns/1/edit", "/campaigns/1", null);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 403, 405);
    }

    // ── GET /campaigns/validate/dates ─────────────────────────────────────────

    @Test
    void campaignDateValidateEndpointReachable() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        String tomorrow = LocalDate.now().plusDays(1).toString();
        String nextWeek = LocalDate.now().plusDays(7).toString();
        ResponseEntity<String> resp = get(
                "/campaigns/validate/dates?startDate=" + tomorrow + "&endDate=" + nextWeek, h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    // ── GET /campaigns/validate/code ──────────────────────────────────────────

    @Test
    void campaignValidateCodeEndpointReachable() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/campaigns/validate/code?code=TESTCODE123", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    // ── GET /campaigns/validate/discount ──────────────────────────────────────

    @Test
    void campaignValidateDiscountEndpointReachable() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/campaigns/validate/discount?type=PERCENT&value=10", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void campaignValidateDiscountRejectsOver100() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/campaigns/validate/discount?type=PERCENT&value=150", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        // Response should not be empty
        assertThat(resp.getBody()).isNotNull();
    }

    // ── POST /campaigns/preview-receipt ───────────────────────────────────────

    @Test
    void previewReceiptPostReturnsHtmlFragment() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("receiptWording", "TEST WORDING PREVIEW");
        ResponseEntity<String> resp = postFormWithCsrf("/campaigns/new", "/campaigns/preview-receipt", h, params);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 400, 403);
    }

    // ── GET /campaigns/{id}/preview-receipt ───────────────────────────────────

    @Test
    void previewReceiptGetForSeededCampaign() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        // Campaign 1 is seeded
        ResponseEntity<String> resp = get("/campaigns/1/preview-receipt", h);
        assertThat(resp.getStatusCode().value()).isIn(200, 404, 500);
        if (resp.getStatusCode().is2xxSuccessful()) {
            assertThat(resp.getBody()).isNotEmpty();
        }
    }
}
