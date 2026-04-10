package com.meridian.retail.repository;

import com.meridian.retail.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Audit log repository — IMMUTABLE BY DESIGN.
 *
 * Extends Spring Data's {@link Repository} base (not {@link
 * org.springframework.data.jpa.repository.JpaRepository}) so that {@code delete*},
 * {@code deleteAll}, {@code saveAll} and other mutating helpers are NOT inherited.
 * Only insert ({@code save} of a new entity) and read operations are exposed. Any
 * attempt to mutate an existing row is additionally rejected by the
 * {@code prevent_audit_update} / {@code prevent_audit_delete} triggers from V14.
 *
 * {@code AuditLogServiceTest.repositoryExposesNoMutators} is the reflection-based
 * regression test for this policy.
 */
@org.springframework.stereotype.Repository
public interface AuditLogRepository extends org.springframework.data.repository.Repository<AuditLog, Long> {

    /** Insert-only. The parameter must be a fresh entity without an id. */
    <S extends AuditLog> S save(S entity);

    Optional<AuditLog> findById(Long id);

    long count();

    List<AuditLog> findAll();

    Page<AuditLog> findAll(Pageable pageable);

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
