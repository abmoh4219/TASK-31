package com.meridian.retail.integration;

import com.meridian.retail.dto.CreateCampaignRequest;
import com.meridian.retail.entity.ApprovalStatus;
import com.meridian.retail.entity.Campaign;
import com.meridian.retail.entity.CampaignStatus;
import com.meridian.retail.entity.CampaignType;
import com.meridian.retail.entity.RiskLevel;
import com.meridian.retail.repository.AuditLogRepository;
import com.meridian.retail.service.ApprovalService;
import com.meridian.retail.service.CampaignService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CampaignIntegrationTest extends AbstractIntegrationTest {

    @Autowired CampaignService campaignService;
    @Autowired ApprovalService approvalService;
    @Autowired AuditLogRepository auditLogRepository;

    @Test
    void createSubmitApproveFlow() {
        long auditBefore = auditLogRepository.count();

        CreateCampaignRequest req = new CreateCampaignRequest();
        req.setName("Integration Test Campaign");
        req.setDescription("created via integration test");
        req.setType(CampaignType.COUPON);
        req.setReceiptWording("TEST RECEIPT");
        req.setStoreId("STORE-IT-1");
        req.setRiskLevel(RiskLevel.LOW);
        req.setStartDate(LocalDate.now().plusDays(1));
        req.setEndDate(LocalDate.now().plusDays(10));

        Campaign created = campaignService.createCampaign(req, "ops", "127.0.0.1");
        assertThat(created.getId()).isNotNull();
        assertThat(created.getStatus()).isEqualTo(CampaignStatus.DRAFT);

        Campaign submitted = campaignService.submitForReview(created.getId(), "ops", "127.0.0.1");
        assertThat(submitted.getStatus()).isEqualTo(CampaignStatus.PENDING_REVIEW);

        var queueEntry = approvalService.submitToQueue(
                submitted.getId(), "ops", RiskLevel.LOW, "127.0.0.1");
        assertThat(queueEntry.getStatus()).isEqualTo(ApprovalStatus.PENDING);

        var approved = approvalService.approve(queueEntry.getId(), "reviewer", "looks fine", "127.0.0.1");
        assertThat(approved.getStatus()).isEqualTo(ApprovalStatus.APPROVED);

        Campaign reloaded = campaignService.findById(created.getId());
        assertThat(reloaded.getStatus()).isEqualTo(CampaignStatus.APPROVED);

        // Audit trail should have grown by at least: created, status_changed, approval_submitted, approval_approved
        assertThat(auditLogRepository.count() - auditBefore).isGreaterThanOrEqualTo(4);
    }
}
