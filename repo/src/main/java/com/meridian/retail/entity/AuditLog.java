package com.meridian.retail.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Immutable audit record. Three layers of defence:
 *   1. Java layer — no {@code @Setter}; fields only settable via {@code @Builder} at
 *      construction time.
 *   2. Spring Data layer — {@code AuditLogRepository} extends {@code Repository} (not
 *      {@code JpaRepository}) and exposes only insert + read operations.
 *   3. Database layer — V14 installs MySQL triggers that reject UPDATE and DELETE on
 *      the {@code audit_logs} table with SQLSTATE 45000.
 * No {@code @PreUpdate} hook and no {@code updated_at} column.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", length = 100)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "operator_username", length = 100)
    private String operatorUsername;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "before_state", columnDefinition = "LONGTEXT")
    private String beforeState;

    @Column(name = "after_state", columnDefinition = "LONGTEXT")
    private String afterState;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
