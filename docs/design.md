# design.md — Retail Campaign Governance & Content Integrity
Task ID: TASK-31

This document is generated from the actual implemented code at the end of Phase 8.
Every reference below points at a real class, method, migration file or template
that exists in `repo/src/`.

---

## 1. Architecture

```
                          ┌──────────────────────────┐
                          │        Browser           │
                          │  Thymeleaf + Bootstrap 5 │
                          │  HTMX + Chart.js (local) │
                          └────────────┬─────────────┘
                                       │ HTTP(S), cookies, CSRF header
                                       ▼
┌──────────────────────────────────────────────────────────────────────┐
│                     Spring Boot 3.2.5 (Java 17)                       │
│                                                                        │
│  ┌─────────────────┐  ┌─────────────────┐  ┌────────────────────┐    │
│  │  RateLimit      │→ │ NonceValidation │→ │  RequestSigning    │    │
│  │  Filter         │  │ Filter          │  │  Filter            │    │
│  │  (Bucket4j)     │  │ (POST /admin,   │  │  (POST /admin)     │    │
│  │                 │  │  dual-approve)  │  │  HMAC-SHA256       │    │
│  └─────────────────┘  └─────────────────┘  └────────────────────┘    │
│          ↓                    ↓                      ↓                 │
│      Spring Security: CSRF, Session, FormLogin, @PreAuthorize          │
│          ↓                                                             │
│  @Controller (thin: parse request → call service → render template)   │
│          ↓                                                             │
│  @Service (business rules, validation, XSS sanitize, audit logging)   │
│          ↓                                                             │
│  @Repository (Spring Data JPA — JPQL with named params, no concat)     │
│          ↓                                                             │
│  Hibernate / JDBC / HikariCP                                           │
└──────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
                          ┌──────────────────────────┐
                          │       MySQL 8.0          │
                          │  Flyway-managed schema   │
                          │  (12 migrations)         │
                          └──────────────────────────┘
```

---

## 2. Docker Service Map

| Service          | Image                                     | Port | Depends on           | Volume               |
|------------------|-------------------------------------------|------|----------------------|----------------------|
| `setup`          | `alpine:3.18`                             | —    | —                    | bind mount `.:/workspace` (creates .env on first run) |
| `mysql`          | `mysql:8.0`                               | 3306 (internal) | `setup`   | `mysql-data:/var/lib/mysql` |
| `app`            | built from `Dockerfile` (eclipse-temurin:17-jre-alpine) | 8080 | `mysql` (healthy), `setup` | `upload-data:/app/uploads`, `backup-data:/app/backups` |

**Test compose** adds:
| Service          | Image                                     | Purpose                          |
|------------------|-------------------------------------------|----------------------------------|
| `mysql-test`     | `mysql:8.0`                               | Fresh DB per test run (DB `retail_campaign_test`) |
| `test`           | built from `Dockerfile.test` (maven:3.9-eclipse-temurin-17-alpine) | Runs `run_tests.sh` → unit + integration phases |

**First-run flow**: `setup` detects missing `.env`, copies `.env.example → .env`, exits 0. `mysql` and `app` then start using values from `.env` (or `${VAR:-default}` substitution if `.env` is still absent).

---

## 3. JPA Entities (20) and Key Relationships

```
 users                           login_attempts ─ used_nonces
  │                                                │
  │ created_by                                     │
  ▼                                                │
 campaigns ─────────────────────────────────────┐  │
  │  │  │                                       │  │
  │  │  ├──▶ approval_queue ─▶ dual_approval_requests
  │  │  │                                       │
  │  │  ├──▶ campaign_attachments ─▶ temp_download_links
  │  │  │        (+ upload_sessions in-flight)   │
  │  │  │                                        │
  │  │  └──▶ content_items ─▶ content_versions
  │  │           │           ─▶ content_merge_log
  │  │           └── master_id self-FK
  │  │
  │  └──▶ coupons ─▶ coupon_redemptions
  │
  │ (no FK, operator-indexed)
  ▼
 audit_logs  (immutable — no @PreUpdate, no updated_at)
 sensitive_access_logs
 export_logs
 change_events    ─▶ anomaly_alerts (derived by AnomalyDetectionService)
 backup_records
```

