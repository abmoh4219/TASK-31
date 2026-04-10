-- V11: Backup records — nightly mysqldump scheduling and history.
CREATE TABLE backup_records (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    filename            VARCHAR(500) NOT NULL,
    file_path           VARCHAR(1000) NOT NULL,
    file_size_bytes     BIGINT NOT NULL DEFAULT 0,
    sha256_checksum     VARCHAR(64),
    status              ENUM('COMPLETE','FAILED','DELETED') NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    restored_at         TIMESTAMP NULL,
    notes               TEXT,
    INDEX idx_backup_status (status),
    INDEX idx_backup_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
