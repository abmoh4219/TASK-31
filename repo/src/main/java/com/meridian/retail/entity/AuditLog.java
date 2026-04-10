package com.meridian.retail.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Immutable audit record. There is intentionally NO @PreUpdate hook and NO updated_at column
 * — these records are written once and never modified. The repository must not expose any
 * update operation.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
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
