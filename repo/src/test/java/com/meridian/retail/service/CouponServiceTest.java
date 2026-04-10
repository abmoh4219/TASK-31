package com.meridian.retail.service;

import com.meridian.retail.dto.CreateCouponRequest;
import com.meridian.retail.entity.Coupon;
import com.meridian.retail.entity.DiscountType;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock CouponRepository couponRepository;
    @Mock CampaignService campaignService;
    @Mock com.meridian.retail.repository.CampaignRepository campaignRepository;
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
    void createRejectsDuplicateCode() {
        CreateCouponRequest req = new CreateCouponRequest();
        req.setCampaignId(1L);
        req.setCode("SUMMER");
        req.setDiscountType(DiscountType.PERCENT);
        req.setDiscountValue(new BigDecimal("10"));
        when(couponRepository.existsByCodeIgnoreCase("SUMMER")).thenReturn(true);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> svc.createCoupon(req, "ops", "127.0.0.1"))
                .isInstanceOf(CampaignValidationException.class)
                .hasMessageContaining("already in use");
    }

    @Test
    void updateChangesDiscountValueButKeepsCode() {
        Coupon existing = Coupon.builder()
                .id(1L).code("KEEPME").campaignId(7L)
                .discountType(DiscountType.PERCENT).discountValue(new BigDecimal("10"))
                .minPurchaseAmount(BigDecimal.ZERO).maxUses(100).build();
        when(couponRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateCouponRequest req = new CreateCouponRequest();
        req.setCampaignId(7L);
        req.setCode("KEEPME");
        req.setDiscountType(DiscountType.PERCENT);
        req.setDiscountValue(new BigDecimal("25"));
        req.setMinPurchaseAmount(new BigDecimal("50"));
        req.setMaxUses(500);

        Coupon updated = svc.updateCoupon(1L, req, "ops", "127.0.0.1");

        assertThat(updated.getCode()).isEqualTo("KEEPME");
        assertThat(updated.getDiscountValue()).isEqualByComparingTo("25");
        assertThat(updated.getMaxUses()).isEqualTo(500);
    }

    @Test
    void mutualExclusionGroupRejectsConflict() {
        Coupon candidate = Coupon.builder().stackable(true).mutualExclusionGroup("SEASONAL").build();
        Coupon existing = Coupon.builder().id(1L).stackable(true).mutualExclusionGroup("SEASONAL").build();
        when(couponRepository.findAllById(anyList())).thenReturn(List.of(existing));
        assertThat(svc.checkStackingCompatibility(candidate, List.of(1L))).isFalse();
    }
}
