package com.meridian.retail.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "dual_approval_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DualApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "approval_queue_id", nullable = false)
    private Long approvalQueueId;

    @Column(name = "approver1_username", length = 100)
    private String approver1Username;

    @Column(name = "approver2_username", length = 100)
    private String approver2Username;

    @Column(name = "approver1_at")
    private LocalDateTime approver1At;

    @Column(name = "approver2_at")
    private LocalDateTime approver2At;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DualApprovalStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = DualApprovalStatus.PENDING;
    }
}
