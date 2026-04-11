package com.meridian.retail.unit.service;

import com.meridian.retail.service.*;
import com.meridian.retail.security.*;

import com.meridian.retail.audit.AuditLogService;
import com.meridian.retail.entity.DualApprovalRequest;
import com.meridian.retail.entity.DualApprovalStatus;
import com.meridian.retail.repository.DualApprovalRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DualApprovalServiceTest {

    @Mock DualApprovalRequestRepository repo;
    @Mock AuditLogService auditLogService;
    @InjectMocks DualApprovalService svc;

    @Test
    void rejectsWhenSecondApproverIsSameAsFirst() {
        DualApprovalRequest req = DualApprovalRequest.builder()
                .id(1L)
                .approver1Username("alice")
                .status(DualApprovalStatus.PENDING)
                .build();
        when(repo.findById(1L)).thenReturn(Optional.of(req));

        assertThatThrownBy(() -> svc.recordSecond(1L, "alice", "127.0.0.1"))
                .isInstanceOf(SameApproverException.class);
    }

    @Test
    void acceptsDifferentSecondApprover() {
        DualApprovalRequest req = DualApprovalRequest.builder()
                .id(2L)
                .approver1Username("alice")
                .status(DualApprovalStatus.PENDING)
                .build();
        when(repo.findById(2L)).thenReturn(Optional.of(req));
        when(repo.save(any(DualApprovalRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> svc.recordSecond(2L, "bob", "127.0.0.1"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsSecondWhenFirstNotRecorded() {
        DualApprovalRequest req = DualApprovalRequest.builder()
                .id(3L)
                .status(DualApprovalStatus.PENDING)
                .build();
        when(repo.findById(3L)).thenReturn(Optional.of(req));

        assertThatThrownBy(() -> svc.recordSecond(3L, "bob", "127.0.0.1"))
                .isInstanceOf(CampaignValidationException.class)
                .hasMessageContaining("First approver");
    }
}
