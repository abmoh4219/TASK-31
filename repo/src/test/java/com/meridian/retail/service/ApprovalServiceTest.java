package com.meridian.retail.service;

import com.meridian.retail.audit.AuditLogService;
import com.meridian.retail.entity.ApprovalQueue;
import com.meridian.retail.entity.ApprovalStatus;
import com.meridian.retail.entity.Campaign;
import com.meridian.retail.entity.CampaignStatus;
import com.meridian.retail.entity.DualApprovalRequest;
import com.meridian.retail.entity.DualApprovalStatus;
import com.meridian.retail.entity.RiskLevel;
import com.meridian.retail.repository.ApprovalQueueRepository;
import com.meridian.retail.repository.CampaignRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

    @Mock ApprovalQueueRepository approvalQueueRepository;
    @Mock CampaignRepository campaignRepository;
    @Mock DualApprovalService dualApprovalService;
    @Mock AuditLogService auditLogService;
    @Mock CouponService couponService;
    @InjectMocks ApprovalService svc;

    @Test
    void reviewerCannotApproveOwnSubmission() {
        ApprovalQueue q = ApprovalQueue.builder()
                .id(10L)
                .campaignId(1L)
                .requestedBy("alice")
                .status(ApprovalStatus.PENDING)
                .riskLevel(RiskLevel.LOW)
                .build();
        when(approvalQueueRepository.findById(10L)).thenReturn(Optional.of(q));

        // Same user tries to approve -> SameApproverException
        assertThatThrownBy(() -> svc.approve(10L, "alice", "looks ok", "127.0.0.1"))
                .isInstanceOf(SameApproverException.class)
                .hasMessageContaining("themselves submitted");
    }

    @Test
    void reviewerCannotRejectOwnSubmission() {
        ApprovalQueue q = ApprovalQueue.builder()
                .id(11L).campaignId(1L)
                .requestedBy("bob").status(ApprovalStatus.PENDING).riskLevel(RiskLevel.LOW)
                .build();
        when(approvalQueueRepository.findById(11L)).thenReturn(Optional.of(q));

        assertThatThrownBy(() -> svc.reject(11L, "bob", "no", "127.0.0.1"))
                .isInstanceOf(SameApproverException.class);
    }

    @Test
    void unknownQueueIdThrowsValidation() {
        when(approvalQueueRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc.approve(99L, "alice", "ok", "127.0.0.1"))
                .isInstanceOf(CampaignValidationException.class)
                .hasMessageContaining("not found");
    }

    // ---------- Dual-approval lifecycle ----------

    @Test
    void recordFirstApprovalRejectsLowRisk() {
        ApprovalQueue q = ApprovalQueue.builder()
                .id(20L).campaignId(1L).requestedBy("alice")
                .status(ApprovalStatus.PENDING).riskLevel(RiskLevel.LOW).build();
        when(approvalQueueRepository.findById(20L)).thenReturn(Optional.of(q));

        assertThatThrownBy(() -> svc.recordFirstApproval(20L, "bob", "127.0.0.1"))
                .isInstanceOf(CampaignValidationException.class)
                .hasMessageContaining("HIGH");
    }

    @Test
    void recordFirstApprovalRejectsSubmitter() {
        ApprovalQueue q = ApprovalQueue.builder()
                .id(21L).campaignId(1L).requestedBy("alice")
                .status(ApprovalStatus.REQUIRES_DUAL).riskLevel(RiskLevel.HIGH).build();
        when(approvalQueueRepository.findById(21L)).thenReturn(Optional.of(q));

        assertThatThrownBy(() -> svc.recordFirstApproval(21L, "alice", "127.0.0.1"))
                .isInstanceOf(SameApproverException.class);
    }

    @Test
    void recordSecondApprovalFullLifecycle() {
        ApprovalQueue q = ApprovalQueue.builder()
                .id(22L).campaignId(5L).requestedBy("alice")
                .status(ApprovalStatus.REQUIRES_DUAL).riskLevel(RiskLevel.HIGH).build();
        when(approvalQueueRepository.findById(22L)).thenReturn(Optional.of(q));

        DualApprovalRequest dual = DualApprovalRequest.builder()
                .id(100L).approvalQueueId(22L).approver1Username("bob")
                .status(DualApprovalStatus.COMPLETE).build();
        when(dualApprovalService.recordSecondForQueue(22L, "carol", "127.0.0.1")).thenReturn(dual);

        Campaign c = Campaign.builder().id(5L).status(CampaignStatus.PENDING_REVIEW).build();
        when(campaignRepository.findById(5L)).thenReturn(Optional.of(c));
        when(approvalQueueRepository.save(any(ApprovalQueue.class))).thenAnswer(inv -> inv.getArgument(0));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));

        ApprovalQueue saved = svc.recordSecondApproval(22L, "carol", "looks good", "127.0.0.1");

        org.assertj.core.api.Assertions.assertThat(saved.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        org.assertj.core.api.Assertions.assertThat(c.getStatus()).isEqualTo(CampaignStatus.APPROVED);
    }
}
