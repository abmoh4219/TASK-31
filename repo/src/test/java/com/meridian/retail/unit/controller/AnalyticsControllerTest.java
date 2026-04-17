package com.meridian.retail.unit.controller;

import com.meridian.retail.audit.SensitiveAccessLogService;
import com.meridian.retail.controller.AnalyticsController;
import com.meridian.retail.entity.CampaignStatus;
import com.meridian.retail.repository.CampaignRepository;
import com.meridian.retail.repository.CouponRedemptionRepository;
import com.meridian.retail.service.AnalyticsService;
import com.meridian.retail.service.ExportService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalyticsControllerTest {

    @Mock AnalyticsService analyticsService;
    @Mock ExportService exportService;
    @Mock CampaignRepository campaignRepository;
    @Mock CouponRedemptionRepository redemptionRepository;
    @Mock SensitiveAccessLogService sensitiveAccessLogService;
    @Mock Model model;
    @Mock Authentication auth;
    @Mock HttpServletRequest request;

    AnalyticsController controller;

    @BeforeEach
    void setUp() {
        controller = new AnalyticsController(analyticsService, exportService,
                campaignRepository, redemptionRepository, sensitiveAccessLogService);
        when(auth.getName()).thenReturn("finance");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    // ── dashboard ─────────────────────────────────────────────────────────────

    @Test
    void dashboardWithNoFiltersReturnsAnalyticsDashboardView() {
        // Complex types (CouponStats, DiscountStats) return null by default
        when(analyticsService.getTopCampaigns(isNull(), isNull(), isNull(), eq(10))).thenReturn(List.of());
        when(analyticsService.getTopCampaignsByCampaign(isNull(), isNull(), isNull(), eq(10))).thenReturn(List.of());
        when(campaignRepository.countByStatusAndDeletedAtIsNull(CampaignStatus.ACTIVE)).thenReturn(5L);
        when(redemptionRepository.count()).thenReturn(100L);

        String view = controller.dashboard(null, null, null, auth, request, model);
        assertThat(view).isEqualTo("analytics/dashboard");
    }

    @Test
    void dashboardLogsAccessToSensitiveService() {
        when(analyticsService.getTopCampaigns(any(), any(), any(), anyInt())).thenReturn(List.of());
        when(analyticsService.getTopCampaignsByCampaign(any(), any(), any(), anyInt())).thenReturn(List.of());
        when(campaignRepository.countByStatusAndDeletedAtIsNull(any())).thenReturn(0L);
        when(redemptionRepository.count()).thenReturn(0L);

        controller.dashboard(null, null, null, auth, request, model);
        verify(sensitiveAccessLogService).logAccess(anyString(), anyString(), isNull(),
                anyString(), anyString());
    }

    @Test
    void dashboardAddsActiveCampaignsAndTotalRedemptionsToModel() {
        when(analyticsService.getTopCampaigns(any(), any(), any(), anyInt())).thenReturn(List.of());
        when(analyticsService.getTopCampaignsByCampaign(any(), any(), any(), anyInt())).thenReturn(List.of());
        when(campaignRepository.countByStatusAndDeletedAtIsNull(any())).thenReturn(3L);
        when(redemptionRepository.count()).thenReturn(50L);

        controller.dashboard(null, null, null, auth, request, model);
        verify(model).addAttribute(eq("activeCampaigns"), eq(3L));
        verify(model).addAttribute(eq("totalRedemptions"), eq(50L));
    }

    @Test
    void dashboardWithStoreIdFilterPassesItToService() {
        when(analyticsService.getTopCampaigns(eq("STORE-1"), any(), any(), anyInt())).thenReturn(List.of());
        when(analyticsService.getTopCampaignsByCampaign(eq("STORE-1"), any(), any(), anyInt())).thenReturn(List.of());
        when(campaignRepository.countByStatusAndDeletedAtIsNull(any())).thenReturn(0L);
        when(redemptionRepository.count()).thenReturn(0L);

        controller.dashboard("STORE-1", null, null, auth, request, model);
        verify(analyticsService).getTopCampaigns(eq("STORE-1"), isNull(), isNull(), eq(10));
        verify(model).addAttribute("storeId", "STORE-1");
    }

    // ── trends ────────────────────────────────────────────────────────────────

    @Test
    void trendsReturnsMapWithPointsKey() {
        when(analyticsService.getDailyTrend(any(), any(), any())).thenReturn(List.of());
        java.util.Map<String, Object> result = controller.trends(null, null, null);
        assertThat(result).containsKey("points");
    }

    @Test
    void trendsWithStoreIdFilterPassesItToService() {
        when(analyticsService.getDailyTrend(eq("STORE-X"), any(), any())).thenReturn(List.of());
        controller.trends("STORE-X", null, null);
        verify(analyticsService).getDailyTrend(eq("STORE-X"), isNull(), isNull());
    }

    // ── exportCsv ─────────────────────────────────────────────────────────────

    @Test
    void exportCsvReturnsCsvResponseEntity() {
        StreamingResponseBody body = out -> {};
        when(exportService.exportRedemptionsCsv(anyString(), anyString())).thenReturn(body);

        ResponseEntity<StreamingResponseBody> resp = controller.exportCsv(auth, request);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getHeaders().getContentType().toString()).contains("text/csv");
        assertThat(resp.getHeaders().getContentDisposition().toString()).contains("redemptions.csv");
    }

    @Test
    void exportCsvLogsAccessToSensitiveService() {
        StreamingResponseBody body = out -> {};
        when(exportService.exportRedemptionsCsv(anyString(), anyString())).thenReturn(body);
        controller.exportCsv(auth, request);
        verify(sensitiveAccessLogService).logAccess(anyString(), anyString(), isNull(),
                anyString(), anyString());
    }
}
