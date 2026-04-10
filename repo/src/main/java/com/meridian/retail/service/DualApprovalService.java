package com.meridian.retail.service;

import com.meridian.retail.audit.AuditAction;
import com.meridian.retail.audit.AuditLogService;
import com.meridian.retail.entity.DualApprovalRequest;
import com.meridian.retail.entity.DualApprovalStatus;
import com.meridian.retail.repository.DualApprovalRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Two-eyes approval workflow for HIGH-risk campaigns and permission changes.
 *
 * Rule: approver1 != approver2 (enforced when recordSecond is called).
 * If the same user attempts to be both approvers a SameApproverException is raised
 * — the service NEVER persists the second approval in that case.
 */
@Service
@RequiredArgsConstructor
public class DualApprovalService {

    private final DualApprovalRequestRepository repository;
    private final AuditLogService auditLogService;

    @Transactional
    public DualApprovalRequest initiate(Long approvalQueueId) {
        // If a request already exists for this queue entry, reuse it.
        return repository.findByApprovalQueueId(approvalQueueId).orElseGet(() ->
                repository.save(DualApprovalRequest.builder()
                        .approvalQueueId(approvalQueueId)
                        .status(DualApprovalStatus.PENDING)
                        .build())
        );
    }

    @Transactional
    public DualApprovalRequest recordFirst(Long requestId, String approverUsername, String ipAddress) {
        DualApprovalRequest req = repository.findById(requestId)
                .orElseThrow(() -> new CampaignValidationException("Dual approval request not found: " + requestId));
        if (req.getStatus() == DualApprovalStatus.COMPLETE) {
            throw new CampaignValidationException("Dual approval already complete");
        }
        req.setApprover1Username(approverUsername);
        req.setApprover1At(LocalDateTime.now());
        DualApprovalRequest saved = repository.save(req);
        auditLogService.log(AuditAction.DUAL_APPROVAL_FIRST, "DualApprovalRequest", saved.getId(),
                null, saved, approverUsername, ipAddress);
        return saved;
    }

    @Transactional
    public DualApprovalRequest recordSecond(Long requestId, String approverUsername, String ipAddress) {
        DualApprovalRequest req = repository.findById(requestId)
                .orElseThrow(() -> new CampaignValidationException("Dual approval request not found: " + requestId));
        if (req.getApprover1Username() == null) {
            throw new CampaignValidationException("First approver not yet recorded");
        }
        if (req.getApprover1Username().equalsIgnoreCase(approverUsername)) {
            // Two-eyes principle: same person cannot fulfil both halves.
            throw new SameApproverException("Approver1 and approver2 must be different users");
        }
        req.setApprover2Username(approverUsername);
        req.setApprover2At(LocalDateTime.now());
        req.setStatus(DualApprovalStatus.COMPLETE);
        DualApprovalRequest saved = repository.save(req);
        auditLogService.log(AuditAction.DUAL_APPROVAL_SECOND, "DualApprovalRequest", saved.getId(),
                null, saved, approverUsername, ipAddress);
        return saved;
    }

    public boolean isComplete(Long requestId) {
        return repository.findById(requestId)
                .map(r -> r.getStatus() == DualApprovalStatus.COMPLETE)
                .orElse(false);
    }
}
