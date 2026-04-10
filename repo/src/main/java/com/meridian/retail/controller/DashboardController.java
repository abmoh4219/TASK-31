package com.meridian.retail.controller;

import com.meridian.retail.entity.ApprovalStatus;
import com.meridian.retail.entity.CampaignStatus;
import com.meridian.retail.repository.AnomalyAlertRepository;
import com.meridian.retail.repository.ApprovalQueueRepository;
import com.meridian.retail.repository.BackupRecordRepository;
import com.meridian.retail.repository.CampaignRepository;
import com.meridian.retail.repository.ContentItemRepository;
import com.meridian.retail.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Role-aware dashboard router. Each role has its own landing page populated entirely from
 * the database via repositories — there is NO hardcoded data in any template.
 *
 * The /dashboard endpoint inspects the current authentication and forwards the user to
 * their primary dashboard. The role-specific endpoints below are also reachable directly
 * from the sidebar.
 */
@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final CampaignRepository campaignRepository;
    private final ApprovalQueueRepository approvalQueueRepository;
    private final UserRepository userRepository;
    private final AnomalyAlertRepository anomalyAlertRepository;
    private final BackupRecordRepository backupRecordRepository;
    private final ContentItemRepository contentItemRepository;

    /** Generic post-login router — forwards to the per-role landing page. */
    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication) {
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_USER");
        return switch (role) {
            case "ROLE_ADMIN"            -> "redirect:/admin/dashboard";
            case "ROLE_REVIEWER"         -> "redirect:/approval/queue";
            case "ROLE_FINANCE"          -> "redirect:/analytics/dashboard";
            case "ROLE_OPERATIONS"       -> "redirect:/campaigns";
            case "ROLE_CUSTOMER_SERVICE" -> "redirect:/campaigns";
            default                      -> "redirect:/login";
        };
    }

    /** ADMIN dashboard — full operational summary, populated from real DB queries. */
    @GetMapping("/admin/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminDashboard(Model model) {
        model.addAttribute("breadcrumb", "Admin Dashboard");
        model.addAttribute("totalUsers", userRepository.count());
        model.addAttribute("activeUsers", userRepository.countByActiveTrue());
        model.addAttribute("activeCampaigns", campaignRepository.countByStatusAndDeletedAtIsNull(CampaignStatus.ACTIVE));
        model.addAttribute("draftCampaigns", campaignRepository.countByStatusAndDeletedAtIsNull(CampaignStatus.DRAFT));
        model.addAttribute("pendingApprovals", approvalQueueRepository.countByStatus(ApprovalStatus.PENDING));
        model.addAttribute("unacknowledgedAlerts", anomalyAlertRepository.countByAcknowledgedAtIsNull());
        model.addAttribute("contentItemCount", contentItemRepository.count());
        model.addAttribute("lastBackup",
                backupRecordRepository.findTop1ByStatusOrderByCreatedAtDesc(com.meridian.retail.entity.BackupStatus.COMPLETE)
                        .orElse(null));
        return "dashboard/admin";
    }
}
