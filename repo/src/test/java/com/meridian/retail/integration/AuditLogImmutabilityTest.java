package com.meridian.retail.integration;

import com.meridian.retail.audit.AuditAction;
import com.meridian.retail.audit.AuditLogService;
import com.meridian.retail.entity.AuditLog;
import com.meridian.retail.repository.AuditLogRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end proof that the audit log is immutable at the DATABASE layer.
 *
 * The application layer protections (no @Setter, restricted repository) are covered
 * by AuditLogServiceTest via reflection. This test goes further: it executes raw
 * native SQL UPDATE and DELETE statements against the live {@code audit_logs} table
 * and asserts that the MySQL triggers from V14 reject them.
 */
class AuditLogImmutabilityTest extends AbstractIntegrationTest {

    @Autowired AuditLogService auditLogService;
    @Autowired AuditLogRepository auditLogRepository;

    @PersistenceContext EntityManager em;

    @Test
    @Transactional
    void nativeUpdateOnAuditLogsIsRejectedByTrigger() {
        auditLogService.log(AuditAction.CAMPAIGN_CREATED, "ImmutabilityTest", 1L,
                null, Map.of("k", "v1"), "tester", "127.0.0.1");
        long count = auditLogRepository.count();
        assertThat(count).isPositive();

        // A raw UPDATE against the immutable table must be rejected by the trigger.
        assertThatThrownBy(() -> {
            em.createNativeQuery("UPDATE audit_logs SET action = 'TAMPERED' WHERE id = (SELECT * FROM (SELECT MAX(id) FROM audit_logs) x)")
                    .executeUpdate();
            em.flush();
        }).hasMessageContaining("immutable");
    }

    @Test
    @Transactional
    void nativeDeleteOnAuditLogsIsRejectedByTrigger() {
        auditLogService.log(AuditAction.CAMPAIGN_CREATED, "ImmutabilityTest", 2L,
                null, Map.of("k", "v2"), "tester", "127.0.0.1");

        assertThatThrownBy(() -> {
            em.createNativeQuery("DELETE FROM audit_logs WHERE id = (SELECT * FROM (SELECT MAX(id) FROM audit_logs) x)")
                    .executeUpdate();
            em.flush();
        }).hasMessageContaining("immutable");
    }
}
