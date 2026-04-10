package com.meridian.retail.controller;

import com.meridian.retail.entity.CampaignStatus;
import com.meridian.retail.repository.CampaignRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Phase 2/3 stub landings for routes whose full controller does not yet exist
 * (replaced module-by-module in Phases 4-6: file upload, content integrity, analytics).
 *
 * /campaigns and /approval/queue have moved to their real controllers and are no
 * longer served from here.
 */
@Controller
@RequiredArgsConstructor
public class StubController {

    private final CampaignRepository campaignRepository;

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
        return "dashboard/cs"; // replaced in Phase 5
    }

    @GetMapping("/upload")
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    public String uploadLanding() {
        return "redirect:/files/upload";
    }
}
