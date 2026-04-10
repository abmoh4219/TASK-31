# Fix Check Audit Report (Issue Revalidation)

**Source baseline:** `.tmp/audit_report-1.md`  
**Revalidation scope:** 8 previously reported issues (static code/config/test inspection only)  
**Date:** 2026-04-10

## Executive Summary

- **Fixed:** 6
- **Partially Fixed:** 2
- **Not Fixed:** 0
- **Cannot Confirm Statistically (static-only limit):** 0

## Revalidation Results

### 1) BLOCKER — Hardcoded secrets in runtime config and test script

**Status:** ✅ **Fixed**

**Evidence (static):**

- `src/main/resources/application.yml:21` → `password: ${DB_PASSWORD:${SPRING_DATASOURCE_PASSWORD:}}`
- `src/main/resources/application.yml:91` → `key: ${ENCRYPTION_KEY:${APP_ENCRYPTION_KEY:}}`
- `src/main/resources/application.yml:94` → `secret: ${HMAC_KEY:${APP_SIGNING_SECRET:}}`
- `run_tests.sh:17`, `run_tests.sh:33` now document unit vs integration modes clearly.

**Assessment:** No plaintext secret defaults are present in the inspected app config entries; secret sourcing is env-based.

---

### 2) BLOCKER — Sensitive backup path stored plaintext at rest

**Status:** ✅ **Fixed**

**Evidence (static):**

- `src/main/java/com/meridian/retail/security/EncryptionService.java:36` (AES-GCM service)
- `src/main/java/com/meridian/retail/security/EncryptionService.java:39` (`AES/GCM/NoPadding`)
- `src/main/java/com/meridian/retail/security/EncryptedStringConverter.java:21`
- `src/main/java/com/meridian/retail/entity/BackupRecord.java:31` (`@Convert(converter = EncryptedStringConverter.class)`)
- `src/test/java/com/meridian/retail/security/EncryptionServiceTest.java:11` (coverage exists)

**Assessment:** `BackupRecord.filePath` is wired to encrypted persistence via JPA converter.

---

### 3) BLOCKER — Nonce/signature bypass still applied too broadly to admin form submissions

**Status:** ✅ **Fixed**

**Evidence (static):**

- `src/main/java/com/meridian/retail/security/NonceValidationFilter.java:100` documents only `application/x-www-form-urlencoded` exemption
- `src/main/java/com/meridian/retail/security/NonceValidationFilter.java:110` checks `startsWith("application/x-www-form-urlencoded")`
- `src/main/java/com/meridian/retail/security/RequestSigningFilter.java:100` same narrowed condition
- `src/test/java/com/meridian/retail/security/AdminFilterBypassTest.java:59` multipart rejected by signing filter
- `src/test/java/com/meridian/retail/security/AdminFilterBypassTest.java:75` multipart rejected by nonce filter

**Assessment:** Exemption is narrowed; multipart is no longer implicitly bypassed.

---

### 4) BLOCKER — Audit log mutability via repository/entity/database gaps

**Status:** ✅ **Fixed**

**Evidence (static):**

- `src/main/java/com/meridian/retail/entity/AuditLog.java:24` entity present without mutable setter path; lifecycle via `@PrePersist` (`:54`)
- `src/main/java/com/meridian/retail/repository/AuditLogRepository.java:27` now extends restricted `Repository` (not broad mutable JPA interface)
- `src/main/resources/db/migration/V14__audit_log_immutability.sql:15` trigger `prevent_audit_update`
- `src/main/resources/db/migration/V14__audit_log_immutability.sql:21` trigger `prevent_audit_delete`
- `src/test/java/com/meridian/retail/integration/AuditLogImmutabilityTest.java:35` update blocked
- `src/test/java/com/meridian/retail/integration/AuditLogImmutabilityTest.java:51` delete blocked

**Assessment:** Java-layer and DB-layer immutability protections are both present.

---

### 5) HIGH — Backup command construction allowed shell interpolation/secrets exposure risk

**Status:** ✅ **Fixed**

**Evidence (static):**

- `src/main/java/com/meridian/retail/backup/BackupService.java:97` uses `ProcessBuilder`
- `src/main/java/com/meridian/retail/backup/BackupService.java:103` uses `MYSQL_PWD` env var
- `src/main/java/com/meridian/retail/backup/BackupService.java:95` comment explicitly avoids `/bin/sh -c`
- `src/main/java/com/meridian/retail/backup/BackupService.java:157` safe builder helper exists
- `src/test/java/com/meridian/retail/backup/BackupCommandSafetyTest.java:15` safety test class present

**Assessment:** Shell-wrapper approach appears removed from current backup execution path; password is no longer passed as CLI arg in the inspected builder path.

---

### 6) MEDIUM — Missing explicit test for rate-limit 429 + Retry-After behavior

**Status:** 🟡 **Partially Fixed**

**Evidence (static):**

- `src/test/java/com/meridian/retail/security/RateLimitFilterTest.java:29` test class added
- `src/test/java/com/meridian/retail/security/RateLimitFilterTest.java:54` overflow scenario test
- `src/test/java/com/meridian/retail/security/RateLimitFilterTest.java:70` asserts `Retry-After`
- `src/test/java/com/meridian/retail/security/RateLimitFilterTest.java:89` additional `Retry-After` assertion

**Assessment:** Behavior is now covered at filter-unit level. Remaining gap vs strict prior wording is lack of explicit full request-chain integration assertion for this specific header behavior.

---

### 7) MEDIUM — Missing nonce/signature test matrix for edge scenarios through request flow

**Status:** 🟡 **Partially Fixed**

**Evidence (static):**

- `src/test/java/com/meridian/retail/security/NonceSignatureMatrixTest.java:39` matrix class exists
- `src/test/java/com/meridian/retail/security/NonceSignatureMatrixTest.java:35` states filters are tested directly (not full MockMvc chain)
- `src/test/java/com/meridian/retail/security/AdminFilterBypassTest.java:59`, `:75` add multipart rejection checks in request-style tests

**Assessment:** Core matrix scenarios are added, but most are filter-direct tests rather than end-to-end route-chain integration tests.

---

### 8) HIGH — No restore drill capability / no test-restore implementation

**Status:** ✅ **Fixed**

**Evidence (static):**

- `src/main/java/com/meridian/retail/backup/RestoreService.java:39` restore service added
- `src/main/java/com/meridian/retail/backup/RestoreService.java:60` real restore path
- `src/main/java/com/meridian/retail/backup/RestoreService.java:100` test-restore path
- `src/main/java/com/meridian/retail/controller/AdminController.java:133` `/backup/test-restore`
- `src/main/java/com/meridian/retail/controller/AdminController.java:150` `/backup/{id}/restore`
- `src/main/resources/templates/admin/backup.html:20` test-restore action in admin UI
- `src/test/java/com/meridian/retail/backup/RestoreServiceTest.java:37` service tests present

**Assessment:** Restore and test-restore capabilities are now implemented and wired through controller + UI + tests.

---

## Final Verdict

Compared to `.tmp/audit_report-1.md`, the repository now shows **substantial remediation** across all 8 issues in static inspection.

- **Resolved to Fixed:** #1, #2, #3, #4, #5, #8
- **Improved but not fully closed against stricter integration-test interpretation:** #6, #7

No additional `.tmp` files were modified during this fix-check output generation.
