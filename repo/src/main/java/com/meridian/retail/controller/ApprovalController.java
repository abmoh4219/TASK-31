package com.meridian.retail.controller;

import com.meridian.retail.service.ApprovalService;
import com.meridian.retail.service.CampaignValidationException;
import com.meridian.retail.service.DualApprovalService;
import com.meridian.retail.service.SameApproverException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/approval")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
public class ApprovalController {

    private final ApprovalService approvalService;
    private final DualApprovalService dualApprovalService;

    @GetMapping("/queue")
    public String queue(Model model) {
        model.addAttribute("breadcrumb", "Approval Queue");
        model.addAttribute("queueItems", approvalService.listPending());
        return "approval/queue";
    }

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Long id,
                          @RequestParam(required = false) String notes,
                          Authentication authentication,
                          HttpServletRequest httpRequest,
                          RedirectAttributes redirect) {
        try {
            approvalService.approve(id, authentication.getName(), notes, clientIp(httpRequest));
            redirect.addFlashAttribute("successMessage", "Approval recorded.");
        } catch (SameApproverException | CampaignValidationException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/approval/queue";
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id,
                         @RequestParam(required = false) String notes,
                         Authentication authentication,
                         HttpServletRequest httpRequest,
                         RedirectAttributes redirect) {
        try {
            approvalService.reject(id, authentication.getName(), notes, clientIp(httpRequest));
            redirect.addFlashAttribute("successMessage", "Rejection recorded.");
        } catch (SameApproverException | CampaignValidationException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/approval/queue";
    }

    /**
     * Dual-approval second step. The NonceValidationFilter intercepts this URL and
     * requires X-Nonce + X-Timestamp headers (anti-replay).
     */
    @PostMapping("/dual-approve/{requestId}")
    public String dualApprove(@PathVariable Long requestId,
                              Authentication authentication,
                              HttpServletRequest httpRequest,
                              RedirectAttributes redirect) {
        try {
            dualApprovalService.recordSecond(requestId, authentication.getName(), clientIp(httpRequest));
            redirect.addFlashAttribute("successMessage", "Dual approval complete.");
        } catch (SameApproverException | CampaignValidationException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/approval/queue";
    }

    private String clientIp(HttpServletRequest request) {
        String header = request.getHeader("X-Forwarded-For");
        if (header != null && !header.isBlank()) return header.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