**Entity → Migration → Table mapping** (all under `repo/src/main/java/com/meridian/retail/entity/`):

| Entity                  | Migration | Table                   | Notes |
|-------------------------|-----------|-------------------------|-------|
| `User`                  | V1        | `users`                 | Role enum, `last_login_at` |
| `LoginAttempt`          | V2        | `login_attempts`        | Drives lockout |
| `UsedNonce`             | V2        | `used_nonces`           | Anti-replay, 10-min TTL |
| `Campaign`              | V3        | `campaigns`             | Soft delete, risk_level |
| `Coupon`                | V4        | `coupons`               | Stacking + mutual exclusion |
| `ApprovalQueue`         | V5        | `approval_queue`        | Lifecycle state machine |
| `DualApprovalRequest`   | V5        | `dual_approval_requests`| Two-eyes enforcement |
| `CampaignAttachment`    | V6        | `campaign_attachments`  | SHA-256 + `masked_roles` JSON |
| `UploadSession`         | V6        | `upload_sessions`       | Chunked upload state |
| `TempDownloadLink`      | V6        | `temp_download_links`   | UUID, 10-min expiry |
| `ContentItem`           | V7        | `content_items`         | `sim_hash BIGINT`, `sha256_fingerprint` |
| `ContentVersion`        | V7        | `content_versions`      | Snapshot JSON per edit |
| `ContentMergeLog`       | V7        | `content_merge_log`     | Before/after diff JSON |
| `AuditLog`              | V8        | `audit_logs`            | **IMMUTABLE** — no update path |
| `SensitiveAccessLog`    | V8        | `sensitive_access_logs` | Admin-only view |
| `CouponRedemption`      | V9        | `coupon_redemptions`    | Analytics source |
| `ExportLog`             | V9        | `export_logs`           | Finance export audit |
| `ChangeEvent`           | V10       | `change_events`         | Raw anomaly signal |
| `AnomalyAlert`          | V10       | `anomaly_alerts`        | Derived severity alerts |
| `BackupRecord`          | V11       | `backup_records`        | Nightly backup history |

---

## 4. Security Architecture

### 4.1 Authentication Flow

```
GET /login           → LoginController.login()
                       (reads ?error|locked|ipblocked|logout flags + captchaRequired)
                       → templates/auth/login.html (with CSRF + optional CAPTCHA)

POST /login          → UsernamePasswordAuthenticationFilter
                       ↓
                       UserDetailsServiceImpl.loadUserByUsername()
                       ↓
                       BCryptPasswordEncoder(12).matches()
                       ↓
                       ┌─────────── FAILURE ────────────┐
                       │                                 │
                       ▼                                 ▼
 CustomAuthenticationSuccessHandler   CustomAuthenticationFailureHandler
   .resetAttempts(username)             .trackFailedAttempt(username, ip)
   .trackSuccessfulAttempt              validate CAPTCHA if required
   update users.last_login_at           set captchaRequired=true if count≥3
   redirect by role:                    redirect:
     ADMIN   → /admin/dashboard           /login?locked     (5 failures/15min)
     REVIEWER→ /approval/queue            /login?ipblocked  (20 failures/60min)
     FINANCE → /analytics/dashboard       /login?error      (otherwise)
     OPS     → /campaigns
     CS      → /campaigns
```

Class references:
- `config/SecurityConfig.java` — SecurityFilterChain, filter order, permitAll matchers
- `security/UserDetailsServiceImpl.java` — loads from `users` table, maps role to `ROLE_<name>`
- `security/CustomAuthenticationSuccessHandler.java` — role-based redirect, last_login_at update
- `security/CustomAuthenticationFailureHandler.java` — lockout tracking + CAPTCHA gating
- `security/AccountLockoutService.java` — time-windowed count query; lock implicit in query result
- `security/LocalCaptchaService.java` — 200×60 BufferedImage, 6 alphanumeric chars, ±15° rotation

### 4.2 Lockout Sequence

