package com.meridian.retail.unit.controller;

import com.meridian.retail.controller.ApprovalController;
import com.meridian.retail.entity.ApprovalQueue;
import com.meridian.retail.entity.DualApprovalRequest;
import com.meridian.retail.entity.RiskLevel;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApprovalControllerTest {

    @Mock ApprovalService approvalService;
    @Mock DualApprovalService dualApprovalService;
    @Mock Model model;
    @Mock Authentication auth;
    @Mock HttpServletRequest request;
    @Mock RedirectAttributes redirect;

    ApprovalController controller;

    @BeforeEach
    void setUp() {
        controller = new ApprovalController(approvalService, dualApprovalService);
        when(auth.getName()).thenReturn("reviewer");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    // ── queue ─────────────────────────────────────────────────────────────────

    @Test
    void queueReturnsQueueView() {
        when(approvalService.listPending()).thenReturn(List.of());
        String view = controller.queue(model);
        assertThat(view).isEqualTo("approval/queue");
    }

    @Test
    void queueWithHighRiskItemsLoadsDualRequests() {
        ApprovalQueue highRisk = mock(ApprovalQueue.class);
        when(highRisk.getRiskLevel()).thenReturn(RiskLevel.HIGH);
        when(highRisk.getId()).thenReturn(1L);
        when(approvalService.listPending()).thenReturn(List.of(highRisk));
        when(approvalService.findDualRequest(1L)).thenReturn(Optional.empty());

        String view = controller.queue(model);
        assertThat(view).isEqualTo("approval/queue");
        verify(approvalService).findDualRequest(1L);
    }

    @Test
    void queueWithLowRiskItemsDoesNotLoadDualRequests() {
        ApprovalQueue lowRisk = mock(ApprovalQueue.class);
        when(lowRisk.getRiskLevel()).thenReturn(RiskLevel.LOW);
        when(approvalService.listPending()).thenReturn(List.of(lowRisk));

        controller.queue(model);
        verify(approvalService, never()).findDualRequest(anyLong());
    }

    // ── approveFirst ──────────────────────────────────────────────────────────

    @Test
    void approveFirstSuccessRedirectsToQueue() {
        when(approvalService.recordFirstApproval(anyLong(), anyString(), anyString()))
                .thenReturn(mock(ApprovalQueue.class));
        String view = controller.approveFirst(1L, auth, request, redirect);
        assertThat(view).isEqualTo("redirect:/approval/queue");
        verify(redirect).addFlashAttribute(eq("successMessage"), anyString());
    }

    @Test
    void approveFirstWithSameApproverExceptionAddsError() {
        doThrow(new SameApproverException("same approver"))
                .when(approvalService).recordFirstApproval(anyLong(), anyString(), anyString());
        String view = controller.approveFirst(1L, auth, request, redirect);
        assertThat(view).isEqualTo("redirect:/approval/queue");
        verify(redirect).addFlashAttribute(eq("errorMessage"), anyString());
    }

    @Test
    void approveFirstWithValidationExceptionAddsError() {
        doThrow(new CampaignValidationException("not pending"))
                .when(approvalService).recordFirstApproval(anyLong(), anyString(), anyString());
        String view = controller.approveFirst(1L, auth, request, redirect);
        assertThat(view).isEqualTo("redirect:/approval/queue");
        verify(redirect).addFlashAttribute(eq("errorMessage"), anyString());
    }

    // ── approveSecond ─────────────────────────────────────────────────────────

    @Test
    void approveSecondSuccessRedirectsToQueue() {
        when(approvalService.recordSecondApproval(anyLong(), anyString(), any(), anyString()))
                .thenReturn(mock(ApprovalQueue.class));
        String view = controller.approveSecond(1L, "notes", auth, request, redirect);
        assertThat(view).isEqualTo("redirect:/approval/queue");
        verify(redirect).addFlashAttribute(eq("successMessage"), anyString());
    }

    @Test
    void approveSecondWithSameApproverExceptionAddsError() {
        doThrow(new SameApproverException("same approver"))
                .when(approvalService).recordSecondApproval(anyLong(), anyString(), any(), anyString());
        String view = controller.approveSecond(1L, null, auth, request, redirect);
        assertThat(view).isEqualTo("redirect:/approval/queue");
        verify(redirect).addFlashAttribute(eq("errorMessage"), anyString());
    }

    // ── approve ───────────────────────────────────────────────────────────────

    @Test
    void approveSuccessRedirectsToQueue() {
        when(approvalService.approve(anyLong(), anyString(), any(), anyString()))
                .thenReturn(mock(ApprovalQueue.class));
        String view = controller.approve(1L, "looks good", auth, request, redirect);
        assertThat(view).isEqualTo("redirect:/approval/queue");
    }

    @Test
    void approveWithValidationExceptionAddsError() {
        when(approvalService.approve(anyLong(), anyString(), any(), anyString()))
                .thenThrow(new CampaignValidationException("not pending"));
        String view = controller.approve(1L, null, auth, request, redirect);
        assertThat(view).isEqualTo("redirect:/approval/queue");
        verify(redirect).addFlashAttribute(eq("errorMessage"), anyString());
    }

    // ── reject ────────────────────────────────────────────────────────────────

    @Test
    void rejectSuccessRedirectsToQueue() {
        when(approvalService.reject(anyLong(), anyString(), any(), anyString()))
                .thenReturn(mock(ApprovalQueue.class));
        String view = controller.reject(1L, "not good", auth, request, redirect);
        assertThat(view).isEqualTo("redirect:/approval/queue");
        verify(redirect).addFlashAttribute(eq("successMessage"), anyString());
    }

    @Test
    void rejectWithExceptionAddsError() {
        doThrow(new CampaignValidationException("already rejected"))
                .when(approvalService).reject(anyLong(), anyString(), any(), anyString());
        String view = controller.reject(1L, null, auth, request, redirect);
        assertThat(view).isEqualTo("redirect:/approval/queue");
        verify(redirect).addFlashAttribute(eq("errorMessage"), anyString());
    }

    // ── dualApprove ───────────────────────────────────────────────────────────

    @Test
    void dualApproveSuccessRedirectsToQueue() {
        when(dualApprovalService.recordSecond(anyLong(), anyString(), anyString()))
                .thenReturn(mock(com.meridian.retail.entity.DualApprovalRequest.class));
        String view = controller.dualApprove(1L, auth, request, redirect);
        assertThat(view).isEqualTo("redirect:/approval/queue");
        verify(redirect).addFlashAttribute(eq("successMessage"), anyString());
    }

    @Test
    void dualApproveWithSameApproverExceptionAddsError() {
        doThrow(new SameApproverException("same approver"))
                .when(dualApprovalService).recordSecond(anyLong(), anyString(), anyString());
        String view = controller.dualApprove(1L, auth, request, redirect);
        assertThat(view).isEqualTo("redirect:/approval/queue");
        verify(redirect).addFlashAttribute(eq("errorMessage"), anyString());
    }
}
