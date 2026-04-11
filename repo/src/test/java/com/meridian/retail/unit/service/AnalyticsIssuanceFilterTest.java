package com.meridian.retail.unit.service;

import com.meridian.retail.service.*;
import com.meridian.retail.security.*;

import com.meridian.retail.repository.CampaignRepository;
import com.meridian.retail.repository.CouponRedemptionRepository;
import com.meridian.retail.repository.CouponRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R4 audit HIGH #4: {@code AnalyticsService.getCouponStats} must compute the
 * "issuance" metric via the filter-aware repository query, NOT a flat
 * {@code couponRepository.findAll()} sum that ignores store and date filters.
 *
 * The test mocks the repositories so we can assert exactly which method is called and
 * with which filter values, without spinning up a real database.
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsIssuanceFilterTest {

    @Mock CouponRedemptionRepository redemptionRepository;
    @Mock CouponRepository couponRepository;
    @Mock CampaignRepository campaignRepository;

    @Test
    void issuanceQueryReceivesStoreAndDateFilters() {
        AnalyticsService svc = new AnalyticsService(redemptionRepository, couponRepository, campaignRepository);
        LocalDateTime from = LocalDateTime.of(2026, 4, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 4, 30, 23, 59);

        when(redemptionRepository.countRedemptions(eq("STORE-001"), any(), any())).thenReturn(7L);
        when(couponRepository.sumMaxUsesByCampaignFilters(
                eq("STORE-001"),
                eq(LocalDate.of(2026, 4, 1)),
                eq(LocalDate.of(2026, 4, 30))))
                .thenReturn(123L);

        AnalyticsService.CouponStats stats = svc.getCouponStats("STORE-001", from, to);

        assertThat(stats.issuanceCount()).isEqualTo(123L);
        assertThat(stats.redemptionCount()).isEqualTo(7L);
        // findAll() must NOT be called — that was the broken implementation.
        verify(couponRepository, times(0)).findAll();
        verify(couponRepository).sumMaxUsesByCampaignFilters(
                eq("STORE-001"),
                eq(LocalDate.of(2026, 4, 1)),
                eq(LocalDate.of(2026, 4, 30)));
    }

    @Test
    void issuanceWithNullFiltersDelegatesToRepositoryWithNulls() {
        AnalyticsService svc = new AnalyticsService(redemptionRepository, couponRepository, campaignRepository);

        when(redemptionRepository.countRedemptions(any(), any(), any())).thenReturn(0L);
        when(couponRepository.sumMaxUsesByCampaignFilters(any(), any(), any())).thenReturn(42L);

        AnalyticsService.CouponStats stats = svc.getCouponStats(null, null, null);

        assertThat(stats.issuanceCount()).isEqualTo(42L);
        verify(couponRepository, times(0)).findAll();
    }
}
