-- V5: Approval queue + dual approval requests for high-risk campaign edits.
CREATE TABLE approval_queue (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    campaign_id         BIGINT NOT NULL,
    requested_by        VARCHAR(100) NOT NULL,
    assigned_reviewer   VARCHAR(100),
    status              ENUM('PENDING','APPROVED','REJECTED','REQUIRES_DUAL') NOT NULL DEFAULT 'PENDING',
    risk_level          ENUM('LOW','MEDIUM','HIGH') NOT NULL DEFAULT 'LOW',
    notes               TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_approval_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns(id) ON DELETE CASCADE,
    INDEX idx_approval_status (status),
    INDEX idx_approval_reviewer (assigned_reviewer)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE dual_approval_requests (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    approval_queue_id       BIGINT NOT NULL,
    approver1_username      VARCHAR(100),
    approver2_username      VARCHAR(100),
    approver1_at            TIMESTAMP NULL,
    approver2_at            TIMESTAMP NULL,
    status                  ENUM('PENDING','COMPLETE') NOT NULL DEFAULT 'PENDING',
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dual_approval_queue FOREIGN KEY (approval_queue_id) REFERENCES approval_queue(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
