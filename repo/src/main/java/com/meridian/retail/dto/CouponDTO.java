package com.meridian.retail.dto;

import com.meridian.retail.entity.Coupon;
import com.meridian.retail.entity.DiscountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponDTO {
    private Long id;
    private Long campaignId;
    private String code;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal minPurchaseAmount;
    private int maxUses;
    private int usesCount;
    private boolean stackable;
    private String mutualExclusionGroup;
    private LocalDate validFrom;
    private LocalDate validUntil;

    public static CouponDTO from(Coupon c) {
        return CouponDTO.builder()
                .id(c.getId())
                .campaignId(c.getCampaignId())
                .code(c.getCode())
                .discountType(c.getDiscountType())
                .discountValue(c.getDiscountValue())
                .minPurchaseAmount(c.getMinPurchaseAmount())
                .maxUses(c.getMaxUses())
                .usesCount(c.getUsesCount())
                .stackable(c.isStackable())
                .mutualExclusionGroup(c.getMutualExclusionGroup())
                .validFrom(c.getValidFrom())
                .validUntil(c.getValidUntil())
                .build();
    }
}