```
 Attempt N → trackFailedAttempt() → insert login_attempts row
            ↓
            currentFailureCount(username) queries WHERE attempted_at > now - 15min
            ↓
            N ≥ 3 → session.captchaRequired = true (next render shows CAPTCHA)
            N ≥ 5 → redirect /login?locked  (account window)
           IP N ≥ 20 → redirect /login?ipblocked (60-min window)
            ↓
            Lock lifts automatically when oldest failure ages out of window
            (no separate unlock job — the data IS the lock state).
```

### 4.3 Rate Limiting

- Class: `security/RateLimitFilter.java` (extends `OncePerRequestFilter`)
- Engine: Bucket4j 8.10.1, in-memory `ConcurrentHashMap<String, Bucket>`
- Bucket key: `standard:<username>` or `export:<username>`
- Quotas: 60 req/min standard, 10 req/min for `/analytics/export/**`
- On exhaustion: HTTP 429 + `Retry-After: 60` + JSON body
- Anonymous traffic is excluded (login throttling is IP-based via lockout service)

### 4.4 Anti-Replay Nonce Validation

- Class: `security/NonceValidationFilter.java`
- Applies to: `POST /admin/**` and `POST /approval/dual-approve/**`
- Required headers: `X-Nonce` (any unique value, UUID works) + `X-Timestamp` (epoch millis)
- Rules:
  1. `|now - X-Timestamp| ≤ 5 minutes` — else HTTP 400
  2. `used_nonces` table lookup — else HTTP 400 (replay detected)
  3. On success: persist `UsedNonce` with `expires_at = now + 10 minutes`
- Cleanup: `security/UsedNonceCleanupTask.java` @Scheduled cron `0 0 * * * *` (hourly)

### 4.5 Request Signing (HMAC-SHA256)

- Class: `security/RequestSigningFilter.java`
- Applies to: `POST /admin/**`
- Canonical string: `METHOD + "\n" + PATH + "\n" + X-Timestamp + "\n" + SHA256(body, hex)`
- HMAC key: `app.signing.secret` from application.yml (default `retail-campaign-hmac-signing-key!!`)
- Constant-time compare via `constantTimeEquals()` (defensive against timing attacks)
- On mismatch: HTTP 403 + JSON error
- Body is read via `ContentCachingRequestWrapper` so downstream controllers still see the body

### 4.6 CSRF

- `CookieCsrfTokenRepository.withHttpOnlyFalse()` — cookie is readable by JS so HTMX can include the token as header
- Base layout `templates/layout/base.html` exposes token via `<meta name="_csrf">` + attaches it to every HTMX request via an `htmx:configRequest` listener
- Spring Security 6 uses two-token CSRF: cookie value is the session identifier, form `${_csrf.token}` is the signed submission value (this is exercised in `SecurityIntegrationTest.postWithoutCsrfRejected`)

---

## 5. Business Rules Implementation Map

Every rule from SPEC.md, with its implementation class and method reference:

