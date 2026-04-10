package com.meridian.retail.service;

import com.meridian.retail.audit.AuditAction;
import com.meridian.retail.audit.AuditLogService;
import com.meridian.retail.entity.User;
import com.meridian.retail.entity.UserRole;
import com.meridian.retail.repository.UserRepository;
import com.meridian.retail.security.PasswordValidationService;
import com.meridian.retail.security.XssInputSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * User management service. ALL mutations:
 *   - run XssInputSanitizer on string input
 *   - validate password complexity (12+ chars, upper/lower/digit/special)
 *   - persist BCrypt hashes (strength 12, matching seed data)
 *   - emit AuditLogService.log(...) with before/after snapshots
 *
 * Only ADMIN should ever invoke these methods — enforced by AdminController class-level
 * @PreAuthorize. The service itself does not re-check the role because the call site is
 * narrow and centralised; if that changes, add @PreAuthorize here too.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordValidationService passwordValidationService;
    private final AuditLogService auditLogService;

    public List<User> listAll() {
        return userRepository.findAll();
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }

    public boolean isUsernameAvailable(String username) {
        if (username == null || username.isBlank()) return false;
        return !userRepository.existsByUsername(username);
    }

    @Transactional
    public User createUser(String username, String password, String fullName, UserRole role,
                           String operatorUsername, String ipAddress) {
        username = XssInputSanitizer.sanitize(username);
        fullName = XssInputSanitizer.sanitize(fullName);
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already taken: " + username);
        }
        passwordValidationService.validate(password);

        User u = User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(password))
                .fullName(fullName)
                .role(role)
                .active(true)
                .build();
        User saved = userRepository.save(u);

        // AUDIT: never log the password — only username + role.
        auditLogService.log(AuditAction.USER_CREATED, "User", saved.getId(),
                null, Map.of("username", saved.getUsername(), "role", saved.getRole().name()),
                operatorUsername, ipAddress);
        return saved;
    }

    @Transactional
    public User updateUser(Long id, String fullName, UserRole role,
                           String operatorUsername, String ipAddress) {
        User existing = findById(id);
        Map<String, Object> before = Map.of(
                "fullName", String.valueOf(existing.getFullName()),
                "role", existing.getRole().name()
        );

        boolean roleChanged = existing.getRole() != role;

        existing.setFullName(XssInputSanitizer.sanitize(fullName));
        existing.setRole(role);
        User saved = userRepository.save(existing);

        Map<String, Object> after = Map.of(
                "fullName", String.valueOf(saved.getFullName()),
                "role", saved.getRole().name()
        );

        auditLogService.log(AuditAction.USER_UPDATED, "User", saved.getId(),
                before, after, operatorUsername, ipAddress);
        // If the role specifically changed, emit a second high-signal entry.
        if (roleChanged) {
            auditLogService.log(AuditAction.USER_ROLE_CHANGED, "User", saved.getId(),
                    Map.of("role", before.get("role")), Map.of("role", after.get("role")),
                    operatorUsername, ipAddress);
        }
        return saved;
    }

    @Transactional
    public void deactivateUser(Long id, String operatorUsername, String ipAddress) {
        User existing = findById(id);
        existing.setActive(false);
        userRepository.save(existing);
        auditLogService.log(AuditAction.USER_DEACTIVATED, "User", id,
                Map.of("active", true), Map.of("active", false),
                operatorUsername, ipAddress);
    }
}
