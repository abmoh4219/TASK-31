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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R4 audit HIGH #1: NonceValidationFilter must protect the actual UI completion
 * endpoints for the dual-approval workflow:
 *
 *   POST /approval/{id}/approve-first
 *   POST /approval/{id}/approve-second
 *
 * Before the fix only POST /approval/dual-approve/{requestId} was covered, but the
 * Thymeleaf approval queue submits to approve-first / approve-second instead — so the
 * real high-risk path had zero anti-replay protection.
 *
 * These tests run NonceValidationFilter directly with a mocked UsedNonceRepository so
 * they don't need MySQL or a Spring context.
 */
@ExtendWith(MockitoExtension.class)
class ApprovalNonceFilterTest {

    @Mock UsedNonceRepository usedNonceRepository;
    @Mock FilterChain chain;

    private MockHttpServletRequest formPost(String uri, String nonce, String timestamp) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", uri);
        req.setContentType("application/x-www-form-urlencoded");
        if (nonce != null) req.setParameter("_nonce", nonce);
        if (timestamp != null) req.setParameter("_timestamp", timestamp);
        return req;
    }

    @Test
    void approveSecondWithoutNonceIsRejected() throws Exception {
        NonceValidationFilter f = new NonceValidationFilter(usedNonceRepository);
        MockHttpServletRequest req = formPost("/approval/42/approve-second", null, null);
        MockHttpServletResponse res = new MockHttpServletResponse();

        f.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(400);
        verify(chain, never()).doFilter(req, res);
    }

    @Test
    void approveFirstWithoutNonceIsRejected() throws Exception {
        NonceValidationFilter f = new NonceValidationFilter(usedNonceRepository);
        MockHttpServletRequest req = formPost("/approval/42/approve-first", null, null);
        MockHttpServletResponse res = new MockHttpServletResponse();

        f.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(400);
        verify(chain, never()).doFilter(req, res);
    }

    @Test
    void approveSecondWithFreshNonceIsAccepted() throws Exception {
        when(usedNonceRepository.existsByNonce("nonce-fresh-1")).thenReturn(false);
        NonceValidationFilter f = new NonceValidationFilter(usedNonceRepository);
        MockHttpServletRequest req = formPost("/approval/42/approve-second",
                "nonce-fresh-1", String.valueOf(System.currentTimeMillis()));
        MockHttpServletResponse res = new MockHttpServletResponse();

        f.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
    }

    @Test
    void approveSecondReplayedNonceIsRejected() throws Exception {
        when(usedNonceRepository.existsByNonce("nonce-replay-1")).thenReturn(true);
        NonceValidationFilter f = new NonceValidationFilter(usedNonceRepository);
        MockHttpServletRequest req = formPost("/approval/42/approve-second",
                "nonce-replay-1", String.valueOf(System.currentTimeMillis()));
        MockHttpServletResponse res = new MockHttpServletResponse();

        f.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(400);
        verify(chain, never()).doFilter(req, res);
    }
}
