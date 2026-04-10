-- V2: Login attempts (account + IP lockout tracking) and used nonces (anti-replay).
CREATE TABLE login_attempts (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(100) NOT NULL,
    ip_address      VARCHAR(45) NOT NULL,
    success         BOOLEAN NOT NULL DEFAULT FALSE,
    attempted_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_login_attempts_username_time (username, attempted_at),
    INDEX idx_login_attempts_ip_time (ip_address, attempted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE used_nonces (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    nonce           VARCHAR(128) NOT NULL UNIQUE,
    used_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      TIMESTAMP NOT NULL,
    INDEX idx_used_nonces_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
