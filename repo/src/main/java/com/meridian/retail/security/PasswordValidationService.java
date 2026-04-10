package com.meridian.retail.security;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Password complexity validator per SPEC.md:
 *   - At least 12 characters
 *   - At least one uppercase letter
 *   - At least one lowercase letter
 *   - At least one digit
 *   - At least one special character
 *
 * Throws {@link PasswordComplexityException} with a clear message on failure.
 * Used by UserService when creating or changing passwords. The complexity rules are
 * deliberately simple, fast, and explicit so QA can verify them by reading source.
 */
@Service
public class PasswordValidationService {

    private static final int MIN_LENGTH = 12;
    private static final Pattern UPPER   = Pattern.compile("[A-Z]");
    private static final Pattern LOWER   = Pattern.compile("[a-z]");
    private static final Pattern DIGIT   = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL = Pattern.compile("[^A-Za-z0-9]");

    /**
     * Throws {@link PasswordComplexityException} unless the candidate satisfies every rule.
     * The error message lists the FULL set of rules so the operator knows exactly what is
     * required (we do NOT leak which specific rule failed — that would help an attacker
     * trying to enumerate complexity).
     */
    public void validate(String password) {
        if (password == null
                || password.length() < MIN_LENGTH
                || !UPPER.matcher(password).find()
                || !LOWER.matcher(password).find()
                || !DIGIT.matcher(password).find()
                || !SPECIAL.matcher(password).find()) {
            throw new PasswordComplexityException(
                "Password must be at least 12 characters with uppercase, lowercase, digit, and special character"
            );
        }
    }

    /** Convenience boolean variant for HTMX validation endpoints. */
    public boolean isValid(String password) {
        try {
            validate(password);
            return true;
        } catch (PasswordComplexityException e) {
            return false;
        }
    }
}
