package com.meridian.retail.service;

import com.meridian.retail.repository.CampaignRepository;
import com.meridian.retail.repository.CouponRedemptionRepository;
import com.meridian.retail.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Read-only analytics aggregations for the Finance / Admin dashboard and CSV export.
 *
 * Every method uses JPQL with named parameters (no string concatenation) so SQL injection
 * is structurally impossible.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final CouponRedemptionRepository redemptionRepository;
    private final CouponRepository couponRepository;
    private final CampaignRepository campaignRepository;

    public record CouponStats(long issuanceCount, long redemptionCount) {}
    public record DiscountStats(BigDecimal totalDiscountGiven, BigDecimal avgOrderTotal) {}
    public record TopCampaign(Long couponId, String code, long redemptions, BigDecimal totalDiscount) {}
    public record CampaignMetric(Long campaignId, String campaignName, long redemptions, BigDecimal totalDiscount) {}
    public record TrendPoint(String date, long redemptions, BigDecimal totalDiscount) {}

    public CouponStats getCouponStats(String storeId, LocalDateTime from, LocalDateTime to) {
        long redemptionCount = redemptionRepository.countRedemptions(storeId, from, to);
        long issuance = couponRepository.findAll().stream().mapToInt(c -> c.getMaxUses()).sum();
        return new CouponStats(issuance, redemptionCount);
    }

    public DiscountStats getDiscountUtilization(LocalDateTime from, LocalDateTime to) {
        BigDecimal total = redemptionRepository.sumDiscountApplied(from, to);
        if (total == null) total = BigDecimal.ZERO;
        return new DiscountStats(total, BigDecimal.ZERO);
    }

    /**
     * Campaign-level performance ranking (requirement: "top performing campaigns by
     * store and date range"). Unlike getTopCampaigns — which is actually coupon-centric —
     * this rolls redemptions up to the parent campaign via the coupon → campaign join.
     */
    public List<CampaignMetric> getTopCampaignsByCampaign(String storeId, LocalDateTime from,
                                                          LocalDateTime to, int limit) {
        List<Object[]> rows = redemptionRepository.topCampaignsByRedemptions(storeId, from, to);
        List<CampaignMetric> out = new ArrayList<>();
        int taken = 0;
        for (Object[] row : rows) {
            if (taken >= limit) break;
            Long campaignId = ((Number) row[0]).longValue();
            long count = ((Number) row[1]).longValue();
            BigDecimal sum = (BigDecimal) row[2];
            String name = campaignRepository.findById(campaignId).map(c -> c.getName()).orElse("Campaign #" + campaignId);
            out.add(new CampaignMetric(campaignId, name, count, sum == null ? BigDecimal.ZERO : sum));
            taken++;
        }
        return out;
    }

    /**
     * Daily redemption trend — used by the line chart on the analytics dashboard.
     * Date bucket is MySQL DATE(redeemed_at). Returned in ascending date order.
     */
    public List<TrendPoint> getDailyTrend(String storeId, LocalDateTime from, LocalDateTime to) {
        List<Object[]> rows = redemptionRepository.dailyRedemptionTrend(storeId, from, to);
        List<TrendPoint> out = new ArrayList<>();
        for (Object[] row : rows) {
            String date = row[0] == null ? "" : row[0].toString();
            long cnt = ((Number) row[1]).longValue();
            BigDecimal total = row[2] == null ? BigDecimal.ZERO : new BigDecimal(row[2].toString());
            out.add(new TrendPoint(date, cnt, total));
        }
        return out;
    }

    public List<TopCampaign> getTopCampaigns(String storeId, LocalDateTime from, LocalDateTime to, int limit) {
        List<Object[]> rows = redemptionRepository.topCouponsByRedemptions(storeId, from, to);
        List<TopCampaign> out = new ArrayList<>();
        int taken = 0;
        for (Object[] row : rows) {
            if (taken >= limit) break;
            Long couponId = ((Number) row[0]).longValue();
            long count = ((Number) row[1]).longValue();
            BigDecimal sum = (BigDecimal) row[2];
            String code = couponRepository.findById(couponId).map(c -> c.getCode()).orElse("?");
            out.add(new TopCampaign(couponId, code, count, sum == null ? BigDecimal.ZERO : sum));
            taken++;
        }
        return out;
    }
}