| Rule (from SPEC.md)                                        | Class : Method |
|------------------------------------------------------------|----------------|
| End date must be after start date, start not in past      | `CampaignService.validateDateRange` |
| Discount > 0 and percent ≤ 100                             | `CampaignService.validateDiscountValue` |
| Status DRAFT → PENDING_REVIEW on submit                    | `CampaignService.submitForReview` |
| Status APPROVED → ACTIVE on publish                        | `CampaignService.publishCampaign` |
| Coupon code uniqueness (case-insensitive)                  | `CouponService.createCoupon` + `CouponRepository.existsByCodeIgnoreCase` |
| Stacking + mutual exclusion check                          | `CouponService.checkStackingCompatibility` |
| HIGH risk → dual approval required                         | `ApprovalService.submitToQueue` (sets `REQUIRES_DUAL`) + `approve` (holds until dual complete) |
| Reviewer ≠ submitter                                       | `ApprovalService.approve` / `reject` throws `SameApproverException` |
| Approver1 ≠ Approver2                                      | `DualApprovalService.recordSecond` throws `SameApproverException` |
| 12-char password + complexity                              | `PasswordValidationService.validate` |
| XSS sanitization on all user input                         | `XssInputSanitizer.sanitize` (Jsoup `Safelist.none()`) |
| Audit log on every critical operation                      | `AuditLogService.log` (called 27 times across 12 services) |
| File MIME validation by signature, not extension           | `FileValidationService.detectAndValidateMime` (Apache Tika) |
| 50 MB per file, chunked upload                             | `FileValidationService.validateSize` + `ChunkedUploadService.initUpload/receiveChunk/finalizeUpload` |
| SHA-256 computed at finalize                               | `StorageService.computeSha256` |
| Watermark for internal-only attachments                    | `WatermarkService.addPdfWatermark` / `addImageWatermark` |
| Masked download: authorized → original, else → watermark or 403 | `MaskedDownloadService.serve` |
| Temp link: 10-min expiry, user-bound, single-use           | `TempDownloadLinkService.resolve` throws `LinkExpiredException` / `AccessDeniedException` |
| SHA-256 exact-dup detection                                | `DuplicateDetectionService.findExactDuplicates` |
| SimHash 64-bit near-dup (Hamming ≤ 8)                      | `FingerprintService.computeSimHash` / `hammingDistance` / `isNearDuplicate` |
| URL normalization (strip utm_*, fbclid, gclid, mc_*, default ports, trailing /) | `FingerprintService.normalizeUrl` |
| Merge duplicates into master, retain versions              | `MergeService.merge` (snapshots before mutation) |
| Version history + rollback                                 | `ContentVersionService.snapshotCurrent` / `rollback` |
| Analytics queries use JPQL with named params only          | `CouponRedemptionRepository.*` / `AnalyticsService.*` |
| Finance-only export, rate-limited                          | `ExportService` class-level `@PreAuthorize` + `RateLimitFilter` export bucket |
| Mass-deletion anomaly (>10 deletes/5min)                   | `AnomalyDetectionService.detectAnomalies` (MASS_DELETION / HIGH) |
| Repeated-export anomaly (>5 exports/10min)                 | `AnomalyDetectionService.detectAnomalies` (REPEATED_EXPORT / MEDIUM) |
| Nightly mysqldump backup                                   | `BackupService` @Scheduled cron `0 0 2 * * *` |
| 14-day backup retention                                    | `BackupService.pruneExpired` |

---

## 6. File Storage Layout

```
/app/uploads/
├── {campaignId}/
│   └── v{version}_{sanitized-filename}        ← finalized attachments
└── tmp/
    └── {uploadId}/
        ├── chunk-0                             ← per-chunk writes during upload
        ├── chunk-1
        └── chunk-N
```

**Permission inheritance from parent campaign**: enforced via `@PreAuthorize` on `FileController` upload endpoints (requires `OPERATIONS`/`ADMIN`) plus the campaign-level access check through `CampaignAttachmentRepository.findByCampaignIdOrderByVersionDesc` which is only reachable via the authenticated controller path.

**Version numbering** computed at finalize time: `findByOriginalFilenameAndCampaignIdOrderByVersionDesc` returns the previous max, incremented by 1.

**Temp files** are cleaned up by `StorageService.cleanupTemp` after each successful finalize. Per-chunk writes use `StandardOpenOption.CREATE + TRUNCATE_EXISTING + WRITE` so re-uploading the same chunk is idempotent.

---

## 7. Content Integrity Pipeline

```
Import (CSV or manual)
  ↓
ContentImportService.importFromCsv / importSingle
  ↓
XssInputSanitizer.sanitize(title, url, body)
  ↓
FingerprintService.normalizeUrl(sourceUrl)
FingerprintService.computeSha256(body)
FingerprintService.computeSimHash(body)   ← Guava Murmur3_128, first 8 bytes,
                                            ± accumulator per bit, sign → final bit
  ↓
ContentItem saved with status=ACTIVE
  ↓
DuplicateDetectionService.findExactDuplicates  (SHA-256 lookup)
DuplicateDetectionService.findNearDuplicates   (in-memory Hamming scan, ≤ 8)
DuplicateDetectionService.groupDuplicates      (deterministic master = lowest id)
  ↓
MergeService.merge(masterId, duplicateIds)
  1. ContentVersionService.snapshotCurrent(dupId)   ← BEFORE mutation
  2. dup.status = MERGED; dup.masterId = masterId
  3. ContentMergeLog row with before/after JSON diff
  4. AuditLogService.log(CONTENT_MERGED)
  ↓
Rollback path:
  ContentVersionService.rollback(contentId, versionNum)
    1. snapshotCurrent (so rollback is itself reversible)
    2. Read target version JSON
    3. Apply fields back onto live row
    4. AuditLogService.log(CONTENT_ROLLED_BACK)
```

