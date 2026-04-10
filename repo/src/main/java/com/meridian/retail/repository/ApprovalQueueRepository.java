package com.meridian.retail.repository;

import com.meridian.retail.entity.ApprovalQueue;
import com.meridian.retail.entity.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ApprovalQueueRepository extends JpaRepository<ApprovalQueue, Long> {
    List<ApprovalQueue> findByStatusOrderByCreatedAtAsc(ApprovalStatus status);
    List<ApprovalQueue> findByStatusInOrderByCreatedAtAsc(Collection<ApprovalStatus> statuses);
    long countByStatus(ApprovalStatus status);
    List<ApprovalQueue> findByAssignedReviewer(String reviewer);
    List<ApprovalQueue> findByCampaignIdOrderByCreatedAtDesc(Long campaignId);
}
