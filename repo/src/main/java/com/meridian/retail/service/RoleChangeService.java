package com.meridian.retail.service;

import com.meridian.retail.audit.AuditAction;
import com.meridian.retail.audit.AuditLogService;
import com.meridian.retail.entity.RoleChangeRequest;
import com.meridian.retail.entity.RoleChangeRequest.RoleChangeStatus;
import com.meridian.retail.entity.User;
import com.meridian.retail.entity.UserRole;
import com.meridian.retail.repository.RoleChangeRequestRepository;
import com.meridian.retail.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Dual-approval workflow for user role changes.
 *
 * Two-eyes invariant (checked here, not in the controller):
 *   - requestedBy ≠ approver1 ≠ approver2
 *
 * Applying the role change happens in a single transaction with the second approval
 * so a partial failure cannot leave the target user in a halfway state.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoleChangeService {

    private final RoleChangeRequestRepository repository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public List<RoleChangeRequest> listPending() {
        return repository.findByStatusInOrderByRequestedAtDesc(
                List.of(RoleChangeStatus.PENDING, RoleChangeStatus.FIRST_APPROVED));
    }

    public List<RoleChangeRequest> listAll() {
        return repository.findAllByOrderByRequestedAtDesc();
    }

    public RoleChangeRequest findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new CampaignValidationException("Role change request not found: " + id));
    }

    /**
     * Create a pending role-change request. Does NOT mutate the target user — the role
     * only changes once the request transitions to APPLIED.
     */
    @Transactional
    public RoleChangeRequest request(Long targetUserId, UserRole newRole, String requestedBy, String ipAddress) {
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + targetUserId));
        if (target.getRole() == newRole) {
            throw new CampaignValidationException("User already has role " + newRole);
        }
        RoleChangeRequest req = RoleChangeRequest.builder()
                .targetUserId(target.getId())
                .targetUsername(target.getUsername())
                .oldRole(target.getRole())
                .newRole(newRole)
                .requestedBy(requestedBy)
                .status(RoleChangeStatus.PENDING)
                .build();
        RoleChangeRequest saved = repository.save(req);
        auditLogService.log(AuditAction.APPROVAL_SUBMITTED, "RoleChangeRequest", saved.getId(),
                null,
                Map.of("targetUser", target.getUsername(),
                       "oldRole", target.getRole().name(),
                       "newRole", newRole.name()),
                requestedBy, ipAddress);
        return saved;
    }

    /** First approver step. Must differ from the original requester. */
    @Transactional
    public RoleChangeRequest recordFirstApproval(Long requestId, String approver, String ipAddress) {
        RoleChangeRequest req = findById(requestId);
        if (req.getStatus() != RoleChangeStatus.PENDING) {
            throw new CampaignValidationException("First approval only valid on PENDING requests");
        }
        if (req.getRequestedBy().equalsIgnoreCase(approver)) {
            throw new SameApproverException("The requester cannot also be the first approver");
        }
        req.setApprover1Username(approver);
        req.setApprover1At(LocalDateTime.now());
        req.setStatus(RoleChangeStatus.FIRST_APPROVED);
        RoleChangeRequest saved = repository.save(req);
        auditLogService.log(AuditAction.DUAL_APPROVAL_FIRST, "RoleChangeRequest", saved.getId(),
                null, Map.of("approver1", approver), approver, ipAddress);
        return saved;
    }

    /**
     * Second approver step. Must differ from BOTH the requester AND the first approver.
     * On success, applies the new role to the target user and flips status to APPLIED.
     */
    @Transactional
    public RoleChangeRequest recordSecondApproval(Long requestId, String approver, String ipAddress) {
        RoleChangeRequest req = findById(requestId);
        if (req.getStatus() != RoleChangeStatus.FIRST_APPROVED) {
            throw new CampaignValidationException("Second approval requires the request to be FIRST_APPROVED");
        }
        if (req.getRequestedBy().equalsIgnoreCase(approver)) {
            throw new SameApproverException("The requester cannot also be the second approver");
        }
        if (req.getApprover1Username() != null
                && req.getApprover1Username().equalsIgnoreCase(approver)) {
            throw new SameApproverException("Approver1 and approver2 must be different users");
        }

        // Apply the role change atomically with the approval.
        User target = userRepository.findById(req.getTargetUserId())
                .orElseThrow(() -> new IllegalArgumentException("Target user vanished: " + req.getTargetUserId()));
        UserRole oldRole = target.getRole();
        target.setRole(req.getNewRole());
        userRepository.save(target);

        req.setApprover2Username(approver);
        req.setApprover2At(LocalDateTime.now());
        req.setStatus(RoleChangeStatus.APPLIED);
        req.setAppliedAt(LocalDateTime.now());
        RoleChangeRequest saved = repository.save(req);

        auditLogService.log(AuditAction.DUAL_APPROVAL_SECOND, "RoleChangeRequest", saved.getId(),
                null, Map.of("approver2", approver), approver, ipAddress);
        auditLogService.log(AuditAction.USER_ROLE_CHANGED, "User", target.getId(),
                Map.of("role", oldRole.name()), Map.of("role", target.getRole().name()),
                approver, ipAddress);
        return saved;
    }

    @Transactional
    public RoleChangeRequest reject(Long requestId, String actor, String ipAddress) {
        RoleChangeRequest req = findById(requestId);
        if (req.getStatus() == RoleChangeStatus.APPLIED) {
            throw new CampaignValidationException("Cannot reject an already-applied role change");
        }
        req.setStatus(RoleChangeStatus.REJECTED);
        RoleChangeRequest saved = repository.save(req);
        auditLogService.log(AuditAction.APPROVAL_REJECTED, "RoleChangeRequest", saved.getId(),
                null, Map.of("rejectedBy", actor), actor, ipAddress);
        return saved;
    }
}
