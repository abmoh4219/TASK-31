package com.meridian.retail.service;

import com.meridian.retail.entity.Coupon;
import com.meridian.retail.repository.CampaignRepository;
import com.meridian.retail.repository.CouponRedemptionRepository;
import com.meridian.retail.repository.CouponRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock CouponRedemptionRepository redemptionRepository;
    @Mock CouponRepository couponRepository;
    @Mock CampaignRepository campaignRepository;
    @InjectMocks AnalyticsService svc;

    @Test
    void couponStatsAggregatesIssuanceAndRedemptions() {
        Coupon c1 = Coupon.builder().id(1L).maxUses(100).build();
        Coupon c2 = Coupon.builder().id(2L).maxUses(50).build();
        when(couponRepository.findAll()).thenReturn(List.of(c1, c2));
        when(redemptionRepository.countRedemptions(eq("STORE-001"), any(), any())).thenReturn(42L);

        var stats = svc.getCouponStats("STORE-001", null, null);
        assertThat(stats.issuanceCount()).isEqualTo(150);
        assertThat(stats.redemptionCount()).isEqualTo(42);
    }

    @Test
    void discountUtilizationHandlesNullSum() {
        when(redemptionRepository.sumDiscountApplied(any(), any())).thenReturn(null);
        var stats = svc.getDiscountUtilization(null, null);
        assertThat(stats.totalDiscountGiven()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void topCampaignsLimitsResults() {
        Object[] r1 = {1L, 10L, BigDecimal.valueOf(100)};
        Object[] r2 = {2L, 5L,  BigDecimal.valueOf(50)};
        Object[] r3 = {3L, 3L,  BigDecimal.valueOf(30)};
        when(redemptionRepository.topCouponsByRedemptions(any(), any(), any()))
                .thenReturn(List.of(r1, r2, r3));
        when(couponRepository.findById(1L)).thenReturn(Optional.of(Coupon.builder().code("A").build()));
        when(couponRepository.findById(2L)).thenReturn(Optional.of(Coupon.builder().code("B").build()));

        var top = svc.getTopCampaigns(null, null, null, 2);
        assertThat(top).hasSize(2);
        assertThat(top.get(0).code()).isEqualTo("A");
        assertThat(top.get(0).redemptions()).isEqualTo(10);
    }
}
