package com.meridian.retail.audit;

/**
 * Enumerated audit actions. Each value represents a critical operation that must
 * leave an immutable record in audit_logs. Adding a new operation? Add a constant
 * here, then call AuditLogService.log(...) from the service method that performs it.
 */
public enum AuditAction {
    CAMPAIGN_CREATED,
    CAMPAIGN_UPDATED,
    CAMPAIGN_DELETED,
    CAMPAIGN_STATUS_CHANGED,
    COUPON_CREATED,
    COUPON_UPDATED,
    APPROVAL_SUBMITTED,
    APPROVAL_APPROVED,
    APPROVAL_REJECTED,
    DUAL_APPROVAL_FIRST,
    DUAL_APPROVAL_SECOND,
    FILE_UPLOADED,
    FILE_DOWNLOADED,
    FILE_MASKED_ACCESS,
    CONTENT_IMPORTED,
    CONTENT_MERGED,
    CONTENT_ROLLED_BACK,
    USER_CREATED,
    USER_UPDATED,
    USER_DEACTIVATED,
    USER_ROLE_CHANGED,
    EXPORT_GENERATED,
    BACKUP_RUN
}
