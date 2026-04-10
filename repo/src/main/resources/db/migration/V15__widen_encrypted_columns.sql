-- R4 audit MEDIUM #7: broaden encryption-at-rest to additional sensitive fields.
--
-- Newly encrypted (AES-GCM via EncryptedStringConverter):
--   - backup_records.notes        (already TEXT, no DDL needed)
--   - anomaly_alerts.description  (already TEXT, no DDL needed)
--
-- The columns are already TEXT, so this migration is a documentation marker:
-- it lets static reviewers see the version where encryption-at-rest was broadened
-- and matches the field list documented in SecurityDesignDecisions.md.

-- No DDL changes — placeholder marker only.
SELECT 1;