---

## 8. Audit Log Design

**Why REQUIRES_NEW transaction**: `AuditLogService.log` uses `@Transactional(propagation = Propagation.REQUIRES_NEW)` so the audit write happens in its own physical transaction. If the outer business transaction rolls back (e.g. campaign creation hits a later validation error), the audit row still persists — we want to know about attempted operations even when they didn't complete.

**Immutability guarantee**: `AuditLogRepository` exposes only `save(AuditLog)` (inherited from JpaRepository for INSERTs) plus paged read methods. There are **zero** custom `delete*` or `update*` methods declared. Enforced three ways:
1. Documentation block in `AuditLogRepository.java`
2. Unit test `AuditLogServiceTest.repositoryHasNoUpdateOrDeleteMethods` uses reflection to assert no custom delete/update methods exist
3. Entity has no `@PreUpdate` hook and no `updatedAt` column

**Audit triggers** (27 call sites across 12 services):

| Action constant            | Triggered by |
|----------------------------|--------------|
| `CAMPAIGN_CREATED`         | `CampaignService.createCampaign` |
| `CAMPAIGN_UPDATED`         | `CampaignService.updateCampaign` (with before/after DTO) |
| `CAMPAIGN_STATUS_CHANGED`  | `submitForReview`, `publishCampaign`, `expireCampaign` |
| `CAMPAIGN_DELETED`         | `CampaignService.softDelete` |
| `COUPON_CREATED`           | `CouponService.createCoupon` |
| `APPROVAL_SUBMITTED`       | `ApprovalService.submitToQueue` |
| `APPROVAL_APPROVED`        | `ApprovalService.approve` |
| `APPROVAL_REJECTED`        | `ApprovalService.reject` |
| `DUAL_APPROVAL_FIRST`      | `DualApprovalService.recordFirst` |
| `DUAL_APPROVAL_SECOND`     | `DualApprovalService.recordSecond` |
| `FILE_UPLOADED`            | `ChunkedUploadService.finalizeUpload` |
| `FILE_DOWNLOADED`          | `MaskedDownloadService.serve` (authorized path) |
| `FILE_MASKED_ACCESS`       | `MaskedDownloadService.serve` (watermarked or blocked) |
| `CONTENT_IMPORTED`         | `ContentImportService.importFromCsv` / `importSingle` |
| `CONTENT_MERGED`           | `MergeService.merge` |
| `CONTENT_ROLLED_BACK`      | `ContentVersionService.rollback` |
| `USER_CREATED`             | `UserService.createUser` (password NEVER logged) |
| `USER_UPDATED`             | `UserService.updateUser` |
| `USER_ROLE_CHANGED`        | `UserService.updateUser` (additional emission on role diff) |
| `USER_DEACTIVATED`         | `UserService.deactivateUser` |
| `EXPORT_GENERATED`         | `ExportService.exportRedemptionsCsv` |
| `BACKUP_RUN`               | `BackupService.runManualBackup` |

Sensitive-field access (e.g. employee notes) has its own dedicated `SensitiveAccessLogService` writing to `sensitive_access_logs`, viewed only by ADMIN via `/admin/sensitive-log`.

---

## 9. Anomaly Detection

| Threshold                  | Window    | Severity | Alert Type          |
|----------------------------|-----------|----------|---------------------|
| > 10 DELETE change events  | 5 min     | HIGH     | `MASS_DELETION`     |
| > 5 EXPORT change events   | 10 min    | MEDIUM   | `REPEATED_EXPORT`   |

**Flow**:
```
Service mutation → ChangeEventService.record(eventType, entityType, entityId, user)
                   → inserts change_events row
                          ↓
AnomalyAlertScheduler @Scheduled(fixedDelay=60_000L)
                   → AnomalyDetectionService.detectAnomalies()
                          ↓
                   count change_events WHERE occurred_at > now - window
                          ↓
                   if > threshold → AnomalyAlertRepository.save(new AnomalyAlert(...))
                                    + SLF4J WARN log line
```

