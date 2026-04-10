package com.meridian.retail.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Pre-authentication gate that enforces lockout + CAPTCHA BEFORE credentials are evaluated.
 *
 * Without this filter, a brute-forcer that guesses correct credentials after being
 * locked/CAPTCHA-challenged would succeed because lockout + CAPTCHA were only checked on
 * the failure path. This filter closes the loophole: on POST /login it short-circuits
 * to /login?error=locked, /login?error=ipblocked, or /login?error=captcha if the guard
 * fails, so Spring Security never gets a chance to validate the password.
 *
 * Registered BEFORE UsernamePasswordAuthenticationFilter in SecurityConfig.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PreAuthLockoutFilter extends OncePerRequestFilter {

    private final AccountLockoutService lockoutService;
    private final LocalCaptchaService captchaService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        if (!isLoginPost(request)) {
            chain.doFilter(request, response);
            return;
        }

        String username = request.getParameter("username");
        String ip = clientIp(request);

        // IP block takes precedence — an attacker burning through many accounts is blocked
        // across all of them once the IP threshold trips.
        if (lockoutService.isIpBlocked(ip)) {
            log.warn("Pre-auth block: IP {} is blocked; rejecting /login", ip);
            redirect(request, response, "/login?ipblocked");
            return;
        }

        if (lockoutService.isAccountLocked(username)) {
            log.warn("Pre-auth block: account '{}' is locked; rejecting /login", username);
            redirect(request, response, "/login?locked");
            return;
        }

        // CAPTCHA gate: if the session indicates a CAPTCHA challenge is in effect, validate
        // it BEFORE credential checking. Otherwise a correct password would bypass the CAPTCHA.
        HttpSession session = request.getSession(false);
        if (session != null && Boolean.TRUE.equals(session.getAttribute("captchaRequired"))) {
            String captchaInput = request.getParameter("captcha");
            if (captchaInput == null || captchaInput.isBlank()
                    || !captchaService.validateAnswer(session, captchaInput)) {
                log.warn("Pre-auth block: CAPTCHA missing/invalid for user '{}'", username);
                redirect(request, response, "/login?captcha");
                return;
            }
            // Valid CAPTCHA consumed — validateAnswer already removed it from the session.
        }

        chain.doFilter(request, response);
    }

    private boolean isLoginPost(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && "/login".equals(request.getRequestURI());
    }

    private void redirect(HttpServletRequest request, HttpServletResponse response, String location)
            throws IOException {
        response.sendRedirect(request.getContextPath() + location);
    }

    private String clientIp(HttpServletRequest request) {
        String header = request.getHeader("X-Forwarded-For");
        if (header != null && !header.isBlank()) {
            return header.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
