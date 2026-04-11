package com.meridian.retail.unit.security;

import com.meridian.retail.security.*;
import com.meridian.retail.service.*;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pre-auth gate must short-circuit the chain (i.e. never call chain.doFilter) when:
 *   - the username is account-locked
 *   - the IP is blocked
 *   - the session requires CAPTCHA and the submitted value is missing/invalid
 * and must pass through otherwise.
 */
@ExtendWith(MockitoExtension.class)
class PreAuthLockoutFilterTest {

    @Mock AccountLockoutService lockoutService;
    @Mock LocalCaptchaService captchaService;
    @Mock FilterChain chain;

    @InjectMocks PreAuthLockoutFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/login");
        request.setParameter("username", "alice");
        response = new MockHttpServletResponse();
    }

    @Test
    void passesThroughWhenNotALoginPost() throws Exception {
        request.setRequestURI("/some/other");
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }

    @Test
    void blocksLockedAccountWithoutCallingChain() throws Exception {
        when(lockoutService.isIpBlocked(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        when(lockoutService.isAccountLocked("alice")).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getRedirectedUrl()).contains("locked");
    }

    @Test
    void blocksBlockedIpWithoutCallingChain() throws Exception {
        when(lockoutService.isIpBlocked(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getRedirectedUrl()).contains("ipblocked");
    }

    @Test
    void blocksWhenCaptchaRequiredAndMissing() throws Exception {
        when(lockoutService.isIpBlocked(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        when(lockoutService.isAccountLocked("alice")).thenReturn(false);
        request.getSession(true).setAttribute("captchaRequired", Boolean.TRUE);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getRedirectedUrl()).contains("captcha");
    }

    @Test
    void allowsWhenNotLockedAndNoCaptchaRequired() throws Exception {
        when(lockoutService.isIpBlocked(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        when(lockoutService.isAccountLocked("alice")).thenReturn(false);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void allowsWhenCaptchaRequiredAndValid() throws Exception {
        when(lockoutService.isIpBlocked(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        when(lockoutService.isAccountLocked("alice")).thenReturn(false);
        request.getSession(true).setAttribute("captchaRequired", Boolean.TRUE);
        request.setParameter("captcha", "ABC123");
        when(captchaService.validateAnswer(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("ABC123"))).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
