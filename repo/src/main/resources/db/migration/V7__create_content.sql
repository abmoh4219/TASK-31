-- V7: Content integrity — items, version chain, merge log.
CREATE TABLE content_items (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    campaign_id         BIGINT NULL,
    source_url          TEXT,
    normalized_url      VARCHAR(2048),
    title               VARCHAR(500),
    body_text           LONGTEXT,
    sha256_fingerprint  VARCHAR(64),
    sim_hash            BIGINT,
    status              ENUM('ACTIVE','DUPLICATE','MERGED') NOT NULL DEFAULT 'ACTIVE',
    master_id           BIGINT NULL,
    imported_by         VARCHAR(100) NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_content_sha (sha256_fingerprint),
    INDEX idx_content_simhash (sim_hash),
    INDEX idx_content_status (status),
    INDEX idx_content_master (master_id),
    CONSTRAINT fk_content_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE content_versions (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    content_id      BIGINT NOT NULL,
    version_num     INT NOT NULL,
    snapshot_json   LONGTEXT NOT NULL,
    changed_by      VARCHAR(100) NOT NULL,
    changed_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_versions_content FOREIGN KEY (content_id) REFERENCES content_items(id) ON DELETE CASCADE,
    INDEX idx_versions_content (content_id, version_num)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE content_merge_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    master_id       BIGINT NOT NULL,
    merged_ids      JSON NOT NULL,
    before_json     LONGTEXT,
    after_json      LONGTEXT,
    merged_by       VARCHAR(100) NOT NULL,
    merged_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_merge_master (master_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
