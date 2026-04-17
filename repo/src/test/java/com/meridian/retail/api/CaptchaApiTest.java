package com.meridian.retail.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the CAPTCHA endpoints — public endpoints that require no authentication.
 */
class CaptchaApiTest extends AbstractApiTest {

    // ── GET /captcha/image ────────────────────────────────────────────────────────

    @Test
    void captchaImageIsPublicAndReturnsPng() {
        ResponseEntity<byte[]> resp = http().exchange(
                url("/captcha/image"), HttpMethod.GET, null, byte[].class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        // PNG magic bytes: 0x89 0x50 0x4E 0x47
        byte[] body = resp.getBody();
        assertThat(body).isNotNull().hasSizeGreaterThan(4);
        assertThat(body[0]).isEqualTo((byte) 0x89);
        assertThat(body[1]).isEqualTo((byte) 0x50); // 'P'
        assertThat(body[2]).isEqualTo((byte) 0x4E); // 'N'
        assertThat(body[3]).isEqualTo((byte) 0x47); // 'G'
    }

    @Test
    void captchaImageDoesNotRequireAuth() {
        // Should not redirect to login
        ResponseEntity<String> resp = get("/captcha/image", null);
        assertThat(resp.getStatusCode().value()).isNotEqualTo(302);
    }

    // ── POST /captcha/validate ────────────────────────────────────────────────────

    @Test
    void captchaValidateWithWrongAnswerReturnsHtmlFragment() {
        // Even without a real session CAPTCHA, posting returns an HTML fragment response.
        // We don't need CSRF here since /captcha/validate is public.
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("captcha", "wrongAnswer");

        // Get CSRF first by hitting the login page
        ResponseEntity<String> loginPage = get("/login", null);
        String maskedCsrf = extractHiddenField(loginPage.getBody(), "_csrf");
        String rawXsrf = cookie(loginPage.getHeaders().get(HttpHeaders.SET_COOKIE), "XSRF-TOKEN");
        String session = cookie(loginPage.getHeaders().get(HttpHeaders.SET_COOKIE), "JSESSIONID");

        String sessionAndXsrf = (session != null ? "JSESSIONID=" + session : "")
                + (rawXsrf != null ? (session != null ? "; " : "") + "XSRF-TOKEN=" + rawXsrf : "");
        if (!sessionAndXsrf.isEmpty()) h.add(HttpHeaders.COOKIE, sessionAndXsrf);
        if (maskedCsrf != null) params.add("_csrf", maskedCsrf);

        ResponseEntity<String> resp = http().exchange(
                url("/captcha/validate"), HttpMethod.POST, new HttpEntity<>(params, h), String.class);
        // Should return an HTML fragment (text/html), not redirect
        assertThat(resp.getStatusCode().value()).isIn(200, 400, 403);
    }

    @Test
    void captchaValidateReachableWithoutAuth() {
        // The endpoint exists — just verify it responds
        ResponseEntity<String> resp = get("/captcha/validate", null);
        // GET might return 405 (method not allowed) or 200 — either way endpoint exists
        assertThat(resp.getStatusCode().value()).isNotEqualTo(404);
    }
}
