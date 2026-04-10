package com.meridian.retail.service;

import com.meridian.retail.entity.Coupon;
import com.meridian.retail.repository.CouponRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock CouponRepository couponRepository;
    @Mock CampaignService campaignService;
    @Mock com.meridian.retail.audit.AuditLogService auditLogService;
    @InjectMocks CouponService svc;

    @Test
    void nonStackableCouponRejectedIfCartNotEmpty() {
        Coupon candidate = Coupon.builder().stackable(false).build();
        Coupon existing = Coupon.builder().id(1L).stackable(true).build();
        when(couponRepository.findAllById(anyList())).thenReturn(List.of(existing));
        assertThat(svc.checkStackingCompatibility(candidate, List.of(1L))).isFalse();
    }

    @Test
    void stackableCouponAcceptedWithCompatibleCart() {
        Coupon candidate = Coupon.builder().stackable(true).build();
        Coupon existing = Coupon.builder().id(1L).stackable(true).build();
        when(couponRepository.findAllById(anyList())).thenReturn(List.of(existing));
        assertThat(svc.checkStackingCompatibility(candidate, List.of(1L))).isTrue();
    }

    @Test
    void mutualExclusionGroupRejectsConflict() {
        Coupon candidate = Coupon.builder().stackable(true).mutualExclusionGroup("SEASONAL").build();
        Coupon existing = Coupon.builder().id(1L).stackable(true).mutualExclusionGroup("SEASONAL").build();
        when(couponRepository.findAllById(anyList())).thenReturn(List.of(existing));
        assertThat(svc.checkStackingCompatibility(candidate, List.of(1L))).isFalse();
    }
}
