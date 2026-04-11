package com.meridian.retail.unit.security;

import com.meridian.retail.security.*;
import com.meridian.retail.service.*;

import com.meridian.retail.repository.UsedNonceRepository;
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
import static org.mockito.Mockito.when;

/**
 * R4 HIGH #1 + #2 (audit re-check): NEITHER NonceValidationFilter NOR
 * RequestSigningFilter carries a form-encoded bypass for /admin/**. Browser form POSTs
 * must include {@code _nonce}, {@code _timestamp}, and {@code _signature} hidden fields
 * injected by {@code static/js/nonce-form.js}. The signature is fetched from
 * {@code POST /admin/sign-form}, which keeps the HMAC secret on the server.
 */
@ExtendWith(MockitoExtension.class)
class AdminFilterBypassTest {

    @Mock FilterChain chain;
    @Mock UsedNonceRepository usedNonceRepository;

    @Test
    void nonceFilterRejectsAdminFormPostWithoutNonce() throws Exception {
        // R4 HIGH #2: blanket form-encoded bypass removed. A form POST to /admin/**
        // without _nonce/_timestamp must now be rejected.
        NonceValidationFilter f = new NonceValidationFilter(usedNonceRepository);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("POST");
        req.setRequestURI("/admin/users");
        req.setContentType("application/x-www-form-urlencoded");
        MockHttpServletResponse res = new MockHttpServletResponse();

        f.doFilter(req, res, chain);

        org.assertj.core.api.Assertions.assertThat(res.getStatus()).isEqualTo(400);
        verify(chain, never()).doFilter(req, res);
    }

    @Test
    void nonceFilterAcceptsAdminFormPostWithNonceFormFields() throws Exception {
        // The browser-friendly path: hidden _nonce + _timestamp form params satisfy the
        // anti-replay check (no header required).
        when(usedNonceRepository.existsByNonce("nonce-form-1")).thenReturn(false);
        NonceValidationFilter f = new NonceValidationFilter(usedNonceRepository);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("POST");
        req.setRequestURI("/admin/users");
        req.setContentType("application/x-www-form-urlencoded");
        req.setParameter("_nonce", "nonce-form-1");
        req.setParameter("_timestamp", String.valueOf(System.currentTimeMillis()));
        MockHttpServletResponse res = new MockHttpServletResponse();

        f.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
    }

    @Test
    void signingFilterRejectsAdminFormPostWithoutSignature() throws Exception {
        // R4 audit re-check fix: form-encoded bypass removed. A POST under /admin/**
        // without _signature must now be rejected with 403.
        RequestSigningFilter f = new RequestSigningFilter();
        ReflectionTestUtils.setField(f, "signingSecret", "retail-campaign-hmac-signing-key!!");
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("POST");
        req.setRequestURI("/admin/users");
        req.setContentType("application/x-www-form-urlencoded");
        MockHttpServletResponse res = new MockHttpServletResponse();

        f.doFilter(req, res, chain);

        org.assertj.core.api.Assertions.assertThat(res.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(req, res);
    }

    @Test
    void signingFilterAcceptsAdminFormPostWithValidSignatureFormFields() throws Exception {
        // The browser-friendly path: hidden _nonce/_timestamp/_signature form params
        // satisfy the signing check using the form-mode canonical
        // (method + path + timestamp + nonce, no body hash).
        String secret = "retail-campaign-hmac-signing-key!!";
        RequestSigningFilter f = new RequestSigningFilter();
        ReflectionTestUtils.setField(f, "signingSecret", secret);

        String method = "POST";
        String path = "/admin/users";
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = "browser-nonce-1";
        String expected = f.signFormCanonical(method, path, timestamp, nonce);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod(method);
        req.setRequestURI(path);
        req.setContentType("application/x-www-form-urlencoded");
        req.setParameter("_nonce", nonce);
        req.setParameter("_timestamp", timestamp);
        req.setParameter("_signature", expected);
        MockHttpServletResponse res = new MockHttpServletResponse();

        f.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
    }

    @Test
    void signingFilterRejectsAdminFormPostWithBadSignature() throws Exception {
        RequestSigningFilter f = new RequestSigningFilter();
        ReflectionTestUtils.setField(f, "signingSecret", "retail-campaign-hmac-signing-key!!");

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("POST");
        req.setRequestURI("/admin/users");
        req.setContentType("application/x-www-form-urlencoded");
        req.setParameter("_nonce", "browser-nonce-2");
        req.setParameter("_timestamp", String.valueOf(System.currentTimeMillis()));
        req.setParameter("_signature", "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
        MockHttpServletResponse res = new MockHttpServletResponse();

        f.doFilter(req, res, chain);

        org.assertj.core.api.Assertions.assertThat(res.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(req, res);
    }

    @Test
    void signingFilterSkipsSignFormEndpoint() throws Exception {
        // /admin/sign-form must NOT be subject to the signing filter — it issues the
        // very signature this filter validates. Spring Security ROLE_ADMIN still gates it.
        RequestSigningFilter f = new RequestSigningFilter();
        ReflectionTestUtils.setField(f, "signingSecret", "retail-campaign-hmac-signing-key!!");
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("POST");
        req.setRequestURI("/admin/sign-form");
        req.setContentType("application/json");
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
