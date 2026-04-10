-- V9: Coupon redemptions and finance export logs.
CREATE TABLE coupon_redemptions (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    coupon_id           BIGINT NOT NULL,
    store_id            VARCHAR(100),
    redeemed_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    discount_applied    DECIMAL(10,2) NOT NULL,
    order_total         DECIMAL(10,2) NOT NULL,
    CONSTRAINT fk_redemption_coupon FOREIGN KEY (coupon_id) REFERENCES coupons(id) ON DELETE CASCADE,
    INDEX idx_redemption_store (store_id),
    INDEX idx_redemption_when (redeemed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE export_logs (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    exported_by         VARCHAR(100) NOT NULL,
    export_type         VARCHAR(100) NOT NULL,
    filters_applied     JSON,
    row_count           INT NOT NULL DEFAULT 0,
    file_path           VARCHAR(1000),
    exported_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address          VARCHAR(45),
    INDEX idx_export_user (exported_by),
    INDEX idx_export_when (exported_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
