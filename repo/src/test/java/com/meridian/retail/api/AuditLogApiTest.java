package com.meridian.retail.api;

import com.meridian.retail.audit.AuditAction;
import com.meridian.retail.audit.AuditLogService;
import com.meridian.retail.dto.CreateCampaignRequest;
import com.meridian.retail.entity.AuditLog;
import com.meridian.retail.entity.CampaignType;
import com.meridian.retail.entity.RiskLevel;
import com.meridian.retail.repository.AuditLogRepository;
import com.meridian.retail.service.CampaignService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Audit log API tests — covers both service-level audit trail assertions and
 * real HTTP endpoint access control for the audit log viewer.
 */
class AuditLogApiTest extends AbstractApiTest {

    @Autowired CampaignService campaignService;
    @Autowired AuditLogService auditLogService;
    @Autowired AuditLogRepository auditLogRepository;

    @PersistenceContext EntityManager em;

    // ---- Service-level audit trail tests ----

    @Test
    void campaignCreateProducesAuditEntryWithBeforeAndAfter() {
        long before = auditLogRepository.count();

        CreateCampaignRequest req = new CreateCampaignRequest();
        req.setName("Audit Trail API Test Campaign");
        req.setDescription("ensures audit row is written");
        req.setType(CampaignType.COUPON);
        req.setReceiptWording("AUDIT TEST");
        req.setStoreId("STORE-AUDIT-API");
        req.setRiskLevel(RiskLevel.LOW);
        req.setStartDate(LocalDate.now().plusDays(1));
        req.setEndDate(LocalDate.now().plusDays(5));

        var created = campaignService.createCampaign(req, "ops", "127.0.0.1");

        assertThat(auditLogRepository.count()).isGreaterThan(before);

        List<AuditLog> entries = auditLogRepository
                .findByEntityTypeAndEntityIdOrderByCreatedAtDesc("Campaign", created.getId());
        assertThat(entries).isNotEmpty();
        AuditLog latest = entries.get(0);
        assertThat(latest.getAction()).isEqualTo("CAMPAIGN_CREATED");
        assertThat(latest.getOperatorUsername()).isEqualTo("ops");
        assertThat(latest.getBeforeState()).isNull();
        assertThat(latest.getAfterState()).contains("Audit Trail API Test Campaign");
    }

    // ---- DB-level immutability tests (real MySQL triggers) ----

    @Test
    @Transactional
    void nativeUpdateOnAuditLogsIsRejectedByTrigger() {
        auditLogService.log(AuditAction.CAMPAIGN_CREATED, "ImmutabilityTest", 1L,
                null, Map.of("k", "v1"), "tester", "127.0.0.1");
        assertThat(auditLogRepository.count()).isPositive();

        assertThatThrownBy(() -> {
            em.createNativeQuery(
                    "UPDATE audit_logs SET action = 'TAMPERED' WHERE id = " +
                    "(SELECT * FROM (SELECT MAX(id) FROM audit_logs) x)")
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
            em.createNativeQuery(
                    "DELETE FROM audit_logs WHERE id = " +
                    "(SELECT * FROM (SELECT MAX(id) FROM audit_logs) x)")
                    .executeUpdate();
            em.flush();
        }).hasMessageContaining("immutable");
    }

    // ---- Real HTTP endpoint tests ----

    @Test
    void adminCanAccessAuditLogPage() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        ResponseEntity<String> resp = get("/admin/audit-log", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void opsCannotAccessAuditLogPage() {
        HttpHeaders h = loginAs("ops", "Ops@Retail2024!");
        ResponseEntity<String> resp = get("/admin/audit-log", h);
        assertThat(resp.getStatusCode().value()).isIn(302, 403);
    }

    @Test
    void adminCanAccessSensitiveAuditLogPage() {
        HttpHeaders h = loginAs("admin", "Admin@Retail2024!");
        ResponseEntity<String> resp = get("/admin/sensitive-log", h);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }
}
