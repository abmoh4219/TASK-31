package com.meridian.retail.service;

import com.meridian.retail.audit.AuditAction;
import com.meridian.retail.audit.AuditLogService;
import com.meridian.retail.dto.CouponDTO;
import com.meridian.retail.dto.CreateCouponRequest;
import com.meridian.retail.entity.Campaign;
import com.meridian.retail.entity.CampaignStatus;
import com.meridian.retail.entity.Coupon;
import com.meridian.retail.repository.CampaignRepository;
import com.meridian.retail.repository.CouponRepository;
import com.meridian.retail.security.XssInputSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final CampaignService campaignService;
    private final CampaignRepository campaignRepository;
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

    /**
     * Pre-approval validation hook called by ApprovalService before flipping a campaign
     * to APPROVED. Looks at every coupon attached to the candidate campaign and checks
     * for stacking / mutual-exclusion conflicts against coupons that already belong to
     * APPROVED (or beyond) campaigns in the SAME store.
     *
     * Returns a list of human-readable conflict messages — empty when the campaign is
     * safe to approve. The caller is responsible for translating a non-empty list into
     * an exception or UI error.
     *
     * Conflict rules:
     *   - A non-stackable candidate coupon vs ANY existing live coupon in the same store -> conflict.
     *   - A candidate coupon whose mutual_exclusion_group matches a live coupon in the same store -> conflict.
     */
    public List<String> findApprovalStackingConflicts(Long campaignId) {
        List<String> conflicts = new ArrayList<>();
        Campaign candidate = campaignRepository.findById(campaignId).orElse(null);
        if (candidate == null) return conflicts;

        List<Coupon> candidateCoupons = couponRepository.findByCampaignId(campaignId);
        if (candidateCoupons.isEmpty()) return conflicts;

        // "Live" = APPROVED or ACTIVE campaigns in the same store, excluding the candidate itself.
        List<Campaign> liveCampaigns = campaignRepository.findAll().stream()
                .filter(c -> !c.getId().equals(campaignId))
                .filter(c -> c.getDeletedAt() == null)
                .filter(c -> c.getStatus() == CampaignStatus.APPROVED
                          || c.getStatus() == CampaignStatus.ACTIVE)
                .filter(c -> {
                    if (candidate.getStoreId() == null && c.getStoreId() == null) return true;
                    if (candidate.getStoreId() == null || c.getStoreId() == null) return false;
                    return candidate.getStoreId().equalsIgnoreCase(c.getStoreId());
                })
                .toList();

        if (liveCampaigns.isEmpty()) return conflicts;

        List<Coupon> liveCoupons = new ArrayList<>();
        for (Campaign c : liveCampaigns) {
            liveCoupons.addAll(couponRepository.findByCampaignId(c.getId()));
        }
        if (liveCoupons.isEmpty()) return conflicts;

        for (Coupon cand : candidateCoupons) {
            if (!cand.isStackable()) {
                for (Coupon live : liveCoupons) {
                    conflicts.add("Coupon '" + cand.getCode() + "' is non-stackable but campaign '"
                            + campaignNameOf(live.getCampaignId()) + "' already has live coupon '"
                            + live.getCode() + "' in the same store");
                    break; // one conflict per candidate coupon is enough
                }
            }
            String group = cand.getMutualExclusionGroup();
            if (group != null && !group.isBlank()) {
                for (Coupon live : liveCoupons) {
                    if (group.equalsIgnoreCase(live.getMutualExclusionGroup())) {
                        conflicts.add("Coupon '" + cand.getCode() + "' shares exclusion group '"
                                + group + "' with live coupon '" + live.getCode() + "' from campaign '"
                                + campaignNameOf(live.getCampaignId()) + "'");
                    }
                }
            }
        }
        return conflicts;
    }

    private String campaignNameOf(Long id) {
        return campaignRepository.findById(id).map(Campaign::getName).orElse("#" + id);
    }
}
