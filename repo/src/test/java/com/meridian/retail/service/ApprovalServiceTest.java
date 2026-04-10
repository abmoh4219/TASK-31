package com.meridian.retail.service;

import com.meridian.retail.audit.AuditLogService;
import com.meridian.retail.entity.ApprovalQueue;
import com.meridian.retail.entity.ApprovalStatus;
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
}
