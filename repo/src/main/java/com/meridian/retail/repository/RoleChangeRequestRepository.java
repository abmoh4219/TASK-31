package com.meridian.retail.repository;

import com.meridian.retail.entity.RoleChangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface RoleChangeRequestRepository extends JpaRepository<RoleChangeRequest, Long> {
    List<RoleChangeRequest> findByStatusInOrderByRequestedAtDesc(Collection<RoleChangeRequest.RoleChangeStatus> statuses);
    List<RoleChangeRequest> findAllByOrderByRequestedAtDesc();
}
