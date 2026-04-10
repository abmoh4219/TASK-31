package com.meridian.retail.security;

import com.meridian.retail.entity.User;
import com.meridian.retail.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Successful login handler.
 *
 * Responsibilities:
 *   1. Reset all failed-attempt records for this user (so future failures start fresh).
 *   2. Update last_login_at on the User row.
 *   3. Clear the captchaRequired session attribute.
 *   4. Redirect by role to the appropriate landing page.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CustomAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final AccountLockoutService lockoutService;
    private final UserRepository userRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException, ServletException {

        String username = authentication.getName();
        String ip = clientIp(request);

        // Step 1: clear lockout state and record the success in the audit trail.
        lockoutService.resetAttempts(username);
        lockoutService.trackSuccessfulAttempt(username, ip);

        // Step 2: persist last_login_at on the user row.
        Optional<User> userOpt = userRepository.findByUsername(username);
        userOpt.ifPresent(u -> {
            u.setLastLoginAt(LocalDateTime.now());
            userRepository.save(u);
        });

        // Step 3: forget the captcha challenge for the next visit.
        if (request.getSession(false) != null) {
            request.getSession(false).removeAttribute("captchaRequired");
        }

        // Step 4: role-based redirect.
        String role = primaryRole(authentication);
        String target = switch (role) {
            case "ROLE_ADMIN"            -> "/admin/dashboard";
            case "ROLE_REVIEWER"         -> "/approval/queue";
            case "ROLE_FINANCE"          -> "/analytics/dashboard";
            case "ROLE_OPERATIONS"       -> "/campaigns";
            case "ROLE_CUSTOMER_SERVICE" -> "/campaigns";
            default                      -> "/";
        };
        log.info("Login success: user={} role={} -> {}", username, role, target);
        getRedirectStrategy().sendRedirect(request, response, target);
    }

    private String primaryRole(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_USER");
    }

    private String clientIp(HttpServletRequest request) {
        String header = request.getHeader("X-Forwarded-For");
        if (header != null && !header.isBlank()) {
            return header.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
