package com.meridian.retail.service;

import com.meridian.retail.entity.DiscountType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit tests for CampaignService validation rules. The mutating methods are
 * exercised in CampaignIntegrationTest against real Spring + MySQL.
 */
class CampaignServiceTest {

    private final CampaignService svc = new CampaignService(null, null);

    // ---- date range ----
    @Test
    void validDateRange() {
        assertThatCode(() ->
                svc.validateDateRange(LocalDate.now().plusDays(1), LocalDate.now().plusDays(5))
        ).doesNotThrowAnyException();
    }

    @Test
    void rejectEndOnSameDayAsStart() {
        LocalDate s = LocalDate.now().plusDays(1);
        assertThatThrownBy(() -> svc.validateDateRange(s, s))
                .isInstanceOf(CampaignValidationException.class)
                .hasMessageContaining("after");
    }

    @Test
    void rejectStartInPast() {
        assertThatThrownBy(() -> svc.validateDateRange(LocalDate.now().minusDays(1), LocalDate.now().plusDays(1)))
                .isInstanceOf(CampaignValidationException.class)
                .hasMessageContaining("past");
    }

    @Test
    void rejectNullDates() {
        assertThatThrownBy(() -> svc.validateDateRange(null, LocalDate.now().plusDays(1)))
                .isInstanceOf(CampaignValidationException.class);
        assertThatThrownBy(() -> svc.validateDateRange(LocalDate.now().plusDays(1), null))
                .isInstanceOf(CampaignValidationException.class);
    }

    // ---- discount value ----
    @Test
    void validPercentDiscount() {
        assertThatCode(() -> svc.validateDiscountValue(DiscountType.PERCENT, BigDecimal.valueOf(15)))
                .doesNotThrowAnyException();
    }

    @Test
    void validFixedDiscount() {
        assertThatCode(() -> svc.validateDiscountValue(DiscountType.FIXED, BigDecimal.valueOf(20)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectNegativeDiscount() {
        assertThatThrownBy(() -> svc.validateDiscountValue(DiscountType.FIXED, BigDecimal.valueOf(-1)))
                .isInstanceOf(CampaignValidationException.class);
    }

    @Test
    void rejectZeroDiscount() {
        assertThatThrownBy(() -> svc.validateDiscountValue(DiscountType.FIXED, BigDecimal.ZERO))
                .isInstanceOf(CampaignValidationException.class);
    }

    @Test
    void rejectPercentOver100() {
        assertThatThrownBy(() -> svc.validateDiscountValue(DiscountType.PERCENT, BigDecimal.valueOf(101)))
                .isInstanceOf(CampaignValidationException.class)
                .hasMessageContaining("100");
    }
}
