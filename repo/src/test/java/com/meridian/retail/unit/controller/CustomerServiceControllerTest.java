package com.meridian.retail.unit.controller;

import com.meridian.retail.controller.CustomerServiceController;
import com.meridian.retail.entity.Campaign;
import com.meridian.retail.entity.Coupon;
import com.meridian.retail.repository.CampaignRepository;
import com.meridian.retail.repository.CouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ui.Model;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerServiceControllerTest {

    @Mock CouponRepository couponRepository;
    @Mock CampaignRepository campaignRepository;
    @Mock Model model;

    CustomerServiceController controller;

    @BeforeEach
    void setUp() {
        controller = new CustomerServiceController(couponRepository, campaignRepository);
    }

    @Test
    void lookupWithNullCodeReturnsView() {
        String view = controller.lookup(null, model);
        assertThat(view).isEqualTo("dashboard/cs");
        verify(model).addAttribute("query", null);
        verifyNoInteractions(couponRepository);
    }

    @Test
    void lookupWithBlankCodeReturnsView() {
        String view = controller.lookup("  ", model);
        assertThat(view).isEqualTo("dashboard/cs");
        verifyNoInteractions(couponRepository);
    }

    @Test
    void lookupWithCodeSearchesCoupon() {
        when(couponRepository.findByCodeIgnoreCase("SPRING15")).thenReturn(Optional.empty());
        String view = controller.lookup("SPRING15", model);
        assertThat(view).isEqualTo("dashboard/cs");
        verify(couponRepository).findByCodeIgnoreCase("SPRING15");
        verify(model).addAttribute("coupon", null);
    }

    @Test
    void lookupWithFoundCouponAddsItToModel() {
        Coupon coupon = mock(Coupon.class);
        when(coupon.getCampaignId()).thenReturn(1L);
        when(couponRepository.findByCodeIgnoreCase("SPRING15")).thenReturn(Optional.of(coupon));
        Campaign campaign = mock(Campaign.class);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));

        String view = controller.lookup("SPRING15", model);
        assertThat(view).isEqualTo("dashboard/cs");
        verify(model).addAttribute("coupon", coupon);
        verify(model).addAttribute("campaign", campaign);
    }

    @Test
    void lookupWithFoundCouponButNoCampaignOnlyAddsCoupon() {
        Coupon coupon = mock(Coupon.class);
        when(coupon.getCampaignId()).thenReturn(99L);
        when(couponRepository.findByCodeIgnoreCase("UNKNOWN")).thenReturn(Optional.of(coupon));
        when(campaignRepository.findById(99L)).thenReturn(Optional.empty());

        String view = controller.lookup("UNKNOWN", model);
        assertThat(view).isEqualTo("dashboard/cs");
        verify(model).addAttribute("coupon", coupon);
        verify(model, never()).addAttribute(eq("campaign"), any());
    }
}
