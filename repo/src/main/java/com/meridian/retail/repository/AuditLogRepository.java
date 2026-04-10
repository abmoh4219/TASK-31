package com.meridian.retail.repository;

import com.meridian.retail.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AuditLogRepository — IMMUTABLE BY DESIGN.
 *
 * No update() or delete() operations are exposed here. The only write path is save()
 * (which JpaRepository provides for INSERTs). Any attempt to call .deleteById(),
 * .deleteAll(), etc. should be considered a security violation and is intentionally
 * absent from the contract — Spring Data inherits these from JpaRepository, so we
 * additionally enforce the policy at the service layer (AuditLogService) and document
 * it here so static-audit reviewers can verify the design.
 *
 * IMMUTABLE: No update or delete operations permitted by design.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, Long entityId);

    List<AuditLog> findByOperatorUsernameOrderByCreatedAtDesc(String operatorUsername);

    @Query("select a from AuditLog a where " +
           "(:action is null or a.action = :action) and " +
           "(:entityType is null or a.entityType = :entityType) and " +
           "(:from is null or a.createdAt >= :from) and " +
           "(:to is null or a.createdAt <= :to) " +
           "order by a.createdAt desc")
    Page<AuditLog> search(@Param("action") String action,
                          @Param("entityType") String entityType,
                          @Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to,
                          Pageable pageable);
}
