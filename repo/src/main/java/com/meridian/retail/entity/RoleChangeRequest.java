package com.meridian.retail.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Dual-approval record for a user role change.
 *
 * Lifecycle: PENDING -> FIRST_APPROVED -> APPLIED (or REJECTED at any step).
 * The two-eyes rule is enforced in RoleChangeService: requestedBy, approver1, approver2
 * must all be distinct admin users. The change is APPLIED only when status flips to
 * APPLIED, which also updates the target user's role in a single transaction.
 */
@Entity
@Table(name = "role_change_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;

    @Column(name = "target_username", nullable = false, length = 100)
    private String targetUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_role", nullable = false)
    private UserRole oldRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_role", nullable = false)
    private UserRole newRole;

    @Column(name = "requested_by", nullable = false, length = 100)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "approver1_username", length = 100)
    private String approver1Username;

    @Column(name = "approver1_at")
    private LocalDateTime approver1At;

    @Column(name = "approver2_username", length = 100)
    private String approver2Username;

    @Column(name = "approver2_at")
    private LocalDateTime approver2At;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RoleChangeStatus status;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @PrePersist
    void onCreate() {
        if (requestedAt == null) requestedAt = LocalDateTime.now();
        if (status == null) status = RoleChangeStatus.PENDING;
    }

    public enum RoleChangeStatus {
        PENDING, FIRST_APPROVED, APPLIED, REJECTED
    }
}
