-- V8: Audit logs (immutable — no updated_at column) + sensitive field access logs.
CREATE TABLE audit_logs (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    action              VARCHAR(100) NOT NULL,
    entity_type         VARCHAR(100),
    entity_id           BIGINT,
    operator_username   VARCHAR(100),
    ip_address          VARCHAR(45),
    before_state        LONGTEXT,
    after_state         LONGTEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- IMMUTABLE: NO updated_at column. Audit log records are written once and never modified.
    INDEX idx_audit_action (action),
    INDEX idx_audit_entity (entity_type, entity_id),
    INDEX idx_audit_operator (operator_username),
    INDEX idx_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sensitive_access_logs (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    accessor_username   VARCHAR(100) NOT NULL,
    field_name          VARCHAR(200) NOT NULL,
    entity_type         VARCHAR(100),
    entity_id           BIGINT,
    ip_address          VARCHAR(45),
    accessed_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_sensitive_accessor (accessor_username),
    INDEX idx_sensitive_accessed (accessed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
