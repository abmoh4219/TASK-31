package com.meridian.retail.service;

import com.meridian.retail.anomaly.ChangeEventService;
import com.meridian.retail.audit.AuditAction;
import com.meridian.retail.audit.AuditLogService;
import com.meridian.retail.dto.CampaignDTO;
import com.meridian.retail.dto.CreateCampaignRequest;
import com.meridian.retail.entity.Campaign;
import com.meridian.retail.entity.CampaignStatus;
import com.meridian.retail.entity.CampaignType;
import com.meridian.retail.entity.DiscountType;
import com.meridian.retail.repository.CampaignRepository;
import com.meridian.retail.security.XssInputSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Owns ALL campaign business rules. Controllers must remain thin — every guard,
 * validation, status transition and audit log call lives here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final AuditLogService auditLogService;
    private final ChangeEventService changeEventService;

    // ---------- Read ----------

    public List<Campaign> listAll() {
        return campaignRepository.findByDeletedAtIsNullOrderByCreatedAtDesc();
    }

    public List<Campaign> listByCreator(String username) {
        return campaignRepository.findByCreatedByAndDeletedAtIsNullOrderByCreatedAtDesc(username);
    }

    public List<Campaign> search(CampaignStatus status, CampaignType type) {
        return campaignRepository.search(status, type);
    }

    public Campaign findById(Long id) {
        return campaignRepository.findById(id)
                .orElseThrow(() -> new CampaignValidationException("Campaign not found: " + id));
    }

    // ---------- Create / Update ----------

    @Transactional
    public Campaign createCampaign(CreateCampaignRequest req, String username, String ipAddress) {
        validateDateRange(req.getStartDate(), req.getEndDate());

        Campaign c = Campaign.builder()
                .name(XssInputSanitizer.sanitize(req.getName()))
                .description(XssInputSanitizer.sanitize(req.getDescription()))
                .type(req.getType())
                .status(CampaignStatus.DRAFT)
                .receiptWording(XssInputSanitizer.sanitize(req.getReceiptWording()))
                .storeId(XssInputSanitizer.sanitize(req.getStoreId()))
                .riskLevel(req.getRiskLevel())
                .createdBy(username)
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
                .build();
        Campaign saved = campaignRepository.save(c);
        // AUDIT: campaign creation must always leave a record.
        auditLogService.log(AuditAction.CAMPAIGN_CREATED, "Campaign", saved.getId(),
                null, CampaignDTO.from(saved), username, ipAddress);
        return saved;
    }

    @Transactional
    public Campaign updateCampaign(Long id, CreateCampaignRequest req, String username, String ipAddress) {
        Campaign existing = findById(id);
        CampaignDTO before = CampaignDTO.from(existing);

        validateDateRange(req.getStartDate(), req.getEndDate());

        existing.setName(XssInputSanitizer.sanitize(req.getName()));
        existing.setDescription(XssInputSanitizer.sanitize(req.getDescription()));
        existing.setType(req.getType());
        existing.setReceiptWording(XssInputSanitizer.sanitize(req.getReceiptWording()));
        existing.setStoreId(XssInputSanitizer.sanitize(req.getStoreId()));
        existing.setRiskLevel(req.getRiskLevel());
        existing.setStartDate(req.getStartDate());
        existing.setEndDate(req.getEndDate());

        Campaign saved = campaignRepository.save(existing);
        // AUDIT: include both before and after for tamper-evident change tracking.
        auditLogService.log(AuditAction.CAMPAIGN_UPDATED, "Campaign", saved.getId(),
                before, CampaignDTO.from(saved), username, ipAddress);
        return saved;
    }

    @Transactional
    public Campaign submitForReview(Long id, String username, String ipAddress) {
        Campaign c = findById(id);
        if (c.getStatus() != CampaignStatus.DRAFT) {
            throw new CampaignValidationException("Only DRAFT campaigns can be submitted for review");
        }
        CampaignDTO before = CampaignDTO.from(c);
        c.setStatus(CampaignStatus.PENDING_REVIEW);
        Campaign saved = campaignRepository.save(c);
        auditLogService.log(AuditAction.CAMPAIGN_STATUS_CHANGED, "Campaign", saved.getId(),
                before, CampaignDTO.from(saved), username, ipAddress);
        return saved;
    }

    @Transactional
    public Campaign publishCampaign(Long id, String username, String ipAddress) {
        Campaign c = findById(id);
        if (c.getStatus() != CampaignStatus.APPROVED) {
            throw new CampaignValidationException("Only APPROVED campaigns can be published");
        }
        CampaignDTO before = CampaignDTO.from(c);
        c.setStatus(CampaignStatus.ACTIVE);
        Campaign saved = campaignRepository.save(c);
        auditLogService.log(AuditAction.CAMPAIGN_STATUS_CHANGED, "Campaign", saved.getId(),
                before, CampaignDTO.from(saved), username, ipAddress);
        return saved;
    }

    @Transactional
    public Campaign expireCampaign(Long id, String username, String ipAddress) {
        Campaign c = findById(id);
        CampaignDTO before = CampaignDTO.from(c);
        c.setStatus(CampaignStatus.EXPIRED);
        Campaign saved = campaignRepository.save(c);
        auditLogService.log(AuditAction.CAMPAIGN_STATUS_CHANGED, "Campaign", saved.getId(),
                before, CampaignDTO.from(saved), username, ipAddress);
        return saved;
    }

    @Transactional
    public void softDelete(Long id, String username, String ipAddress) {
        Campaign c = findById(id);
        CampaignDTO before = CampaignDTO.from(c);
        c.setDeletedAt(LocalDateTime.now());
        campaignRepository.save(c);
        auditLogService.log(AuditAction.CAMPAIGN_DELETED, "Campaign", c.getId(),
                before, null, username, ipAddress);
        // Emit a DELETE change event so AnomalyDetectionService can spot a burst of
        // deletions (mass-delete rule). Distinct from the audit log, which stores diffs.
        changeEventService.record("DELETE", "Campaign", c.getId(), username);
    }

    // ---------- Validation rules (Task 3.3) ----------

    /** Throws if end is on or before start, or if start is in the past. */
    public void validateDateRange(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new CampaignValidationException("Start and end dates are required");
        }
        if (!end.isAfter(start)) {
            throw new CampaignValidationException("End date must be strictly after start date");
        }
        if (start.isBefore(LocalDate.now())) {
            throw new CampaignValidationException("Start date cannot be in the past");
        }
    }

    /**
     * Throws if discount value is non-positive, or if a PERCENT discount exceeds 100.
     * Used by both CampaignService and CouponService for consistency.
     */
    public void validateDiscountValue(DiscountType type, BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new CampaignValidationException("Discount value must be greater than zero");
        }
        if (type == DiscountType.PERCENT && value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new CampaignValidationException("Percent discount cannot exceed 100%");
        }
    }
}
