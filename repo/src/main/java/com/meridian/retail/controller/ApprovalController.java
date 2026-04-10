package com.meridian.retail.controller;

import com.meridian.retail.entity.ApprovalQueue;
import com.meridian.retail.entity.DualApprovalRequest;
import com.meridian.retail.entity.RiskLevel;
import com.meridian.retail.service.ApprovalService;
import com.meridian.retail.service.CampaignValidationException;
import com.meridian.retail.service.DualApprovalService;
import com.meridian.retail.service.SameApproverException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        List<ApprovalQueue> items = approvalService.listPending();
        model.addAttribute("queueItems", items);

        // For each HIGH-risk item, side-load the dual-approval row so the template can
        // decide whether to render "First Approval" or "Second Approval" and which button
        // to disable. Keyed by queueId for easy Thymeleaf lookup.
        Map<Long, DualApprovalRequest> dualByQueue = new HashMap<>();
        for (ApprovalQueue q : items) {
            if (q.getRiskLevel() == RiskLevel.HIGH) {
                Optional<DualApprovalRequest> dual = approvalService.findDualRequest(q.getId());
                dual.ifPresent(d -> dualByQueue.put(q.getId(), d));
            }
        }
        model.addAttribute("dualByQueue", dualByQueue);
        return "approval/queue";
    }

    /**
     * First-approval step for HIGH-risk items. The queue entry stays in REQUIRES_DUAL
     * and only flips to APPROVED once the second (different) reviewer completes step two.
     */
    @PostMapping("/{id}/approve-first")
    public String approveFirst(@PathVariable Long id,
                               Authentication authentication,
                               HttpServletRequest httpRequest,
                               RedirectAttributes redirect) {
        try {
            approvalService.recordFirstApproval(id, authentication.getName(), clientIp(httpRequest));
            redirect.addFlashAttribute("successMessage",
                    "First approval recorded. A second reviewer must complete the dual approval.");
        } catch (SameApproverException | CampaignValidationException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/approval/queue";
    }

    /**
     * Second (completing) approval step for HIGH-risk items. The service enforces
     * approver1 != approver2 — a user who recorded the first approval will be rejected
     * here with SameApproverException.
     */
    @PostMapping("/{id}/approve-second")
    public String approveSecond(@PathVariable Long id,
                                @RequestParam(required = false) String notes,
                                Authentication authentication,
                                HttpServletRequest httpRequest,
                                RedirectAttributes redirect) {
        try {
            approvalService.recordSecondApproval(id, authentication.getName(), notes, clientIp(httpRequest));
            redirect.addFlashAttribute("successMessage", "Dual approval complete — campaign approved.");
        } catch (SameApproverException | CampaignValidationException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/approval/queue";
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
