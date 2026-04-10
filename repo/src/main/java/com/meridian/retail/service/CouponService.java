package com.meridian.retail.service;

import com.meridian.retail.audit.AuditAction;
import com.meridian.retail.audit.AuditLogService;
import com.meridian.retail.dto.CouponDTO;
import com.meridian.retail.dto.CreateCouponRequest;
import com.meridian.retail.entity.Coupon;
import com.meridian.retail.repository.CouponRepository;
import com.meridian.retail.security.XssInputSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final CampaignService campaignService;
    private final AuditLogService auditLogService;

    public List<Coupon> listByCampaign(Long campaignId) {
        return couponRepository.findByCampaignId(campaignId);
    }

    public List<Coupon> listAll() {
        return couponRepository.findAll();
    }

    public Coupon findById(Long id) {
        return couponRepository.findById(id)
                .orElseThrow(() -> new CampaignValidationException("Coupon not found: " + id));
    }

    /**
     * Update a coupon. The code itself is immutable (it can be referenced from live
     * receipts / redemption logs); only discount value, limits, dates, and stacking
     * rules can be modified. An AuditLog row is written with before/after state.
     */
    @Transactional
    public Coupon updateCoupon(Long id, CreateCouponRequest req, String username, String ipAddress) {
        Coupon existing = findById(id);
        CouponDTO before = CouponDTO.from(existing);

        campaignService.validateDiscountValue(req.getDiscountType(), req.getDiscountValue());

        existing.setDiscountType(req.getDiscountType());
        existing.setDiscountValue(req.getDiscountValue());
        existing.setMinPurchaseAmount(req.getMinPurchaseAmount() != null ? req.getMinPurchaseAmount() : BigDecimal.ZERO);
        existing.setMaxUses(req.getMaxUses());
        existing.setStackable(req.isStackable());
        existing.setMutualExclusionGroup(XssInputSanitizer.sanitize(req.getMutualExclusionGroup()));
        existing.setValidFrom(req.getValidFrom());
        existing.setValidUntil(req.getValidUntil());

        Coupon saved = couponRepository.save(existing);
        auditLogService.log(AuditAction.COUPON_UPDATED, "Coupon", saved.getId(),
                before, CouponDTO.from(saved), username, ipAddress);
        return saved;
    }

    @Transactional
    public Coupon createCoupon(CreateCouponRequest req, String username, String ipAddress) {
        // Sanitize then validate
        String code = XssInputSanitizer.sanitize(req.getCode());
        if (code == null || code.isBlank()) {
            throw new CampaignValidationException("Coupon code is required");
        }
        if (couponRepository.existsByCodeIgnoreCase(code)) {
            throw new CampaignValidationException("Coupon code already in use: " + code);
        }
        // Defer to CampaignService for the discount value rule so it stays consistent.
        campaignService.validateDiscountValue(req.getDiscountType(), req.getDiscountValue());

        Coupon c = Coupon.builder()
                .campaignId(req.getCampaignId())
                .code(code)
                .discountType(req.getDiscountType())
                .discountValue(req.getDiscountValue())
                .minPurchaseAmount(req.getMinPurchaseAmount() != null ? req.getMinPurchaseAmount() : BigDecimal.ZERO)
                .maxUses(req.getMaxUses())
                .stackable(req.isStackable())
                .mutualExclusionGroup(XssInputSanitizer.sanitize(req.getMutualExclusionGroup()))
                .validFrom(req.getValidFrom())
                .validUntil(req.getValidUntil())
                .build();

        Coupon saved = couponRepository.save(c);
        auditLogService.log(AuditAction.COUPON_CREATED, "Coupon", saved.getId(),
                null, CouponDTO.from(saved), username, ipAddress);
        return saved;
    }

    public boolean isCodeAvailable(String code) {
        if (code == null || code.isBlank()) return false;
        return !couponRepository.existsByCodeIgnoreCase(code);
    }

    /**
     * Returns true if {@code candidate} can be combined with the supplied set of already-applied
     * coupon ids. Rules:
     *   - If the candidate is non-stackable AND the cart already has any coupon, reject.
     *   - If any existing coupon shares the candidate's mutual_exclusion_group, reject.
     */
    public boolean checkStackingCompatibility(Coupon candidate, List<Long> existingCouponIds) {
        if (candidate == null) return false;
        List<Coupon> existing = couponRepository.findAllById(existingCouponIds);

        if (!candidate.isStackable() && !existing.isEmpty()) {
            return false;
        }
        for (Coupon e : existing) {
            if (!e.isStackable()) return false;
        }

        Set<String> groups = new HashSet<>();
        if (candidate.getMutualExclusionGroup() != null) groups.add(candidate.getMutualExclusionGroup());
        for (Coupon e : existing) {
            if (e.getMutualExclusionGroup() != null && !groups.add(e.getMutualExclusionGroup())) {
                // Already present -> two coupons share an exclusion group -> reject.
                return false;
            }
        }
        // Also reject if candidate's group is already used by an existing coupon.
        if (candidate.getMutualExclusionGroup() != null) {
            for (Coupon e : existing) {
                if (candidate.getMutualExclusionGroup().equalsIgnoreCase(e.getMutualExclusionGroup())) {
                    return false;
                }
            }
        }
        return true;
    }
}
