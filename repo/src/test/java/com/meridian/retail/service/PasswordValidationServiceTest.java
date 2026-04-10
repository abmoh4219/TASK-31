package com.meridian.retail.service;

import com.meridian.retail.security.PasswordComplexityException;
import com.meridian.retail.security.PasswordValidationService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordValidationServiceTest {

    private final PasswordValidationService svc = new PasswordValidationService();

    @Test
    void acceptsCompliantPassword() {
        assertDoesNotThrow(() -> svc.validate("Admin@Retail2024!"));
        assertTrue(svc.isValid("Admin@Retail2024!"));
    }

    @Test
    void rejectsTooShort() {
        assertThrows(PasswordComplexityException.class, () -> svc.validate("Aa1!aA"));
        assertFalse(svc.isValid("Aa1!aA"));
    }

    @Test
    void rejectsMissingUpper() {
        assertThrows(PasswordComplexityException.class, () -> svc.validate("alllowercase1!@"));
    }

    @Test
    void rejectsMissingLower() {
        assertThrows(PasswordComplexityException.class, () -> svc.validate("ALLUPPERCASE1!@"));
    }

    @Test
    void rejectsMissingDigit() {
        assertThrows(PasswordComplexityException.class, () -> svc.validate("NoDigitsHere!@"));
    }

    @Test
    void rejectsMissingSpecial() {
        assertThrows(PasswordComplexityException.class, () -> svc.validate("NoSpecial1234567"));
    }

    @Test
    void rejectsNull() {
        assertThrows(PasswordComplexityException.class, () -> svc.validate(null));
    }
}
