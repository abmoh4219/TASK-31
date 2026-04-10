-- V3: Campaigns — coupons + limited-time discounts.
CREATE TABLE campaigns (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                VARCHAR(255) NOT NULL,
    description         TEXT,
    type                ENUM('COUPON','DISCOUNT') NOT NULL,
    status              ENUM('DRAFT','PENDING_REVIEW','APPROVED','ACTIVE','EXPIRED','REJECTED') NOT NULL DEFAULT 'DRAFT',
    receipt_wording     TEXT,
    store_id            VARCHAR(100),
    risk_level          ENUM('LOW','MEDIUM','HIGH') NOT NULL DEFAULT 'LOW',
    created_by          VARCHAR(100) NOT NULL,
    start_date          DATE,
    end_date            DATE,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at          TIMESTAMP NULL,
    INDEX idx_campaigns_status (status),
    INDEX idx_campaigns_store (store_id),
    INDEX idx_campaigns_dates (start_date, end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
