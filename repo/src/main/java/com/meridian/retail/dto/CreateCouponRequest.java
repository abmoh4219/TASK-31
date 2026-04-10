package com.meridian.retail.dto;

import com.meridian.retail.entity.DiscountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateCouponRequest {

    @NotNull(message = "Campaign ID is required")
    private Long campaignId;

    @NotBlank(message = "Coupon code is required")
    @Size(max = 50)
    private String code;

    @NotNull(message = "Discount type is required")
    private DiscountType discountType;

    @NotNull(message = "Discount value is required")
    private BigDecimal discountValue;

    @PositiveOrZero(message = "Minimum purchase amount cannot be negative")
    private BigDecimal minPurchaseAmount;

    @PositiveOrZero
    private int maxUses;

    private boolean stackable;

    @Size(max = 100)
    private String mutualExclusionGroup;

    private LocalDate validFrom;
    private LocalDate validUntil;
}
