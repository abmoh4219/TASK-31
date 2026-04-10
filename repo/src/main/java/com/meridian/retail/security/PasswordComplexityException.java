package com.meridian.retail.security;

/** Thrown by PasswordValidationService when a candidate password fails complexity rules. */
public class PasswordComplexityException extends RuntimeException {
    public PasswordComplexityException(String message) {
        super(message);
    }
}
