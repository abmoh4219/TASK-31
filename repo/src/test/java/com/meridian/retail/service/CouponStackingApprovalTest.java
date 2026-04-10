package com.meridian.retail.service;

import com.meridian.retail.audit.AuditLogService;
import com.meridian.retail.entity.Campaign;
import com.meridian.retail.entity.CampaignStatus;
import com.meridian.retail.entity.Coupon;
import com.meridian.retail.repository.CampaignRepository;
import com.meridian.retail.repository.CouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * R4 audit HIGH #3: stacking / mutual-exclusion validation must run during the
 * approval flow, not just as a runtime cart check. {@link CouponService#findApprovalStackingConflicts(Long)}
 * is the new pre-approval hook called by {@code ApprovalService} before flipping a
 * campaign to APPROVED.
 *
 * Two cases:
 *   - candidate non-stackable coupon vs already-live coupon in same store -> conflict
 *   - candidate with no overlapping group / stackable -> no conflict
 */
@ExtendWith(MockitoExtension.class)
class CouponStackingApprovalTest {

    @Mock CouponRepository couponRepository;
    @Mock CampaignRepository campaignRepository;
    @Mock CampaignService campaignService;
    @Mock AuditLogService auditLogService;

    private CouponService couponService;

    @BeforeEach
    void setUp() {
        couponService = new CouponService(couponRepository, campaignService, campaignRepository, auditLogService);
        // Most tests don't care about audit writes; just no-op them.
        lenient().doNothing().when(auditLogService).log(any(), any(), any(), any(), any(), any(), any());
    }

    private Campaign campaign(long id, String store, CampaignStatus status) {
        return Campaign.builder()
                .id(id)
                .name("Camp " + id)
                .storeId(store)
                .status(status)
                .build();
    }

    private Coupon coupon(long id, long campaignId, String code, boolean stackable, String group) {
        return Coupon.builder()
                .id(id)
                .campaignId(campaignId)
                .code(code)
                .stackable(stackable)
                .mutualExclusionGroup(group)
                .build();
    }

    @Test
    void nonStackableCandidateAgainstLiveCouponInSameStoreReportsConflict() {
        Campaign candidate = campaign(100L, "STORE-001", CampaignStatus.PENDING_REVIEW);
        Campaign live = campaign(200L, "STORE-001", CampaignStatus.APPROVED);
        Coupon candidateCoupon = coupon(1L, 100L, "NEW10", false, null);
        Coupon liveCoupon = coupon(2L, 200L, "OLD15", true, null);

        org.mockito.Mockito.when(campaignRepository.findById(100L)).thenReturn(Optional.of(candidate));
        org.mockito.Mockito.when(couponRepository.findByCampaignId(100L)).thenReturn(List.of(candidateCoupon));
        org.mockito.Mockito.when(campaignRepository.findAll()).thenReturn(List.of(candidate, live));
        org.mockito.Mockito.when(couponRepository.findByCampaignId(200L)).thenReturn(List.of(liveCoupon));

        List<String> conflicts = couponService.findApprovalStackingConflicts(100L);

        assertThat(conflicts).isNotEmpty();
        assertThat(conflicts.get(0)).contains("NEW10").contains("non-stackable");
    }

    @Test
    void overlappingMutualExclusionGroupReportsConflict() {
        Campaign candidate = campaign(100L, "STORE-001", CampaignStatus.PENDING_REVIEW);
        Campaign live = campaign(200L, "STORE-001", CampaignStatus.ACTIVE);
        Coupon candidateCoupon = coupon(1L, 100L, "GROUPA-NEW", true, "GROUP_A");
        Coupon liveCoupon = coupon(2L, 200L, "GROUPA-OLD", true, "GROUP_A");

        org.mockito.Mockito.when(campaignRepository.findById(100L)).thenReturn(Optional.of(candidate));
        org.mockito.Mockito.when(couponRepository.findByCampaignId(100L)).thenReturn(List.of(candidateCoupon));
        org.mockito.Mockito.when(campaignRepository.findAll()).thenReturn(List.of(candidate, live));
        org.mockito.Mockito.when(couponRepository.findByCampaignId(200L)).thenReturn(List.of(liveCoupon));

        List<String> conflicts = couponService.findApprovalStackingConflicts(100L);

        assertThat(conflicts).anyMatch(s -> s.contains("GROUP_A"));
    }

    @Test
    void stackableCandidateInDifferentStoreHasNoConflict() {
        Campaign candidate = campaign(100L, "STORE-001", CampaignStatus.PENDING_REVIEW);
        Campaign live = campaign(200L, "STORE-002", CampaignStatus.APPROVED);
        Coupon candidateCoupon = coupon(1L, 100L, "STACK-NEW", true, null);
        Coupon liveCoupon = coupon(2L, 200L, "STACK-OLD", false, null);

        org.mockito.Mockito.when(campaignRepository.findById(100L)).thenReturn(Optional.of(candidate));
        org.mockito.Mockito.when(couponRepository.findByCampaignId(100L)).thenReturn(List.of(candidateCoupon));
        org.mockito.Mockito.when(campaignRepository.findAll()).thenReturn(List.of(candidate, live));

        List<String> conflicts = couponService.findApprovalStackingConflicts(100L);

        assertThat(conflicts).isEmpty();
    }
}
