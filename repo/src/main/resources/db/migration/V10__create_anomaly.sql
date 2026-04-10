-- V10: Change events (raw signal) and anomaly alerts (derived state).
CREATE TABLE change_events (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type      VARCHAR(100) NOT NULL,
    entity_type     VARCHAR(100),
    entity_id       BIGINT,
    username        VARCHAR(100),
    occurred_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata_json   JSON,
    INDEX idx_change_event_type (event_type),
    INDEX idx_change_when (occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE anomaly_alerts (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    alert_type          VARCHAR(100) NOT NULL,
    description         TEXT NOT NULL,
    severity            ENUM('LOW','MEDIUM','HIGH','CRITICAL') NOT NULL,
    detected_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acknowledged_by     VARCHAR(100),
    acknowledged_at     TIMESTAMP NULL,
    INDEX idx_anomaly_severity (severity),
    INDEX idx_anomaly_detected (detected_at),
    INDEX idx_anomaly_ack (acknowledged_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
