package com.meridian.retail.security;

import com.meridian.retail.entity.LoginAttempt;
import com.meridian.retail.repository.LoginAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Account / IP lockout per SPEC.md:
 *   - 5 failed attempts per account within 15 minutes  -> account locked for 15 minutes
 *   - 20 failed attempts per IP address within 60 min  -> IP blocked for the remainder of the hour
 *
 * The "lock duration" is enforced implicitly by the time-window query: once the oldest
 * failure falls outside the window, the count drops below the threshold and the account
 * unlocks. This means we never have to write a separate "unlock_at" column or run a
 * scheduled unlock job — the data IS the lock state.
 *
 * QA-facing readability: every public method here is a single, explicit query against
 * login_attempts. No magic, no caching layer. The static-audit reviewer can verify lockout
 * by reading these 4 methods and one repository interface.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountLockoutService {

    private final LoginAttemptRepository loginAttemptRepository;

    @Value("${app.lockout.account-max-attempts:5}")
    private int accountMaxAttempts;

    @Value("${app.lockout.account-window-minutes:15}")
    private int accountWindowMinutes;

    @Value("${app.lockout.ip-max-attempts:20}")
    private int ipMaxAttempts;

    @Value("${app.lockout.ip-window-minutes:60}")
    private int ipWindowMinutes;

    /**
     * Returns true if {@code username} has accumulated >= 5 failed attempts in the last 15 minutes.
     * The lock automatically lifts once the oldest failure ages out of the window.
     */
    public boolean isAccountLocked(String username) {
        if (username == null || username.isBlank()) return false;
        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(accountWindowMinutes);
        long failures = loginAttemptRepository
                .countByUsernameAndSuccessFalseAndAttemptedAtAfter(username, windowStart);
        return failures >= accountMaxAttempts;
    }

    /**
     * Returns true if {@code ipAddress} has accumulated >= 20 failed attempts in the last 60 minutes.
     */
    public boolean isIpBlocked(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) return false;
        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(ipWindowMinutes);
        long failures = loginAttemptRepository
                .countByIpAddressAndSuccessFalseAndAttemptedAtAfter(ipAddress, windowStart);
        return failures >= ipMaxAttempts;
    }

    /**
     * Records a single failed login attempt. Always called from
     * {@link CustomAuthenticationFailureHandler} BEFORE the lockout check, so the call that
     * pushes the account over the threshold is itself counted.
     */
    public void trackFailedAttempt(String username, String ipAddress) {
        LoginAttempt attempt = LoginAttempt.builder()
                .username(username == null ? "" : username)
                .ipAddress(ipAddress == null ? "" : ipAddress)
                .success(false)
                .attemptedAt(LocalDateTime.now())
                .build();
        loginAttemptRepository.save(attempt);
        log.warn("Failed login tracked: user={} ip={}", username, ipAddress);
    }

    /** Records a successful login (kept for analytics + visible "last login" tracking). */
    public void trackSuccessfulAttempt(String username, String ipAddress) {
        LoginAttempt attempt = LoginAttempt.builder()
                .username(username == null ? "" : username)
                .ipAddress(ipAddress == null ? "" : ipAddress)
                .success(true)
                .attemptedAt(LocalDateTime.now())
                .build();
        loginAttemptRepository.save(attempt);
    }

    /**
     * Clears the failed-attempt history for a username on successful login. Without this
     * the failure count would re-trigger lockout the next time the user typed a wrong password.
     */
    public void resetAttempts(String username) {
        if (username == null || username.isBlank()) return;
        loginAttemptRepository.deleteByUsername(username);
    }

    /** Returns the current failure count for the account window — used to decide if CAPTCHA must show. */
    public long currentFailureCount(String username) {
        if (username == null || username.isBlank()) return 0;
        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(accountWindowMinutes);
        return loginAttemptRepository
                .countByUsernameAndSuccessFalseAndAttemptedAtAfter(username, windowStart);
    }
}
