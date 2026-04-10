package com.meridian.retail.controller;

import com.meridian.retail.dto.CreateCouponRequest;
import com.meridian.retail.entity.Coupon;
import com.meridian.retail.entity.DiscountType;
import com.meridian.retail.repository.CampaignRepository;
import com.meridian.retail.service.CampaignValidationException;
import com.meridian.retail.service.CouponService;
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

import java.util.Arrays;

/**
 * CRUD controller for coupons.
 *
 * List and forms render Thymeleaf pages; create/update go through CouponService which
 * validates, sanitizes, and writes audit-log rows. The coupon CODE is immutable after
 * creation — it can appear on live receipts and redemption logs, so the edit form
 * displays it read-only.
 *
 * Role gate: OPERATIONS + ADMIN. Finance/reviewer/customer-service are read-only via
 * the list page but cannot reach the form endpoints (enforced below + in the sidebar).
 */
@Controller
@RequestMapping("/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;
    private final CampaignRepository campaignRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','REVIEWER','FINANCE','CUSTOMER_SERVICE')")
    public String list(Model model) {
        model.addAttribute("breadcrumb", "Coupons");
        model.addAttribute("coupons", couponService.listAll());
        return "coupon/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public String newForm(Model model) {
        model.addAttribute("breadcrumb", "New Coupon");
        model.addAttribute("coupon", new CreateCouponRequest());
        model.addAttribute("campaigns", campaignRepository.findByDeletedAtIsNullOrderByCreatedAtDesc());
        model.addAttribute("discountTypes", Arrays.asList(DiscountType.values()));
        model.addAttribute("editing", false);
        return "coupon/form";
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public String create(@Valid @ModelAttribute("coupon") CreateCouponRequest request,
                         BindingResult binding,
                         Authentication auth,
                         HttpServletRequest httpRequest,
                         RedirectAttributes redirect,
                         Model model) {
        if (binding.hasErrors()) {
            model.addAttribute("campaigns", campaignRepository.findByDeletedAtIsNullOrderByCreatedAtDesc());
            model.addAttribute("discountTypes", Arrays.asList(DiscountType.values()));
            model.addAttribute("editing", false);
            return "coupon/form";
        }
        try {
            Coupon saved = couponService.createCoupon(request, auth.getName(), clientIp(httpRequest));
            redirect.addFlashAttribute("successMessage", "Coupon created: " + saved.getCode());
            return "redirect:/coupons";
        } catch (CampaignValidationException e) {
            binding.reject("code", e.getMessage());
            model.addAttribute("campaigns", campaignRepository.findByDeletedAtIsNullOrderByCreatedAtDesc());
            model.addAttribute("discountTypes", Arrays.asList(DiscountType.values()));
            model.addAttribute("editing", false);
            return "coupon/form";
        }
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public String editForm(@PathVariable Long id, Model model) {
        Coupon existing = couponService.findById(id);
        CreateCouponRequest form = new CreateCouponRequest();
        form.setCampaignId(existing.getCampaignId());
        form.setCode(existing.getCode());
        form.setDiscountType(existing.getDiscountType());
        form.setDiscountValue(existing.getDiscountValue());
        form.setMinPurchaseAmount(existing.getMinPurchaseAmount());
        form.setMaxUses(existing.getMaxUses());
        form.setStackable(existing.isStackable());
        form.setMutualExclusionGroup(existing.getMutualExclusionGroup());
        form.setValidFrom(existing.getValidFrom());
        form.setValidUntil(existing.getValidUntil());

        model.addAttribute("breadcrumb", "Edit Coupon");
        model.addAttribute("coupon", form);
        model.addAttribute("couponId", id);
        model.addAttribute("campaigns", campaignRepository.findByDeletedAtIsNullOrderByCreatedAtDesc());
        model.addAttribute("discountTypes", Arrays.asList(DiscountType.values()));
        model.addAttribute("editing", true);
        return "coupon/form";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("coupon") CreateCouponRequest request,
                         BindingResult binding,
                         Authentication auth,
                         HttpServletRequest httpRequest,
                         RedirectAttributes redirect,
                         Model model) {
        if (binding.hasErrors()) {
            model.addAttribute("campaigns", campaignRepository.findByDeletedAtIsNullOrderByCreatedAtDesc());
            model.addAttribute("discountTypes", Arrays.asList(DiscountType.values()));
            model.addAttribute("editing", true);
            model.addAttribute("couponId", id);
            return "coupon/form";
        }
        try {
            couponService.updateCoupon(id, request, auth.getName(), clientIp(httpRequest));
            redirect.addFlashAttribute("successMessage", "Coupon updated.");
            return "redirect:/coupons";
        } catch (CampaignValidationException e) {
            binding.reject("discountValue", e.getMessage());
            model.addAttribute("campaigns", campaignRepository.findByDeletedAtIsNullOrderByCreatedAtDesc());
            model.addAttribute("discountTypes", Arrays.asList(DiscountType.values()));
            model.addAttribute("editing", true);
            model.addAttribute("couponId", id);
            return "coupon/form";
        }
    }

    /**
     * HTMX code-availability check for live form feedback while the user types the
     * coupon code in the new-coupon form. Returns a tiny HTML fragment that Bootstrap
     * can show as a success/error helper label.
     */
    @GetMapping("/check-code")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    @ResponseBody
    public String checkCode(@RequestParam String code) {
        if (code == null || code.isBlank()) {
            return "<div class='form-text text-muted'>Enter a code.</div>";
        }
        if (couponService.isCodeAvailable(code)) {
            return "<div class='form-text text-success'><i class='bi bi-check-circle'></i> '"
                    + escape(code) + "' is available.</div>";
        }
        return "<div class='form-text text-danger'><i class='bi bi-x-circle'></i> '"
                + escape(code) + "' is already in use.</div>";
    }

    private String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String clientIp(HttpServletRequest request) {
        String header = request.getHeader("X-Forwarded-For");
        if (header != null && !header.isBlank()) return header.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
