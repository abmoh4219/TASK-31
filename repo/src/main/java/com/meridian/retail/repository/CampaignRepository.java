package com.meridian.retail.repository;

import com.meridian.retail.entity.Campaign;
import com.meridian.retail.entity.CampaignStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, Long> {
    List<Campaign> findByDeletedAtIsNullOrderByCreatedAtDesc();
    List<Campaign> findByCreatedByAndDeletedAtIsNullOrderByCreatedAtDesc(String createdBy);
    List<Campaign> findByStatusAndDeletedAtIsNull(CampaignStatus status);
    long countByStatusAndDeletedAtIsNull(CampaignStatus status);

    @Query("select c from Campaign c where c.deletedAt is null and " +
           "(:status is null or c.status = :status) and " +
           "(:type is null or c.type = :type) order by c.createdAt desc")
    List<Campaign> search(@Param("status") CampaignStatus status,
                          @Param("type") com.meridian.retail.entity.CampaignType type);
}
