-- Dual-approval workflow for user role changes (HIGH-risk permission mutations).
-- An admin requests a role change via POST /admin/users/{id}/role-change-request.
-- A second admin records the first approval; a third (different) admin completes
-- the dual approval, which applies the role to the target user.
CREATE TABLE role_change_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    target_user_id BIGINT NOT NULL,
    target_username VARCHAR(100) NOT NULL,
    old_role ENUM('ADMIN','OPERATIONS','REVIEWER','FINANCE','CUSTOMER_SERVICE') NOT NULL,
    new_role ENUM('ADMIN','OPERATIONS','REVIEWER','FINANCE','CUSTOMER_SERVICE') NOT NULL,
    requested_by VARCHAR(100) NOT NULL,
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approver1_username VARCHAR(100) NULL,
    approver1_at TIMESTAMP NULL,
    approver2_username VARCHAR(100) NULL,
    approver2_at TIMESTAMP NULL,
    status ENUM('PENDING','FIRST_APPROVED','APPLIED','REJECTED') NOT NULL DEFAULT 'PENDING',
    applied_at TIMESTAMP NULL,
    INDEX idx_rcr_status (status),
    INDEX idx_rcr_target (target_user_id)
) ENGINE=InnoDB;
