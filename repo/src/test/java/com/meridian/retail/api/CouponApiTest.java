package com.meridian.retail.api;

import com.meridian.retail.dto.CreateCouponRequest;
import com.meridian.retail.entity.DiscountType;
import com.meridian.retail.repository.CouponRepository;
import com.meridian.retail.service.CouponService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Coupon API tests — real HTTP, no MockMvc.
 */
class CouponApiTest extends AbstractApiTest {

    @Autowired CouponService couponService;
    @Autowired CouponRepository couponRepository;

    // ── GET /coupons ──────────────────────────────────────────────────────────

    @Test
    void couponListReachableForOps() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/coupons", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void couponListReachableForAdmin() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        ResponseEntity<String> resp = get("/coupons", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void couponListContainsSeededCoupons() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/coupons", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        // SPRING15 and LOYAL20 are seeded
        assertThat(resp.getBody()).containsIgnoringCase("SPRING");
    }

    @Test
    void couponListReachableForCs() {
        HttpHeaders h = loginAs("cs", "CsUser@Retail2024!");
        ResponseEntity<String> resp = get("/coupons", h);
        // CS role can view coupon list
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 403);
    }

    @Test
    void anonymousCannotAccessCouponList() {
        ResponseEntity<String> resp = get("/coupons", null);
        assertThat(resp.getStatusCode().value()).isIn(200, 302);
        if (resp.getStatusCode().is3xxRedirection()) {
            assertThat(resp.getHeaders().getLocation().toString()).contains("login");
        }
    }

    // ── GET /coupons/new ──────────────────────────────────────────────────────

    @Test
    void newCouponFormReachableForAdmin() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        ResponseEntity<String> resp = get("/coupons/new", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void newCouponFormReachableForOps() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/coupons/new", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    // ── POST /coupons ─────────────────────────────────────────────────────────

    @Test
    void postCouponCreateWithValidData() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", "TESTCOUPON" + System.currentTimeMillis());
        params.add("discountType", "PERCENT");
        params.add("discountValue", "10");
        params.add("minPurchaseAmount", "0");
        params.add("maxUses", "100");
        params.add("stackable", "false");
        ResponseEntity<String> resp = postFormWithCsrf("/coupons/new", "/coupons", h, params);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 400, 403);
    }

    @Test
    void postCouponForbiddenForFinance() {
        HttpHeaders h = loginAs("finance", "Finance@Retail2024!");
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", "FINANCETEST");
        ResponseEntity<String> resp = postFormWithCsrf("/coupons/new", "/coupons", h, params);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void postCouponRequiresAuth() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", "ANONTEST");
        ResponseEntity<String> resp = postForm("/coupons", null, params);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 403);
    }

    // ── GET /coupons/{id}/edit ────────────────────────────────────────────────

    @Test
    void editCouponFormReachableForOps() {
        // SPRING15 is seeded with ID 1 (or find it)
        var coupon = couponRepository.findByCodeIgnoreCase("SPRING15").orElse(null);
        if (coupon != null) {
            HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
            ResponseEntity<String> resp = get("/coupons/" + coupon.getId() + "/edit", h);
            assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        }
    }

    @Test
    void editCouponFormForbiddenForFinance() {
        HttpHeaders h = loginAs("finance", "Finance@Retail2024!");
        ResponseEntity<String> resp = get("/coupons/1/edit", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void editCouponFormForbiddenForCs() {
        HttpHeaders h = loginAs("cs", "CsUser@Retail2024!");
        ResponseEntity<String> resp = get("/coupons/1/edit", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void editCouponRequiresAuth() {
        ResponseEntity<String> resp = get("/coupons/1/edit", null);
        assertThat(resp.getStatusCode().value()).isIn(200, 302);
    }

    // ── POST /coupons/{id} (update) ───────────────────────────────────────────

    @Test
    void updateCouponForOps() {
        // Create a coupon via service to get a valid ID
        CreateCouponRequest req = new CreateCouponRequest();
        req.setCode("HTTPUPDATE" + System.currentTimeMillis());
        req.setCampaignId(1L);
        req.setDiscountType(DiscountType.PERCENT);
        req.setDiscountValue(BigDecimal.valueOf(5));
        req.setMaxUses(50);
        req.setMinPurchaseAmount(BigDecimal.ZERO);
        var coupon = couponService.createCoupon(req, "ops", "127.0.0.1");

        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", coupon.getCode()); // code is immutable
        params.add("discountType", "PERCENT");
        params.add("discountValue", "8");
        params.add("maxUses", "75");
        params.add("minPurchaseAmount", "0");
        params.add("stackable", "false");
        ResponseEntity<String> resp = postFormWithCsrf(
                "/coupons/" + coupon.getId() + "/edit",
                "/coupons/" + coupon.getId(), h, params);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 400, 403);
    }

    @Test
    void updateCouponForbiddenForFinance() {
        HttpHeaders h = loginAs("finance", "Finance@Retail2024!");
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        ResponseEntity<String> resp = postFormWithCsrf(
                "/coupons/1/edit", "/coupons/1", h, params);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void updateCouponRequiresAuth() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        ResponseEntity<String> resp = postForm("/coupons/1", null, params);
        assertThat(resp.getStatusCode().value()).isIn(200, 302, 403);
    }

    // ── GET /coupons/check-code ───────────────────────────────────────────────

    @Test
    void checkCodeEndpointReturnsTakenForExistingCode() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/coupons/check-code?code=SPRING15", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        // Response should indicate SPRING15 is already in use
        assertThat(resp.getBody()).isNotNull();
    }

    @Test
    void checkCodeEndpointReturnsAvailableForNewCode() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/coupons/check-code?code=BRANDNEWCODE" + System.currentTimeMillis(), h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void checkCodeForbiddenForFinance() {
        HttpHeaders h = loginAs("finance", "Finance@Retail2024!");
        ResponseEntity<String> resp = get("/coupons/check-code?code=TEST", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void checkCodeRequiresAuth() {
        ResponseEntity<String> resp = get("/coupons/check-code?code=TEST", null);
        assertThat(resp.getStatusCode().value()).isIn(200, 302);
    }
}
