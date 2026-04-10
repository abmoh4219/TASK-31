package com.meridian.retail.integration;

import com.meridian.retail.dto.CreateCampaignRequest;
import com.meridian.retail.entity.AuditLog;
import com.meridian.retail.entity.Campaign;
import com.meridian.retail.entity.CampaignType;
import com.meridian.retail.entity.RiskLevel;
import com.meridian.retail.repository.AuditLogRepository;
import com.meridian.retail.service.CampaignService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogIntegrationTest extends AbstractIntegrationTest {

    @Autowired CampaignService campaignService;
    @Autowired AuditLogRepository auditLogRepository;

    @Test
    void campaignCreateProducesAuditEntryWithBeforeAndAfter() {
        long before = auditLogRepository.count();

        CreateCampaignRequest req = new CreateCampaignRequest();
        req.setName("Audit Trail Test Campaign");
        req.setDescription("ensures the audit row is written");
        req.setType(CampaignType.COUPON);
        req.setReceiptWording("AUDIT TEST");
        req.setStoreId("STORE-AUDIT");
        req.setRiskLevel(RiskLevel.LOW);
        req.setStartDate(LocalDate.now().plusDays(1));
        req.setEndDate(LocalDate.now().plusDays(5));

        Campaign created = campaignService.createCampaign(req, "ops", "127.0.0.1");

        long after = auditLogRepository.count();
        assertThat(after).isGreaterThan(before);

        List<AuditLog> entries = auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("Campaign", created.getId());
        assertThat(entries).isNotEmpty();
        AuditLog latest = entries.get(0);
        assertThat(latest.getAction()).isEqualTo("CAMPAIGN_CREATED");
        assertThat(latest.getOperatorUsername()).isEqualTo("ops");
        assertThat(latest.getBeforeState()).isNull();
        assertThat(latest.getAfterState()).contains("Audit Trail Test Campaign");
    }
}
