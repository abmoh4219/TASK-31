package com.meridian.retail.unit.service;

import com.meridian.retail.service.*;
import com.meridian.retail.security.*;

import com.meridian.retail.repository.LoginAttemptRepository;
import com.meridian.retail.security.AccountLockoutService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountLockoutServiceTest {

    @Mock LoginAttemptRepository repo;
    @InjectMocks AccountLockoutService service;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "accountMaxAttempts", 5);
        ReflectionTestUtils.setField(service, "accountWindowMinutes", 15);
        ReflectionTestUtils.setField(service, "ipMaxAttempts", 20);
        ReflectionTestUtils.setField(service, "ipWindowMinutes", 60);
    }

    @Test
    void accountLockedAtThreshold() {
        when(repo.countByUsernameAndSuccessFalseAndAttemptedAtAfter(eq("alice"), any(LocalDateTime.class)))
                .thenReturn(5L);
        assertThat(service.isAccountLocked("alice")).isTrue();
    }

    @Test
    void accountNotLockedBelowThreshold() {
        when(repo.countByUsernameAndSuccessFalseAndAttemptedAtAfter(eq("alice"), any(LocalDateTime.class)))
                .thenReturn(4L);
        assertThat(service.isAccountLocked("alice")).isFalse();
    }

    @Test
    void ipBlockedAtThreshold() {
        when(repo.countByIpAddressAndSuccessFalseAndAttemptedAtAfter(eq("10.0.0.1"), any(LocalDateTime.class)))
                .thenReturn(20L);
        assertThat(service.isIpBlocked("10.0.0.1")).isTrue();
    }

    @Test
    void blankUsernameNeverLocked() {
        assertThat(service.isAccountLocked(null)).isFalse();
        assertThat(service.isAccountLocked("")).isFalse();
        verifyNoInteractions(repo);
    }

    @Test
    void resetAttemptsDelegatesToRepo() {
        service.resetAttempts("alice");
        verify(repo).deleteByUsername("alice");
    }
}
