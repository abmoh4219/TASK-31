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
}
