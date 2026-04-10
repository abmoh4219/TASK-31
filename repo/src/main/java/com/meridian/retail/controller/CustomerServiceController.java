package com.meridian.retail.controller;

import com.meridian.retail.entity.Coupon;
import com.meridian.retail.repository.CampaignRepository;
import com.meridian.retail.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

/**
 * Customer-service console: read-only campaign view + coupon code lookup.
 */
@Controller
@RequestMapping("/cs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('CUSTOMER_SERVICE','ADMIN')")
public class CustomerServiceController {

    private final CouponRepository couponRepository;
    private final CampaignRepository campaignRepository;

    @GetMapping("/lookup")
    public String lookup(@RequestParam(required = false) String code, Model model) {
        model.addAttribute("breadcrumb", "Coupon Lookup");
        model.addAttribute("query", code);
        if (code != null && !code.isBlank()) {
            Optional<Coupon> match = couponRepository.findByCodeIgnoreCase(code.trim());
            model.addAttribute("coupon", match.orElse(null));
            match.ifPresent(c -> campaignRepository.findById(c.getCampaignId())
                    .ifPresent(camp -> model.addAttribute("campaign", camp)));
        }
        return "dashboard/cs";
    }
}
