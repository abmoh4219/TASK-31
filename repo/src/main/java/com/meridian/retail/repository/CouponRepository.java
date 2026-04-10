package com.meridian.retail.repository;

import com.meridian.retail.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {
    Optional<Coupon> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCase(String code);
    List<Coupon> findByCampaignId(Long campaignId);
    List<Coupon> findByMutualExclusionGroup(String group);

    /**
     * Filtered issuance: sum of coupon.maxUses where the parent campaign matches the
     * supplied store filter and the coupon's validity window overlaps the date range.
     * Returns 0 (not null) when no rows match.
     *
     * Note: "issuance" here means coupon distribution capacity (max_uses) — actual
     * per-customer issuance events do not exist in this schema, so max_uses is the
     * documented proxy. The metric is now filter-aware.
     */
    @Query("select coalesce(sum(c.maxUses), 0) " +
           "from Coupon c join Campaign ca on c.campaignId = ca.id " +
           "where (:storeId is null or ca.storeId = :storeId) " +
           "  and (:fromDate is null or c.validUntil is null or c.validUntil >= :fromDate) " +
           "  and (:toDate   is null or c.validFrom  is null or c.validFrom  <= :toDate)")
    long sumMaxUsesByCampaignFilters(@Param("storeId") String storeId,
                                     @Param("fromDate") LocalDate fromDate,
                                     @Param("toDate") LocalDate toDate);
}
