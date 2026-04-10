package com.meridian.retail.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * BLOCKER #1 regression: browser form POSTs to /admin/** must pass straight through
 * both the nonce filter AND the signing filter, because admin Thymeleaf forms have no
 * way to produce X-Nonce / X-Signature / X-Timestamp headers and are already protected
 * by CSRF + session auth + RBAC. Only JSON / programmatic callers should be subjected
 * to the anti-replay + signing checks.
 */
@ExtendWith(MockitoExtension.class)
class AdminFilterBypassTest {

    @Mock FilterChain chain;

    @Test
    void nonceFilterSkipsAdminFormPost() throws Exception {
        NonceValidationFilter f = new NonceValidationFilter(null);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("POST");
        req.setRequestURI("/admin/users");
        req.setContentType("application/x-www-form-urlencoded");
        MockHttpServletResponse res = new MockHttpServletResponse();

        f.doFilter(req, res, chain);

        // No 400 (missing header), chain was invoked normally.
        verify(chain).doFilter(req, res);
    }

    @Test
    void signingFilterSkipsAdminFormPost() throws Exception {
        RequestSigningFilter f = new RequestSigningFilter();
        ReflectionTestUtils.setField(f, "signingSecret", "retail-campaign-hmac-signing-key!!");
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("POST");
        req.setRequestURI("/admin/users");
        req.setContentType("application/x-www-form-urlencoded");
        MockHttpServletResponse res = new MockHttpServletResponse();

        f.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
    }

    /** R3 tightening: multipart/form-data under /admin/** must NOT be exempt. */
    @Test
    void signingFilterRejectsAdminMultipartWithoutSignature() throws Exception {
        RequestSigningFilter f = new RequestSigningFilter();
        ReflectionTestUtils.setField(f, "signingSecret", "retail-campaign-hmac-signing-key!!");
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("POST");
        req.setRequestURI("/admin/users");
        req.setContentType("multipart/form-data; boundary=xxx");
        MockHttpServletResponse res = new MockHttpServletResponse();

        f.doFilter(req, res, chain);

        verify(chain, never()).doFilter(req, res);
    }

    /** R3 tightening: multipart/form-data under /admin/** must NOT be exempt from nonce either. */
    @Test
    void nonceFilterRejectsAdminMultipartWithoutHeaders() throws Exception {
        NonceValidationFilter f = new NonceValidationFilter(null);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("POST");
        req.setRequestURI("/admin/users");
        req.setContentType("multipart/form-data; boundary=xxx");
        MockHttpServletResponse res = new MockHttpServletResponse();

        f.doFilter(req, res, chain);

        verify(chain, never()).doFilter(req, res);
    }

    @Test
    void nonceFilterStillRejectsAdminJsonWithoutHeaders() throws Exception {
        NonceValidationFilter f = new NonceValidationFilter(null);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("POST");
        req.setRequestURI("/admin/api/something");
        req.setContentType("application/json");
        MockHttpServletResponse res = new MockHttpServletResponse();

        f.doFilter(req, res, chain);

        // JSON callers must still be blocked without a nonce. Chain NOT invoked.
        verify(chain, never()).doFilter(req, res);
    }
}
