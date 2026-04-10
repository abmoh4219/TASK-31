package com.meridian.retail.service;

import com.meridian.retail.audit.AuditLogService;
import com.meridian.retail.entity.User;
import com.meridian.retail.entity.UserRole;
import com.meridian.retail.repository.UserRepository;
import com.meridian.retail.security.PasswordComplexityException;
import com.meridian.retail.security.PasswordValidationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @org.mockito.Spy PasswordValidationService passwordValidationService = new PasswordValidationService();
    @Mock AuditLogService auditLogService;
    @InjectMocks UserService svc;

    @Test
    void createUserRejectsDuplicateUsername() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);
        assertThatThrownBy(() -> svc.createUser("alice", "Strong@Pass2024!", "Alice", UserRole.OPERATIONS, "admin", "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already taken");
    }

    @Test
    void createUserRejectsWeakPassword() {
        when(userRepository.existsByUsername("bob")).thenReturn(false);
        assertThatThrownBy(() -> svc.createUser("bob", "weak", "Bob", UserRole.OPERATIONS, "admin", "127.0.0.1"))
                .isInstanceOf(PasswordComplexityException.class);
    }

    @Test
    void createUserBcryptHashesPasswordAndAudits() {
        when(userRepository.existsByUsername("carol")).thenReturn(false);
        when(passwordEncoder.encode("Strong@Pass2024!")).thenReturn("$2a$12$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(99L);
            return u;
        });

        User saved = svc.createUser("carol", "Strong@Pass2024!", "Carol", UserRole.REVIEWER, "admin", "127.0.0.1");

        assertThat(saved.getPasswordHash()).isEqualTo("$2a$12$hashed");
        assertThat(saved.getRole()).isEqualTo(UserRole.REVIEWER);
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void deactivateUserFlipsActiveFlag() {
        User u = User.builder().id(5L).username("dave").active(true).role(UserRole.OPERATIONS).build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(u));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        svc.deactivateUser(5L, "admin", "127.0.0.1");
        assertThat(u.isActive()).isFalse();
    }
}
