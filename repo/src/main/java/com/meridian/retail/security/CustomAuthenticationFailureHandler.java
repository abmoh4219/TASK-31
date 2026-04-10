package com.meridian.retail.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Authentication failure handler.
 *
 * Responsibilities (in order):
 *   1. Track the failed attempt against the username + client IP (account/IP lockout state).
 *   2. Validate the locally-rendered CAPTCHA if the session indicates one was required
 *      (i.e. there have already been >= captchaThreshold failures for this account).
 *   3. Decide which redirect to issue:
 *        - /login?ipblocked  if this IP has now exceeded its hourly threshold
 *        - /login?locked     if this account is now locked
 *        - /login?error      otherwise (generic "wrong credentials")
 *   4. Set the session "captchaRequired" attribute if the failure count is at or above
 *      the captcha threshold so the next login form render shows the CAPTCHA challenge.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CustomAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final AccountLockoutService lockoutService;
    private final LocalCaptchaService captchaService;

    @Value("${app.captcha.show-after-failures:3}")
    private int captchaThreshold;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception)
            throws IOException, ServletException {

        String username = request.getParameter("username");
        String ip = clientIp(request);
        HttpSession session = request.getSession(true);

        // Step 1: track the failed attempt against the database — this is what drives lockout.
        lockoutService.trackFailedAttempt(username, ip);

        // Step 2: if the previous render required a CAPTCHA, validate it. A wrong CAPTCHA
        //         counts as a failed attempt (already tracked) but we don't reveal which part failed.
        boolean captchaWasRequired = Boolean.TRUE.equals(session.getAttribute("captchaRequired"));
        if (captchaWasRequired) {
            String captchaInput = request.getParameter("captcha");
            boolean captchaOk = captchaService.validateAnswer(session, captchaInput);
            log.debug("CAPTCHA validation for failed login: {}", captchaOk ? "ok" : "fail");
        }

        // Step 3: bump the captchaRequired flag if we're now at the show-CAPTCHA threshold.
        long failures = lockoutService.currentFailureCount(username);
        if (failures >= captchaThreshold) {
            session.setAttribute("captchaRequired", Boolean.TRUE);
        }

        // Step 4: choose the appropriate redirect.
        String redirect;
        if (lockoutService.isIpBlocked(ip)) {
            log.warn("IP {} is now blocked (too many failures)", ip);
            redirect = "/login?ipblocked";
        } else if (lockoutService.isAccountLocked(username)) {
            log.warn("Account '{}' is now locked", username);
            redirect = "/login?locked";
        } else {
            redirect = "/login?error";
        }
        getRedirectStrategy().sendRedirect(request, response, redirect);
    }

    /** X-Forwarded-For aware client IP extraction. */
    private String clientIp(HttpServletRequest request) {
        String header = request.getHeader("X-Forwarded-For");
        if (header != null && !header.isBlank()) {
            return header.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
