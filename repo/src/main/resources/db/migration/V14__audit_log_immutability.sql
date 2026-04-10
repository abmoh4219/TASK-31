-- HIGH #3 (R3): enforce audit_logs immutability at the database layer.
--
-- Even an operator with direct DB credentials must not be able to tamper with the
-- audit trail. The application layer already refuses mutation — no @Setter on the
-- entity, the repository extends Repository (not JpaRepository) and exposes only
-- insert + read — but that is bypassable by anyone with a mysql client. These triggers
-- are the bottom-layer backstop.
--
-- Any UPDATE or DELETE against audit_logs raises SQLSTATE 45000 with a clear message.
-- INSERTs continue to work normally, so AuditLogService.log(...) is unaffected.

DROP TRIGGER IF EXISTS prevent_audit_update;
DROP TRIGGER IF EXISTS prevent_audit_delete;

CREATE TRIGGER prevent_audit_update
    BEFORE UPDATE ON audit_logs
    FOR EACH ROW
    SIGNAL SQLSTATE '45000'
    SET MESSAGE_TEXT = 'audit_logs rows are immutable by policy';

CREATE TRIGGER prevent_audit_delete
    BEFORE DELETE ON audit_logs
    FOR EACH ROW
    SIGNAL SQLSTATE '45000'
    SET MESSAGE_TEXT = 'audit_logs rows are immutable by policy';
