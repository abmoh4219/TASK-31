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
