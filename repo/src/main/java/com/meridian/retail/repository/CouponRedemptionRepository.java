package com.meridian.retail.repository;

import com.meridian.retail.entity.CouponRedemption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, Long> {

    @Query("select count(r) from CouponRedemption r where " +
           "(:storeId is null or r.storeId = :storeId) and " +
           "(:from is null or r.redeemedAt >= :from) and " +
           "(:to is null or r.redeemedAt <= :to)")
    long countRedemptions(@Param("storeId") String storeId,
                          @Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to);

    @Query("select coalesce(sum(r.discountApplied), 0) from CouponRedemption r where " +
           "(:from is null or r.redeemedAt >= :from) and " +
           "(:to is null or r.redeemedAt <= :to)")
    BigDecimal sumDiscountApplied(@Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to);

    @Query("select r.storeId, count(r) from CouponRedemption r where " +
           "(:from is null or r.redeemedAt >= :from) and " +
           "(:to is null or r.redeemedAt <= :to) group by r.storeId")
    List<Object[]> redemptionsByStore(@Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to);

    @Query("select r.couponId, count(r), coalesce(sum(r.discountApplied),0) " +
           "from CouponRedemption r where " +
           "(:storeId is null or r.storeId = :storeId) and " +
           "(:from is null or r.redeemedAt >= :from) and " +
           "(:to is null or r.redeemedAt <= :to) " +
           "group by r.couponId order by count(r) desc")
    List<Object[]> topCouponsByRedemptions(@Param("storeId") String storeId,
                                           @Param("from") LocalDateTime from,
                                           @Param("to") LocalDateTime to);

    /**
     * Top campaigns (not coupons) by redemption count. The coupon -> campaign join walks
     * through the Coupon entity; filters on store and date range flow down to the redemption.
     * Returns rows of (campaignId, redemptionCount, totalDiscount).
     */
    @Query("select c.campaignId, count(r), coalesce(sum(r.discountApplied),0) " +
           "from CouponRedemption r join Coupon c on r.couponId = c.id where " +
           "(:storeId is null or r.storeId = :storeId) and " +
           "(:from is null or r.redeemedAt >= :from) and " +
           "(:to is null or r.redeemedAt <= :to) " +
           "group by c.campaignId order by count(r) desc")
    List<Object[]> topCampaignsByRedemptions(@Param("storeId") String storeId,
                                             @Param("from") LocalDateTime from,
                                             @Param("to") LocalDateTime to);

    /**
     * Daily redemption trend rows (date, redemptionCount, totalDiscount). Uses MySQL DATE()
     * via a native query so we get a real DATE column instead of a timestamp.
     */
    @Query(value = "SELECT DATE(redeemed_at) as d, " +
                   "       COUNT(*) as cnt, " +
                   "       COALESCE(SUM(discount_applied), 0) as total " +
                   "FROM coupon_redemptions " +
                   "WHERE (:storeId IS NULL OR store_id = :storeId) " +
                   "  AND (:fromDate IS NULL OR redeemed_at >= :fromDate) " +
                   "  AND (:toDate   IS NULL OR redeemed_at <= :toDate) " +
                   "GROUP BY DATE(redeemed_at) " +
                   "ORDER BY DATE(redeemed_at) ASC",
           nativeQuery = true)
    List<Object[]> dailyRedemptionTrend(@Param("storeId") String storeId,
                                        @Param("fromDate") LocalDateTime fromDate,
                                        @Param("toDate") LocalDateTime toDate);
}
