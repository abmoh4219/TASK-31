package com.meridian.retail.controller;

import com.meridian.retail.audit.SensitiveAccessLogService;
import com.meridian.retail.entity.CampaignStatus;
import com.meridian.retail.repository.CampaignRepository;
import com.meridian.retail.repository.CouponRedemptionRepository;
import com.meridian.retail.service.AnalyticsService;
import com.meridian.retail.service.ExportService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;
import java.util.Map;

import java.time.LocalDateTime;

@Controller
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final ExportService exportService;
    private final CampaignRepository campaignRepository;
    private final CouponRedemptionRepository redemptionRepository;
    private final SensitiveAccessLogService sensitiveAccessLogService;

    /** Public-to-staff dashboard. Export button is hidden for non-Finance/Admin via Thymeleaf sec:authorize. */
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN','OPERATIONS')")
    public String dashboard(@RequestParam(required = false) String storeId,
                            @RequestParam(required = false) String from,
                            @RequestParam(required = false) String to,
                            Authentication auth,
                            HttpServletRequest httpRequest,
                            Model model) {
        LocalDateTime f = (from == null || from.isBlank()) ? null : LocalDateTime.parse(from + "T00:00:00");
        LocalDateTime t = (to == null || to.isBlank())     ? null : LocalDateTime.parse(to + "T23:59:59");

        var couponStats = analyticsService.getCouponStats(storeId, f, t);
        var discountStats = analyticsService.getDiscountUtilization(f, t);
        var top = analyticsService.getTopCampaigns(storeId, f, t, 10);
        var topByCampaign = analyticsService.getTopCampaignsByCampaign(storeId, f, t, 10);

        // Financial dashboard read: operator sees aggregated revenue/redemption numbers.
        sensitiveAccessLogService.logAccess("analytics_dashboard", "CouponRedemption", null,
                auth.getName(), clientIp(httpRequest));
        model.addAttribute("breadcrumb", "Analytics");
        model.addAttribute("couponStats", couponStats);
        model.addAttribute("discountStats", discountStats);
        model.addAttribute("topCampaigns", top);
        model.addAttribute("topByCampaign", topByCampaign);
        model.addAttribute("activeCampaigns", campaignRepository.countByStatusAndDeletedAtIsNull(CampaignStatus.ACTIVE));
        model.addAttribute("totalRedemptions", redemptionRepository.count());
        model.addAttribute("storeId", storeId);
        model.addAttribute("fromDate", from);
        model.addAttribute("toDate", to);
        return "analytics/dashboard";
    }

    /**
     * JSON trends endpoint for the dashboard line chart. Returns an array of
     * {date, redemptions, totalDiscount} points bucketed by day.
     */
    @GetMapping("/trends")
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN','OPERATIONS')")
    @ResponseBody
    public Map<String, Object> trends(@RequestParam(required = false) String storeId,
                                      @RequestParam(required = false) String from,
                                      @RequestParam(required = false) String to) {
        LocalDateTime f = (from == null || from.isBlank()) ? null : LocalDateTime.parse(from + "T00:00:00");
        LocalDateTime t = (to == null || to.isBlank())     ? null : LocalDateTime.parse(to + "T23:59:59");
        List<AnalyticsService.TrendPoint> points = analyticsService.getDailyTrend(storeId, f, t);
        return Map.of("points", points);
    }

    /**
     * CSV export — Finance/Admin only.
     * RateLimitFilter restricts /analytics/export/** to 10/min/user (60/min for everything else).
     */
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN')")
    public ResponseEntity<StreamingResponseBody> exportCsv(Authentication auth, HttpServletRequest request) {
        sensitiveAccessLogService.logAccess("analytics_export", "CouponRedemption", null,
                auth.getName(), clientIp(request));
        StreamingResponseBody body = exportService.exportRedemptionsCsv(auth.getName(), clientIp(request));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"redemptions.csv\"")
                .body(body);
    }

    private String clientIp(HttpServletRequest request) {
        String header = request.getHeader("X-Forwarded-For");
        if (header != null && !header.isBlank()) return header.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
