package com.meridian.retail.unit.controller;

import com.meridian.retail.controller.CampaignController;
import com.meridian.retail.dto.CreateCampaignRequest;
import com.meridian.retail.entity.*;
import com.meridian.retail.repository.CouponRepository;
import com.meridian.retail.service.*;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CampaignControllerTest {

    @Mock CampaignService campaignService;
    @Mock ApprovalService approvalService;
    @Mock ReceiptPreviewService receiptPreviewService;
    @Mock CouponRepository couponRepository;
    @Mock Model model;
    @Mock BindingResult bindingResult;
    @Mock Authentication auth;
    @Mock HttpServletRequest request;
    @Mock RedirectAttributes redirect;

    CampaignController controller;

    @BeforeEach
    void setUp() {
        controller = new CampaignController(campaignService, approvalService,
                receiptPreviewService, couponRepository);
        when(auth.getName()).thenReturn("ops");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    // ── list ──────────────────────────────────────────────────────────────────

    @Test
    void listWithNoFiltersCallsListAll() {
        when(campaignService.listAll()).thenReturn(List.of());
        String view = controller.list(null, null, model);
        assertThat(view).isEqualTo("campaign/list");
        verify(campaignService).listAll();
    }

    @Test
    void listWithStatusFilterCallsSearch() {
        when(campaignService.search(any(), any())).thenReturn(List.of());
        String view = controller.list(CampaignStatus.DRAFT, null, model);
        assertThat(view).isEqualTo("campaign/list");
        verify(campaignService).search(CampaignStatus.DRAFT, null);
    }

    @Test
    void listWithTypeFilterCallsSearch() {
        when(campaignService.search(any(), any())).thenReturn(List.of());
        String view = controller.list(null, CampaignType.COUPON, model);
        assertThat(view).isEqualTo("campaign/list");
        verify(campaignService).search(null, CampaignType.COUPON);
    }

    // ── newForm ───────────────────────────────────────────────────────────────

    @Test
    void newFormReturnsFormView() {
        String view = controller.newForm(model);
        assertThat(view).isEqualTo("campaign/form");
    }

    @Test
    void newFormAddsIsNewTrueToModel() {
        controller.newForm(model);
        verify(model).addAttribute("isNew", true);
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void createWithBindingErrorsReturnsFormView() {
        when(bindingResult.hasErrors()).thenReturn(true);
        String view = controller.create(new CreateCampaignRequest(), bindingResult,
                auth, request, redirect, model);
        assertThat(view).isEqualTo("campaign/form");
        verifyNoInteractions(campaignService);
    }

    @Test
    void createSuccessRedirectsToCampaigns() {
        when(bindingResult.hasErrors()).thenReturn(false);
        Campaign c = mock(Campaign.class);
        when(c.getName()).thenReturn("Test Campaign");
        when(campaignService.createCampaign(any(), anyString(), anyString())).thenReturn(c);
        String view = controller.create(new CreateCampaignRequest(), bindingResult,
                auth, request, redirect, model);
        assertThat(view).isEqualTo("redirect:/campaigns");
    }

    @Test
    void createWithValidationExceptionReturnsFormView() {
        when(bindingResult.hasErrors()).thenReturn(false);
        when(campaignService.createCampaign(any(), any(), any()))
                .thenThrow(new CampaignValidationException("date error"));
        String view = controller.create(new CreateCampaignRequest(), bindingResult,
                auth, request, redirect, model);
        assertThat(view).isEqualTo("campaign/form");
        verify(model).addAttribute(eq("errorMessage"), anyString());
    }

    // ── editForm ──────────────────────────────────────────────────────────────

    @Test
    void editFormReturnsCampaignFormView() {
        Campaign c = mock(Campaign.class);
        when(c.getName()).thenReturn("Existing Campaign");
        when(campaignService.findById(1L)).thenReturn(c);
        String view = controller.editForm(1L, model);
        assertThat(view).isEqualTo("campaign/form");
    }

    @Test
    void editFormAddsIsNewFalseToModel() {
        Campaign c = mock(Campaign.class);
        when(campaignService.findById(1L)).thenReturn(c);
        controller.editForm(1L, model);
        verify(model).addAttribute("isNew", false);
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void updateWithBindingErrorsReturnsFormView() {
        when(bindingResult.hasErrors()).thenReturn(true);
        String view = controller.update(1L, new CreateCampaignRequest(), bindingResult,
                auth, request, redirect, model);
        assertThat(view).isEqualTo("campaign/form");
        verifyNoInteractions(campaignService);
    }

    @Test
    void updateSuccessRedirectsToCampaigns() {
        when(bindingResult.hasErrors()).thenReturn(false);
        Campaign updated = mock(Campaign.class);
        when(campaignService.updateCampaign(anyLong(), any(), anyString(), anyString())).thenReturn(updated);
        String view = controller.update(1L, new CreateCampaignRequest(), bindingResult,
                auth, request, redirect, model);
        assertThat(view).isEqualTo("redirect:/campaigns");
    }

    @Test
    void updateWithValidationExceptionReturnsFormView() {
        when(bindingResult.hasErrors()).thenReturn(false);
        when(campaignService.updateCampaign(anyLong(), any(), anyString(), anyString()))
                .thenThrow(new CampaignValidationException("update error"));
        String view = controller.update(1L, new CreateCampaignRequest(), bindingResult,
                auth, request, redirect, model);
        assertThat(view).isEqualTo("campaign/form");
    }

    // ── submitForReview ───────────────────────────────────────────────────────

    @Test
    void submitForReviewSuccessRedirectsToCampaigns() {
        Campaign c = mock(Campaign.class);
        when(c.getId()).thenReturn(1L);
        when(c.getRiskLevel()).thenReturn(RiskLevel.LOW);
        when(campaignService.submitForReview(anyLong(), anyString(), anyString())).thenReturn(c);
        when(approvalService.submitToQueue(anyLong(), anyString(), any(), anyString()))
                .thenReturn(mock(com.meridian.retail.entity.ApprovalQueue.class));
        String view = controller.submitForReview(1L, auth, request, redirect);
        assertThat(view).isEqualTo("redirect:/campaigns");
    }

    @Test
    void submitForReviewWithExceptionRedirectsToCampaigns() {
        when(campaignService.submitForReview(anyLong(), anyString(), anyString()))
                .thenThrow(new CampaignValidationException("already submitted"));
        String view = controller.submitForReview(1L, auth, request, redirect);
        assertThat(view).isEqualTo("redirect:/campaigns");
        verify(redirect).addFlashAttribute(eq("errorMessage"), anyString());
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void deleteRedirectsToCampaigns() {
        doNothing().when(campaignService).softDelete(anyLong(), anyString(), anyString());
        String view = controller.delete(1L, auth, request, redirect);
        assertThat(view).isEqualTo("redirect:/campaigns");
    }

    // ── validateCode ──────────────────────────────────────────────────────────

    @Test
    void validateCodeWithBlankCodeReturnsHintFragment() {
        String result = controller.validateCode(null);
        assertThat(result).contains("Pick a coupon code");
    }

    @Test
    void validateCodeWithExistingCodeReturnsTaken() {
        when(couponRepository.existsByCodeIgnoreCase("TAKEN")).thenReturn(true);
        String result = controller.validateCode("TAKEN");
        assertThat(result).contains("Already taken");
    }

    @Test
    void validateCodeWithNewCodeReturnsAvailable() {
        when(couponRepository.existsByCodeIgnoreCase("NEWCODE")).thenReturn(false);
        String result = controller.validateCode("NEWCODE");
        assertThat(result).contains("Available");
    }

    // ── validateDiscount ──────────────────────────────────────────────────────

    @Test
    void validateDiscountWithValidPercentReturnsSuccess() {
        doNothing().when(campaignService).validateDiscountValue(any(), any());
        String result = controller.validateDiscount(DiscountType.PERCENT, "10");
        assertThat(result).contains("field-validation-success");
    }

    @Test
    void validateDiscountWithExceptionReturnsError() {
        doThrow(new CampaignValidationException("over 100"))
                .when(campaignService).validateDiscountValue(any(), any());
        String result = controller.validateDiscount(DiscountType.PERCENT, "150");
        assertThat(result).contains("field-validation-error");
    }

    @Test
    void validateDiscountWithNonNumericValueReturnsError() {
        String result = controller.validateDiscount(DiscountType.PERCENT, "notanumber");
        assertThat(result).contains("number");
    }

    // ── previewReceipt ────────────────────────────────────────────────────────

    @Test
    void previewReceiptCallsReceiptPreviewService() {
        when(receiptPreviewService.generatePreview(any())).thenReturn("RECEIPT TEXT");
        CreateCampaignRequest req = new CreateCampaignRequest();
        req.setReceiptWording("10% OFF");
        String view = controller.previewReceipt(req, model);
        assertThat(view).contains("receipt");
        verify(receiptPreviewService).generatePreview(any());
    }

    @Test
    void previewReceiptForExistingCallsService() {
        Campaign c = mock(Campaign.class);
        when(campaignService.findById(1L)).thenReturn(c);
        when(receiptPreviewService.generatePreview(c)).thenReturn("PREVIEW");
        String view = controller.previewReceiptForExisting(1L, model);
        assertThat(view).contains("receipt");
        verify(model).addAttribute("preview", "PREVIEW");
    }
}
