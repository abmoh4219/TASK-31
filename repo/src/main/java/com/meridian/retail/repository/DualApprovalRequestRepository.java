package com.meridian.retail.repository;

import com.meridian.retail.entity.DualApprovalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DualApprovalRequestRepository extends JpaRepository<DualApprovalRequest, Long> {
    Optional<DualApprovalRequest> findByApprovalQueueId(Long approvalQueueId);
}
