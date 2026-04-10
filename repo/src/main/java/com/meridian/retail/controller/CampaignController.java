package com.meridian.retail.controller;

import com.meridian.retail.dto.CreateCampaignRequest;
import com.meridian.retail.entity.Campaign;
import com.meridian.retail.entity.CampaignStatus;
import com.meridian.retail.entity.CampaignType;
import com.meridian.retail.entity.DiscountType;
import com.meridian.retail.entity.RiskLevel;
import com.meridian.retail.repository.CouponRepository;
import com.meridian.retail.service.ApprovalService;
import com.meridian.retail.service.CampaignService;
import com.meridian.retail.service.CampaignValidationException;
import com.meridian.retail.service.ReceiptPreviewService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Controller
@RequestMapping("/campaigns")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignService campaignService;
    private final ApprovalService approvalService;
    private final ReceiptPreviewService receiptPreviewService;
    private final CouponRepository couponRepository;

    /** Listing — all roles can read; role-aware action buttons live in the template. */
    @GetMapping
    public String list(@RequestParam(required = false) CampaignStatus status,
                       @RequestParam(required = false) CampaignType type,
                       Model model) {
        model.addAttribute("breadcrumb", "Campaigns");
        model.addAttribute("campaigns", (status != null || type != null)
                ? campaignService.search(status, type)
                : campaignService.listAll());
        model.addAttribute("statusFilter", status);
        model.addAttribute("typeFilter", type);
        model.addAttribute("statuses", CampaignStatus.values());
        model.addAttribute("types", CampaignType.values());
        return "campaign/list";
    }

    /** New campaign form. */
    @GetMapping("/new")
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    public String newForm(Model model) {
        model.addAttribute("breadcrumb", "New Campaign");
        if (!model.containsAttribute("campaign")) {
            model.addAttribute("campaign", new CreateCampaignRequest());
        }
        model.addAttribute("isNew", true);
        model.addAttribute("types", CampaignType.values());
        model.addAttribute("riskLevels", RiskLevel.values());
        return "campaign/form";
    }

    /** Create campaign POST. */
    @PostMapping
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    public String create(@Valid @ModelAttribute("campaign") CreateCampaignRequest request,
                         BindingResult bindingResult,
                         Authentication authentication,
                         HttpServletRequest httpRequest,
                         RedirectAttributes redirect,
                         Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("isNew", true);
            model.addAttribute("types", CampaignType.values());
            model.addAttribute("riskLevels", RiskLevel.values());
            return "campaign/form";
        }
        try {
            Campaign created = campaignService.createCampaign(
                    request, authentication.getName(), clientIp(httpRequest));
            redirect.addFlashAttribute("successMessage",
                    "Campaign '" + created.getName() + "' created as DRAFT.");
            return "redirect:/campaigns";
        } catch (CampaignValidationException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("isNew", true);
            model.addAttribute("types", CampaignType.values());
            model.addAttribute("riskLevels", RiskLevel.values());
            return "campaign/form";
        }
    }

    /** Edit form. */
    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    public String editForm(@PathVariable Long id, Model model) {
        Campaign c = campaignService.findById(id);
        CreateCampaignRequest req = new CreateCampaignRequest();
        req.setName(c.getName());
        req.setDescription(c.getDescription());
        req.setType(c.getType());
        req.setReceiptWording(c.getReceiptWording());
        req.setStoreId(c.getStoreId());
        req.setRiskLevel(c.getRiskLevel());
        req.setStartDate(c.getStartDate());
        req.setEndDate(c.getEndDate());
        model.addAttribute("breadcrumb", "Edit Campaign");
        model.addAttribute("campaign", req);
        model.addAttribute("campaignId", id);
        model.addAttribute("currentCampaign", c);
        model.addAttribute("isNew", false);
        model.addAttribute("types", CampaignType.values());
        model.addAttribute("riskLevels", RiskLevel.values());
        return "campaign/form";
    }

    /** Update via PUT. */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("campaign") CreateCampaignRequest request,
                         BindingResult bindingResult,
                         Authentication authentication,
                         HttpServletRequest httpRequest,
                         RedirectAttributes redirect,
                         Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("campaignId", id);
            model.addAttribute("isNew", false);
            model.addAttribute("types", CampaignType.values());
            model.addAttribute("riskLevels", RiskLevel.values());
            return "campaign/form";
        }
        try {
            campaignService.updateCampaign(id, request, authentication.getName(), clientIp(httpRequest));
            redirect.addFlashAttribute("successMessage", "Campaign updated.");
            return "redirect:/campaigns";
        } catch (CampaignValidationException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("campaignId", id);
            model.addAttribute("isNew", false);
            model.addAttribute("types", CampaignType.values());
            model.addAttribute("riskLevels", RiskLevel.values());
            return "campaign/form";
        }
    }

    /** Submit-for-review action. */
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    public String submitForReview(@PathVariable Long id,
                                  Authentication authentication,
                                  HttpServletRequest httpRequest,
                                  RedirectAttributes redirect) {
        try {
            Campaign c = campaignService.submitForReview(id, authentication.getName(), clientIp(httpRequest));
            approvalService.submitToQueue(c.getId(), authentication.getName(), c.getRiskLevel(), clientIp(httpRequest));
            redirect.addFlashAttribute("successMessage", "Campaign submitted for review.");
        } catch (CampaignValidationException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/campaigns";
    }

    /** Soft delete. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    public String delete(@PathVariable Long id,
                         Authentication authentication,
                         HttpServletRequest httpRequest,
                         RedirectAttributes redirect) {
        campaignService.softDelete(id, authentication.getName(), clientIp(httpRequest));
        redirect.addFlashAttribute("successMessage", "Campaign removed.");
        return "redirect:/campaigns";
    }

    // ---------- HTMX validation endpoints (return small HTML fragments) ----------

    @GetMapping(value = "/validate/dates", produces = "text/html")
    @ResponseBody
    public String validateDates(@RequestParam(required = false) String startDate,
                                @RequestParam(required = false) String endDate) {
        try {
            LocalDate s = (startDate == null || startDate.isBlank()) ? null : LocalDate.parse(startDate);
            LocalDate e = (endDate == null || endDate.isBlank()) ? null : LocalDate.parse(endDate);
            if (s == null || e == null) {
                return "<span class='form-text'>Pick a start and end date.</span>";
            }
            campaignService.validateDateRange(s, e);
            return "<span class='field-validation-success'><i class='bi bi-check-circle'></i> Dates look good.</span>";
        } catch (DateTimeParseException ex) {
            return "<span class='field-validation-error'><i class='bi bi-x-circle'></i> Could not parse one of the dates.</span>";
        } catch (CampaignValidationException ex) {
            return "<span class='field-validation-error'><i class='bi bi-x-circle'></i> " + ex.getMessage() + "</span>";
        }
    }

    /** HTMX endpoint that returns "available" or "taken" for a coupon code. */
    @GetMapping(value = "/validate/code", produces = "text/html")
    @ResponseBody
    public String validateCode(@RequestParam(required = false) String code) {
        if (code == null || code.isBlank()) {
            return "<span class='form-text'>Pick a coupon code.</span>";
        }
        if (couponRepository.existsByCodeIgnoreCase(code)) {
            return "<span class='field-validation-error'><i class='bi bi-x-circle'></i> Already taken</span>";
        }
        return "<span class='field-validation-success'><i class='bi bi-check-circle'></i> Available</span>";
    }

    @GetMapping(value = "/validate/discount", produces = "text/html")
    @ResponseBody
    public String validateDiscount(@RequestParam DiscountType type,
                                   @RequestParam(required = false) String value) {
        try {
            BigDecimal v = (value == null || value.isBlank()) ? null : new BigDecimal(value);
            campaignService.validateDiscountValue(type, v);
            return "<span class='field-validation-success'><i class='bi bi-check-circle'></i> Looks good.</span>";
        } catch (NumberFormatException ex) {
            return "<span class='field-validation-error'><i class='bi bi-x-circle'></i> Discount must be a number.</span>";
        } catch (CampaignValidationException ex) {
            return "<span class='field-validation-error'><i class='bi bi-x-circle'></i> " + ex.getMessage() + "</span>";
        }
    }

    /** Receipt preview HTMX fragment. */
    @PostMapping(value = "/preview-receipt", produces = "text/html")
    public String previewReceipt(@ModelAttribute("campaign") CreateCampaignRequest req,
                                 Model model) {
        Campaign tmp = Campaign.builder()
                .name(req.getName())
                .type(req.getType())
                .storeId(req.getStoreId())
                .receiptWording(req.getReceiptWording())
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
                .build();
        model.addAttribute("preview", receiptPreviewService.generatePreview(tmp));
        return "campaign/receipt-preview-fragment :: receipt";
    }

    @GetMapping(value = "/{id}/preview-receipt", produces = "text/html")
    public String previewReceiptForExisting(@PathVariable Long id, Model model) {
        Campaign c = campaignService.findById(id);
        model.addAttribute("preview", receiptPreviewService.generatePreview(c));
        return "campaign/receipt-preview-fragment :: receipt";
    }

    private String clientIp(HttpServletRequest request) {
        String header = request.getHeader("X-Forwarded-For");
        if (header != null && !header.isBlank()) return header.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
