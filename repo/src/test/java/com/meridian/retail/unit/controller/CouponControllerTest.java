package com.meridian.retail.unit.controller;

import com.meridian.retail.controller.CouponController;
import com.meridian.retail.dto.CreateCouponRequest;
import com.meridian.retail.entity.Coupon;
import com.meridian.retail.entity.DiscountType;
import com.meridian.retail.repository.CampaignRepository;
import com.meridian.retail.service.CampaignValidationException;
import com.meridian.retail.service.CouponService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CouponControllerTest {

    @Mock CouponService couponService;
    @Mock CampaignRepository campaignRepository;
    @Mock Model model;
    @Mock BindingResult bindingResult;
    @Mock Authentication auth;
    @Mock HttpServletRequest request;
    @Mock RedirectAttributes redirect;

    CouponController controller;

    @BeforeEach
    void setUp() {
        controller = new CouponController(couponService, campaignRepository);
        when(auth.getName()).thenReturn("ops");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    // ── list ──────────────────────────────────────────────────────────────────

    @Test
    void listReturnsCouponListView() {
        when(couponService.listAll()).thenReturn(List.of());
        String view = controller.list(model);
        assertThat(view).isEqualTo("coupon/list");
    }

    // ── newForm ───────────────────────────────────────────────────────────────

    @Test
    void newFormReturnsCouponFormView() {
        when(campaignRepository.findByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of());
        String view = controller.newForm(model);
        assertThat(view).isEqualTo("coupon/form");
    }

    @Test
    void newFormAddsEditingFalseToModel() {
        when(campaignRepository.findByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of());
        controller.newForm(model);
        verify(model).addAttribute("editing", false);
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void createWithBindingErrorsReturnsFormView() {
        when(bindingResult.hasErrors()).thenReturn(true);
        when(campaignRepository.findByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of());
        String view = controller.create(new CreateCouponRequest(), bindingResult,
                auth, request, redirect, model);
        assertThat(view).isEqualTo("coupon/form");
        verifyNoInteractions(couponService);
    }

    @Test
    void createSuccessRedirectsToCoupons() {
        when(bindingResult.hasErrors()).thenReturn(false);
        Coupon saved = mock(Coupon.class);
        when(saved.getCode()).thenReturn("TEST10");
        when(couponService.createCoupon(any(), anyString(), anyString())).thenReturn(saved);
        String view = controller.create(new CreateCouponRequest(), bindingResult,
                auth, request, redirect, model);
        assertThat(view).isEqualTo("redirect:/coupons");
    }

    @Test
    void createWithValidationExceptionReturnsFormView() {
        when(bindingResult.hasErrors()).thenReturn(false);
        when(couponService.createCoupon(any(), anyString(), anyString()))
                .thenThrow(new CampaignValidationException("code taken"));
        when(campaignRepository.findByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of());
        String view = controller.create(new CreateCouponRequest(), bindingResult,
                auth, request, redirect, model);
        assertThat(view).isEqualTo("coupon/form");
        verify(bindingResult).reject(eq("code"), anyString());
    }

    // ── editForm ──────────────────────────────────────────────────────────────

    @Test
    void editFormReturnsCouponFormView() {
        Coupon existing = mock(Coupon.class);
        when(existing.getCampaignId()).thenReturn(1L);
        when(existing.getCode()).thenReturn("EDIT10");
        when(existing.getDiscountType()).thenReturn(DiscountType.PERCENT);
        when(existing.getDiscountValue()).thenReturn(BigDecimal.TEN);
        when(existing.getMinPurchaseAmount()).thenReturn(BigDecimal.ZERO);
        when(existing.getMaxUses()).thenReturn(100);
        when(couponService.findById(1L)).thenReturn(existing);
        when(campaignRepository.findByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of());

        String view = controller.editForm(1L, model);
        assertThat(view).isEqualTo("coupon/form");
        verify(model).addAttribute("editing", true);
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void updateWithBindingErrorsReturnsFormView() {
        when(bindingResult.hasErrors()).thenReturn(true);
        when(campaignRepository.findByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of());
        String view = controller.update(1L, new CreateCouponRequest(), bindingResult,
                auth, request, redirect, model);
        assertThat(view).isEqualTo("coupon/form");
        verifyNoInteractions(couponService);
    }

    @Test
    void updateSuccessRedirectsToCoupons() {
        when(bindingResult.hasErrors()).thenReturn(false);
        Coupon updated = mock(Coupon.class);
        when(couponService.updateCoupon(anyLong(), any(), anyString(), anyString())).thenReturn(updated);
        String view = controller.update(1L, new CreateCouponRequest(), bindingResult,
                auth, request, redirect, model);
        assertThat(view).isEqualTo("redirect:/coupons");
        verify(redirect).addFlashAttribute(eq("successMessage"), anyString());
    }

    @Test
    void updateWithValidationExceptionReturnsFormView() {
        when(bindingResult.hasErrors()).thenReturn(false);
        when(couponService.updateCoupon(anyLong(), any(), anyString(), anyString()))
                .thenThrow(new CampaignValidationException("invalid discount"));
        when(campaignRepository.findByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of());
        String view = controller.update(1L, new CreateCouponRequest(), bindingResult,
                auth, request, redirect, model);
        assertThat(view).isEqualTo("coupon/form");
        verify(bindingResult).reject(eq("discountValue"), anyString());
    }

    // ── checkCode ─────────────────────────────────────────────────────────────

    @Test
    void checkCodeWithBlankCodeReturnsHintFragment() {
        String result = controller.checkCode("");
        assertThat(result).contains("Enter a code");
    }

    @Test
    void checkCodeWithAvailableCodeReturnsAvailableFragment() {
        when(couponService.isCodeAvailable("NEWCODE")).thenReturn(true);
        String result = controller.checkCode("NEWCODE");
        assertThat(result).contains("available");
        assertThat(result).contains("NEWCODE");
    }

    @Test
    void checkCodeWithTakenCodeReturnsTakenFragment() {
        when(couponService.isCodeAvailable("SPRING15")).thenReturn(false);
        String result = controller.checkCode("SPRING15");
        assertThat(result).contains("already in use");
        assertThat(result).contains("SPRING15");
    }

    @Test
    void checkCodeEscapesHtmlInCode() {
        when(couponService.isCodeAvailable("<script>")).thenReturn(true);
        String result = controller.checkCode("<script>");
        assertThat(result).doesNotContain("<script>");
        assertThat(result).contains("&lt;script&gt;");
    }
}
