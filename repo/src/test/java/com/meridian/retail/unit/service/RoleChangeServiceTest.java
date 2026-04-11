package com.meridian.retail.unit.service;

import com.meridian.retail.service.*;
import com.meridian.retail.security.*;

import com.meridian.retail.audit.AuditLogService;
import com.meridian.retail.entity.RoleChangeRequest;
import com.meridian.retail.entity.RoleChangeRequest.RoleChangeStatus;
import com.meridian.retail.entity.User;
import com.meridian.retail.entity.UserRole;
import com.meridian.retail.repository.RoleChangeRequestRepository;
import com.meridian.retail.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Covers the two-eyes invariant for user role changes:
 *   - requester ≠ first approver
 *   - requester ≠ second approver
 *   - first approver ≠ second approver
 * and that the target user's role is only applied on the second approval.
 */
@ExtendWith(MockitoExtension.class)
class RoleChangeServiceTest {

    @Mock RoleChangeRequestRepository repo;
    @Mock UserRepository userRepository;
    @Mock AuditLogService auditLogService;
    @InjectMocks RoleChangeService svc;

    private User targetUser() {
        return User.builder().id(42L).username("target").role(UserRole.OPERATIONS).build();
    }

    @Test
    void firstApprovalCannotBeTheRequester() {
        RoleChangeRequest req = RoleChangeRequest.builder()
                .id(1L).targetUserId(42L).targetUsername("target")
                .oldRole(UserRole.OPERATIONS).newRole(UserRole.ADMIN)
                .requestedBy("alice").status(RoleChangeStatus.PENDING).build();
        when(repo.findById(1L)).thenReturn(Optional.of(req));

        assertThatThrownBy(() -> svc.recordFirstApproval(1L, "alice", "127.0.0.1"))
                .isInstanceOf(SameApproverException.class);
    }

    @Test
    void secondApprovalCannotBeTheFirstApprover() {
        RoleChangeRequest req = RoleChangeRequest.builder()
                .id(2L).targetUserId(42L).targetUsername("target")
                .oldRole(UserRole.OPERATIONS).newRole(UserRole.ADMIN)
                .requestedBy("alice").approver1Username("bob")
                .status(RoleChangeStatus.FIRST_APPROVED).build();
        when(repo.findById(2L)).thenReturn(Optional.of(req));

        assertThatThrownBy(() -> svc.recordSecondApproval(2L, "bob", "127.0.0.1"))
                .isInstanceOf(SameApproverException.class);
    }

    @Test
    void secondApprovalCannotBeTheRequester() {
        RoleChangeRequest req = RoleChangeRequest.builder()
                .id(3L).targetUserId(42L).targetUsername("target")
                .oldRole(UserRole.OPERATIONS).newRole(UserRole.ADMIN)
                .requestedBy("alice").approver1Username("bob")
                .status(RoleChangeStatus.FIRST_APPROVED).build();
        when(repo.findById(3L)).thenReturn(Optional.of(req));

        assertThatThrownBy(() -> svc.recordSecondApproval(3L, "alice", "127.0.0.1"))
                .isInstanceOf(SameApproverException.class);
    }

    @Test
    void secondApprovalAppliesRoleChangeOnSuccess() {
        RoleChangeRequest req = RoleChangeRequest.builder()
                .id(4L).targetUserId(42L).targetUsername("target")
                .oldRole(UserRole.OPERATIONS).newRole(UserRole.ADMIN)
                .requestedBy("alice").approver1Username("bob")
                .status(RoleChangeStatus.FIRST_APPROVED).build();
        User target = targetUser();
        when(repo.findById(4L)).thenReturn(Optional.of(req));
        when(userRepository.findById(42L)).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repo.save(any(RoleChangeRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        RoleChangeRequest result = svc.recordSecondApproval(4L, "carol", "127.0.0.1");

        assertThat(target.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(result.getStatus()).isEqualTo(RoleChangeStatus.APPLIED);
        assertThat(result.getApprover2Username()).isEqualTo("carol");
    }

    @Test
    void firstApprovalMustBePending() {
        RoleChangeRequest req = RoleChangeRequest.builder()
                .id(5L).requestedBy("alice").status(RoleChangeStatus.FIRST_APPROVED).build();
        when(repo.findById(5L)).thenReturn(Optional.of(req));

        assertThatThrownBy(() -> svc.recordFirstApproval(5L, "bob", "127.0.0.1"))
                .isInstanceOf(CampaignValidationException.class);
    }

    @Test
    void secondApprovalRequiresFirstApproved() {
        RoleChangeRequest req = RoleChangeRequest.builder()
                .id(6L).requestedBy("alice").status(RoleChangeStatus.PENDING).build();
        when(repo.findById(6L)).thenReturn(Optional.of(req));

        assertThatThrownBy(() -> svc.recordSecondApproval(6L, "bob", "127.0.0.1"))
                .isInstanceOf(CampaignValidationException.class);
    }
}