Admin views unacknowledged alerts at `/admin/anomaly-alerts`, acknowledges via `POST /admin/anomaly-alerts/{id}/ack`.

---

## 10. Backup, Retention & Recovery

**Scheduler**: `BackupService.runNightlyBackup` with `@Scheduled(cron = "0 0 2 * * *")` — runs at 02:00 server time daily.

**Procedure** (inside container):
```
mysqldump -h mysql -u retail_user -pretail_pass retail_campaign | gzip > /app/backups/backup_YYYY-MM-DD_<epoch>.sql.gz
```
Invoked via `ProcessBuilder("/bin/sh", "-c", ...)` so the `|` pipe works as a shell command. `mysql-client` is installed in the production Docker image so `mysqldump` is available at runtime.

**Post-backup**:
1. Compute SHA-256 of the resulting file (`BackupService.sha256Hex`)
2. Insert `BackupRecord` with status `COMPLETE` or `FAILED`
3. Call `pruneExpired()` — for records older than 14 days with status `COMPLETE`, delete the file and flip record to `DELETED`
4. Emit `AUDIT AuditAction.BACKUP_RUN` (manual triggers only; scheduled runs log via SLF4J)

**Recovery RTO: under 4 hours on a single server**:
1. `docker compose down`
2. Locate the latest `backup_*.sql.gz` under `/app/backups/` (or the `backup-data` named volume)
3. `docker compose up mysql`
4. `gunzip < backup.sql.gz | docker exec -i mysql mysql -uretail_user -pretail_pass retail_campaign`
5. `docker compose up app`

Documented in `repo/README.md` under "Recovery" and surfaced in the UI at `/admin/backup` (template `templates/admin/backup.html`).

---

## 11. Test Architecture

- **Unit tests (70)** — all under `src/test/java/com/meridian/retail/service/`, all class names end in `*ServiceTest`. Pure Mockito, no Spring context, no DB. Matched by pattern `*ServiceTest,*FilterTest,*ValidatorTest,*UtilTest,*MapperTest` in `run_tests.sh`.
- **Integration tests (18)** — under `src/test/java/com/meridian/retail/integration/` and `security/`. Extend `AbstractIntegrationTest` which is env-aware:
  - Inside `docker-compose.test.yml`: reads `IT_DATASOURCE_URL` → connects to sibling `mysql-test` service
  - Local dev: lazily starts a Testcontainers MySQL 8 container
- **Single entry point**: `docker compose -f docker-compose.test.yml run --build test` → `run_tests.sh` runs both phases → prints `ALL TESTS PASSED` + exit 0 when all 88 tests pass

---

## 12. UI Design System

All CSS tokens live in `repo/src/main/resources/static/css/custom.css`:

```
Sidebar:       #1e293b (dark slate) / #334155 (hover)
Primary:       #3b82f6 (blue) / #2563eb (hover)
Success:       #22c55e
Warning:       #f59e0b
Danger:        #ef4444
Background:    #f8fafc
Card bg:       #ffffff (radius 12px, subtle shadow)
Font:          Inter (loaded from /static/vendor/inter/, NO CDN)
Vendor JS/CSS: Bootstrap 5.3.3, HTMX 1.9.12, Chart.js 4.4.2, Bootstrap Icons 1.11.3 — all under /static/vendor/
```

**Component classes** (consistent across every page):
- `.card` + `.kpi-card` for content and KPI blocks
- `.status-badge.status-{active|pending|draft|rejected|expired|low|medium|high|critical}`
- `.role-badge.role-{admin|reviewer|operations|finance|customer-service}`
- `.empty-state` with icon + heading + primary CTA
- `.receipt-preview` monospace simulated thermal print
- `.upload-zone` drag-and-drop styling
- `.htmx-indicator` spinner for in-flight HTMX requests

**Every page has**: flash message area (via `templates/layout/base.html`), empty state when no data, role-gated action buttons with `title=` "why blocked" tooltips.
