-- V4: Coupons — child of campaigns. Stacking + mutual exclusion rules.
CREATE TABLE coupons (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    campaign_id             BIGINT NOT NULL,
    code                    VARCHAR(50) NOT NULL UNIQUE,
    discount_type           ENUM('PERCENT','FIXED') NOT NULL,
    discount_value          DECIMAL(10,2) NOT NULL,
    min_purchase_amount     DECIMAL(10,2) NOT NULL DEFAULT 0,
    max_uses                INT NOT NULL DEFAULT 0,
    uses_count              INT NOT NULL DEFAULT 0,
    is_stackable            BOOLEAN NOT NULL DEFAULT FALSE,
    mutual_exclusion_group  VARCHAR(100),
    valid_from              DATE,
    valid_until             DATE,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_coupons_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns(id) ON DELETE CASCADE,
    INDEX idx_coupons_code (code),
    INDEX idx_coupons_exclusion (mutual_exclusion_group)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
