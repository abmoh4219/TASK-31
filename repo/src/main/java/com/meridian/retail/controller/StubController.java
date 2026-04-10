package com.meridian.retail.controller;

import com.meridian.retail.entity.ApprovalStatus;
import com.meridian.retail.entity.CampaignStatus;
import com.meridian.retail.repository.ApprovalQueueRepository;
import com.meridian.retail.repository.CampaignRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Phase 2 stub landings for the role-specific routes that the auth success handler
 * redirects to. These will be replaced by full controllers in Phases 3-6 (Campaign,
 * Approval, Analytics, Content) — but we need them to exist NOW so the post-login flow
 * does not produce a 404 in the running app.
 *
 * Each method renders a role-specific dashboard template populated from the database.
 */
@Controller
@RequiredArgsConstructor
public class StubController {

    private final CampaignRepository campaignRepository;
    private final ApprovalQueueRepository approvalQueueRepository;

    @GetMapping("/campaigns")
    public String campaignsLanding(Model model) {
        model.addAttribute("breadcrumb", "Campaigns");
        model.addAttribute("campaigns", campaignRepository.findByDeletedAtIsNullOrderByCreatedAtDesc());
        return "dashboard/ops";
    }

    @GetMapping("/approval/queue")
    @PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
    public String approvalLanding(Model model) {
        model.addAttribute("breadcrumb", "Approval Queue");
        model.addAttribute("queueItems",
                approvalQueueRepository.findByStatusOrderByCreatedAtAsc(ApprovalStatus.PENDING));
        model.addAttribute("pendingCount",
                approvalQueueRepository.countByStatus(ApprovalStatus.PENDING));
        return "dashboard/reviewer";
    }

    @GetMapping("/analytics/dashboard")
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN','OPERATIONS')")
    public String analyticsLanding(Model model) {
        model.addAttribute("breadcrumb", "Analytics");
        model.addAttribute("activeCampaigns",
                campaignRepository.countByStatusAndDeletedAtIsNull(CampaignStatus.ACTIVE));
        return "dashboard/finance";
    }

    @GetMapping("/content")
    @PreAuthorize("hasAnyRole('OPERATIONS','REVIEWER','ADMIN')")
    public String contentLanding(Model model) {
        model.addAttribute("breadcrumb", "Content Integrity");
        return "dashboard/cs"; // reuse simple landing — replaced in Phase 5
    }

    @GetMapping("/upload")
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    public String uploadLanding(Model model) {
        model.addAttribute("breadcrumb", "Upload Files");
        return "dashboard/ops";
    }
}
