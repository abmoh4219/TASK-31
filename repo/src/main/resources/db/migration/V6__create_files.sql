-- V6: Campaign attachments, chunked upload sessions, and temporary download links.
CREATE TABLE campaign_attachments (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    campaign_id         BIGINT NOT NULL,
    original_filename   VARCHAR(500) NOT NULL,
    stored_filename     VARCHAR(500) NOT NULL,
    stored_path         VARCHAR(1000) NOT NULL,
    file_type           VARCHAR(100),
    file_size_bytes     BIGINT NOT NULL,
    sha256_checksum     VARCHAR(64) NOT NULL,
    is_internal_only    BOOLEAN NOT NULL DEFAULT FALSE,
    masked_roles        JSON,
    version             INT NOT NULL DEFAULT 1,
    uploaded_by         VARCHAR(100) NOT NULL,
    uploaded_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_attach_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns(id) ON DELETE CASCADE,
    INDEX idx_attach_campaign (campaign_id),
    INDEX idx_attach_checksum (sha256_checksum)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE upload_sessions (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    upload_id           VARCHAR(36) NOT NULL UNIQUE,
    campaign_id         BIGINT NOT NULL,
    original_filename   VARCHAR(500) NOT NULL,
    total_chunks        INT NOT NULL,
    received_chunks     JSON,
    temp_dir            VARCHAR(1000) NOT NULL,
    status              ENUM('IN_PROGRESS','COMPLETE','FAILED') NOT NULL DEFAULT 'IN_PROGRESS',
    started_by          VARCHAR(100) NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_upload_session_uid (upload_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE temp_download_links (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    token           VARCHAR(36) NOT NULL UNIQUE,
    file_id         BIGINT NOT NULL,
    username        VARCHAR(100) NOT NULL,
    ip_address      VARCHAR(45),
    expires_at      TIMESTAMP NOT NULL,
    used_at         TIMESTAMP NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tdl_file FOREIGN KEY (file_id) REFERENCES campaign_attachments(id) ON DELETE CASCADE,
    INDEX idx_tdl_token (token),
    INDEX idx_tdl_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
