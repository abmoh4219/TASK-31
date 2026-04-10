package com.meridian.retail.service;

import com.meridian.retail.audit.AuditAction;
import com.meridian.retail.audit.AuditLogService;
import com.meridian.retail.entity.ApprovalQueue;
import com.meridian.retail.entity.ApprovalStatus;
import com.meridian.retail.entity.Campaign;
import com.meridian.retail.entity.CampaignStatus;
import com.meridian.retail.entity.RiskLevel;
import com.meridian.retail.repository.ApprovalQueueRepository;
import com.meridian.retail.repository.CampaignRepository;
import com.meridian.retail.security.XssInputSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Approval queue lifecycle: submit -> assign -> approve|reject. HIGH risk items
 * require two-eyes via DualApprovalService — see {@link #approve(Long, String, String, String)}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalService {

    private final ApprovalQueueRepository approvalQueueRepository;
    private final CampaignRepository campaignRepository;
    private final DualApprovalService dualApprovalService;
    private final AuditLogService auditLogService;

    /**
     * Queue view for reviewers: both single-approval PENDING items AND HIGH-risk items
     * still waiting for dual sign-off (REQUIRES_DUAL). If we filtered on PENDING only,
     * every HIGH-risk campaign would disappear from the UI the moment it was submitted
     * and the dual-approval workflow would be unreachable.
     */
    public List<ApprovalQueue> listPending() {
        return approvalQueueRepository.findByStatusInOrderByCreatedAtAsc(
                List.of(ApprovalStatus.PENDING, ApprovalStatus.REQUIRES_DUAL));
    }

    /** Look up the dual-approval row that backs a queue entry (may not exist for LOW/MED items). */
    public Optional<com.meridian.retail.entity.DualApprovalRequest> findDualRequest(Long queueId) {
        return dualApprovalService.findByQueueId(queueId);
    }

    public ApprovalQueue findById(Long id) {
        return approvalQueueRepository.findById(id)
                .orElseThrow(() -> new CampaignValidationException("Approval queue entry not found: " + id));
    }

    @Transactional
    public ApprovalQueue submitToQueue(Long campaignId, String requestedBy, RiskLevel riskLevel, String ipAddress) {
        Campaign c = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new CampaignValidationException("Campaign not found: " + campaignId));
        ApprovalQueue q = ApprovalQueue.builder()
                .campaignId(c.getId())
                .requestedBy(requestedBy)
                .status(riskLevel == RiskLevel.HIGH ? ApprovalStatus.REQUIRES_DUAL : ApprovalStatus.PENDING)
                .riskLevel(riskLevel)
                .build();
        ApprovalQueue saved = approvalQueueRepository.save(q);
        if (riskLevel == RiskLevel.HIGH) {
            dualApprovalService.initiate(saved.getId());
        }
        auditLogService.log(AuditAction.APPROVAL_SUBMITTED, "ApprovalQueue", saved.getId(),
                null, saved, requestedBy, ipAddress);
        return saved;
    }

    /**
     * Record the FIRST of two required approvals for a HIGH-risk queue entry. The row
     * remains in REQUIRES_DUAL — it only flips to APPROVED once recordSecondApproval()
     * runs with a DIFFERENT user.
     */
    @Transactional
    public ApprovalQueue recordFirstApproval(Long queueId, String reviewerUsername, String ipAddress) {
        ApprovalQueue q = findById(queueId);
        if (q.getRiskLevel() != RiskLevel.HIGH) {
            throw new CampaignValidationException("First-approval step only applies to HIGH risk items");
        }
        if (q.getRequestedBy() != null && q.getRequestedBy().equalsIgnoreCase(reviewerUsername)) {
            throw new SameApproverException("A reviewer cannot approve a campaign they themselves submitted");
        }
        dualApprovalService.recordFirstForQueue(q.getId(), reviewerUsername, ipAddress);
        q.setStatus(ApprovalStatus.REQUIRES_DUAL);
        q.setAssignedReviewer(reviewerUsername);
        return approvalQueueRepository.save(q);
    }

    /**
     * Record the SECOND approval and, when successful, finalize the queue entry to APPROVED
     * and cascade to the underlying campaign. Throws SameApproverException if the same user
     * attempts to fulfil both halves.
     */
    @Transactional
    public ApprovalQueue recordSecondApproval(Long queueId, String reviewerUsername, String notes, String ipAddress) {
        ApprovalQueue q = findById(queueId);
        if (q.getRiskLevel() != RiskLevel.HIGH) {
            throw new CampaignValidationException("Second-approval step only applies to HIGH risk items");
        }
        if (q.getRequestedBy() != null && q.getRequestedBy().equalsIgnoreCase(reviewerUsername)) {
            throw new SameApproverException("A reviewer cannot approve a campaign they themselves submitted");
        }
        // This will throw SameApproverException if the two approvers match (two-eyes rule)
        // and CampaignValidationException if the first approval has not yet been recorded.
        dualApprovalService.recordSecondForQueue(q.getId(), reviewerUsername, ipAddress);

        q.setStatus(ApprovalStatus.APPROVED);
        q.setNotes(XssInputSanitizer.sanitize(notes));
        q.setAssignedReviewer(reviewerUsername);
        ApprovalQueue saved = approvalQueueRepository.save(q);

        campaignRepository.findById(q.getCampaignId()).ifPresent(c -> {
            c.setStatus(CampaignStatus.APPROVED);
            campaignRepository.save(c);
        });

        auditLogService.log(AuditAction.APPROVAL_APPROVED, "ApprovalQueue", saved.getId(),
                null, saved, reviewerUsername, ipAddress);
        return saved;
    }

    @Transactional
    public ApprovalQueue assign(Long queueId, String reviewerUsername) {
        ApprovalQueue q = findById(queueId);
        q.setAssignedReviewer(reviewerUsername);
        return approvalQueueRepository.save(q);
    }

    /**
     * Approve a pending entry.
     *
     * Object-level rule: a reviewer cannot approve their own submission. We compare
     * requestedBy vs reviewerUsername case-insensitively and throw SameApproverException
     * if they match. This is enforced HERE in the service, not in the controller, so the
     * rule is honoured no matter what UI calls this method.
     */
    @Transactional
    public ApprovalQueue approve(Long queueId, String reviewerUsername, String notes, String ipAddress) {
        ApprovalQueue q = findById(queueId);

        if (q.getRequestedBy() != null && q.getRequestedBy().equalsIgnoreCase(reviewerUsername)) {
            throw new SameApproverException("A reviewer cannot approve a campaign they themselves submitted");
        }

        if (q.getRiskLevel() == RiskLevel.HIGH) {
            // For HIGH risk we require dual approval to be COMPLETE before flipping the status.
            // IMPORTANT: a single reviewer hitting "approve" must NEVER satisfy this —
            // we only consult the persisted dual-approval row, which tracks two distinct users.
            com.meridian.retail.entity.DualApprovalRequest dual =
                    dualApprovalService.initiate(q.getId());
            boolean ready = dualApprovalService.isComplete(dual.getId());
            if (!ready) {
                log.info("HIGH risk approval still requires dual sign-off: queueId={}", queueId);
                q.setStatus(ApprovalStatus.REQUIRES_DUAL);
                q.setNotes(XssInputSanitizer.sanitize(notes));
                q.setAssignedReviewer(reviewerUsername);
                approvalQueueRepository.save(q);
                return q;
            }
        }

        q.setStatus(ApprovalStatus.APPROVED);
        q.setNotes(XssInputSanitizer.sanitize(notes));
        q.setAssignedReviewer(reviewerUsername);
        ApprovalQueue saved = approvalQueueRepository.save(q);

        // Cascade: bump the campaign's status to APPROVED so it can later be published.
        campaignRepository.findById(q.getCampaignId()).ifPresent(c -> {
            c.setStatus(CampaignStatus.APPROVED);
            campaignRepository.save(c);
        });

        auditLogService.log(AuditAction.APPROVAL_APPROVED, "ApprovalQueue", saved.getId(),
                null, saved, reviewerUsername, ipAddress);
        return saved;
    }

    @Transactional
    public ApprovalQueue reject(Long queueId, String reviewerUsername, String notes, String ipAddress) {
        ApprovalQueue q = findById(queueId);

        if (q.getRequestedBy() != null && q.getRequestedBy().equalsIgnoreCase(reviewerUsername)) {
            throw new SameApproverException("A reviewer cannot reject a campaign they themselves submitted");
        }

        q.setStatus(ApprovalStatus.REJECTED);
        q.setNotes(XssInputSanitizer.sanitize(notes));
        q.setAssignedReviewer(reviewerUsername);
        ApprovalQueue saved = approvalQueueRepository.save(q);

        campaignRepository.findById(q.getCampaignId()).ifPresent(c -> {
            c.setStatus(CampaignStatus.REJECTED);
            campaignRepository.save(c);
        });

        auditLogService.log(AuditAction.APPROVAL_REJECTED, "ApprovalQueue", saved.getId(),
                null, saved, reviewerUsername, ipAddress);
        return saved;
    }
}
