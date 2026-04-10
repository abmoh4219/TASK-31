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

    public List<ApprovalQueue> listPending() {
        return approvalQueueRepository.findByStatusOrderByCreatedAtAsc(ApprovalStatus.PENDING);
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
            boolean ready = dualApprovalService.isComplete(
                    dualApprovalService.initiate(q.getId()).getId()
            );
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
