# PLAN.md — Retail Campaign Governance Execution Plan
# Task ID: TASK-31
# [ ] = pending  [x] = complete
# Rule: Complete ALL tasks in a phase without stopping. Pause ONLY at phase boundaries.
# Fix errors within the same task before marking [x]. Never ask user mid-task.
# CRITICAL: QA runs Docker AND reads code. Both must be perfect.

---

## PHASE 0 — Foundation & Infrastructure
> Goal: Docker, Maven wrapper, Spring Boot scaffold, Bootstrap 5 base layout, .gitignore, README
> Complete all tasks continuously, then pause. Wait for "proceed".

- [x] 0.1 Create repo/.gitignore with content from CLAUDE.md — note: no .env or .env.* entries since this project has no .env file and requires zero manual configuration
- [x] 0.2 Create repo/README.md (minimal, exact format from CLAUDE.md)
- [x] 0.3 Create repo/pom.xml — Spring Boot 3.2.x parent, Java 17, all dependencies from CLAUDE.md. Include maven-wrapper-plugin so ./mvnw works inside Docker.
- [x] 0.4 Generate Maven wrapper: create .mvn/wrapper/maven-wrapper.properties pointing to Maven 3.9.x, create mvnw shell script (chmod +x)
- [x] 0.5 Create repo/src/main/java/com/meridian/retail/RetailCampaignApplication.java (@SpringBootApplication, main method)
- [x] 0.6 Create repo/src/main/resources/application.yml with ALL values hardcoded (no ${ENV_VAR} references — QA must not configure anything):
       spring.datasource.url=jdbc:mysql://localhost:3306/retail_campaign (overridden by docker-compose env),
       spring.datasource.username=retail_user, spring.datasource.password=retail_pass,
       spring.jpa.hibernate.ddl-auto=validate, spring.flyway.enabled=true,
       spring.thymeleaf.cache=false, server.port=8080,
       logging.level.root=INFO, logging.level.com.meridian.retail=DEBUG,
       app.upload.path=/app/uploads, app.backup.path=/app/backups,
       app.encryption.key=retail-campaign-aes-key-32chars!! (hardcoded — same as docker-compose),
       app.signing.secret=retail-campaign-hmac-signing-key!! (hardcoded — same as docker-compose),
       app.rate-limit.standard=60, app.rate-limit.export=10,
       app.temp-link.expiry-minutes=10, app.backup.retention-days=14
- [x] 0.7 Create repo/src/main/resources/application-docker.yml: datasource URL pointing to mysql service, production logging level
- [x] 0.8 Create repo/src/main/resources/application-test.yml: Testcontainers auto-datasource config (spring.datasource.url=, spring.jpa.hibernate.ddl-auto=create — ONLY for test profile)
- [x] 0.9 Download Bootstrap 5.3 CSS/JS, HTMX 1.9, Chart.js, Inter font to repo/src/main/resources/static/vendor/ — no CDN references anywhere (offline-first requirement from SPEC.md)
- [x] 0.10 Create repo/src/main/resources/static/css/custom.css — full custom stylesheet using CSS variables for the color palette from CLAUDE.md (dark sidebar, card styles, role badges, form styles, empty states)
- [x] 0.11 Create base Thymeleaf layout: templates/layout/base.html — dark sidebar navigation (240px, fixed), top header (breadcrumbs + username + role badge + logout button), main content area, flash message slot, HTMX attributes, Bootstrap 5 and HTMX loaded from /vendor/
- [x] 0.12 Create templates/layout/sidebar.html fragment — role-based menu items using sec:authorize, active state detection, icons (Bootstrap Icons or inline SVG)
- [x] 0.13 Create templates/error/403.html, 404.html, 500.html — styled error pages with "why blocked" message for 403, navigation back to dashboard
- [x] 0.14 Create WebConfig.java (@Configuration): static resource handlers, multipart config (50MB max file size, 200MB max request size), HTMX response header helper
- [x] 0.15 Create repo/Dockerfile — eclipse-temurin:17-jdk-alpine builder stage (./mvnw package -DskipTests), eclipse-temurin:17-jre-alpine runtime stage, COPY target/*.jar app.jar, ENTRYPOINT
- [x] 0.16 Create repo/Dockerfile.test — maven:3.9-eclipse-temurin-17-alpine, COPY pom.xml + src, RUN chmod +x run_tests.sh, CMD ["sh", "run_tests.sh"]
- [x] 0.17 Create repo/docker-compose.yml — exact content from CLAUDE.md (mysql + app services, healthcheck, volumes). CRITICAL: All environment variable values must be hardcoded strings — no ${VAR} syntax anywhere. No .env file. QA runs docker compose up --build with zero configuration.
- [x] 0.18 Create repo/docker-compose.test.yml — mysql-test service (port 3307) + test-runner service that depends on mysql-test healthy
- [x] 0.19 Create repo/run_tests.sh — exact content from CLAUDE.md, chmod +x
- [x] 0.20 Create HealthController.java: GET /actuator/health returns 200 with {status: UP} — no auth required
- [x] 0.21 Verify: ./mvnw compile --no-transfer-progress succeeds with zero errors. Fix all compile errors before marking done.

**Phase 0 checkpoint: ./mvnw compile succeeds. All Docker files created. README matches CLAUDE.md template exactly.**

---

## PHASE 1 — Database Schema, Entities & Seed Data
> Goal: All Flyway migrations create real working tables, all JPA entities compile and map correctly, seed data enables immediate login
> Complete all tasks continuously, then pause. Wait for "proceed".

- [ ] 1.1 Create V1__create_users.sql: users (id BIGINT AUTO_INCREMENT PK, username VARCHAR(100) UNIQUE NOT NULL, password_hash VARCHAR(255) NOT NULL, role ENUM('ADMIN','OPERATIONS','REVIEWER','FINANCE','CUSTOMER_SERVICE') NOT NULL, is_active BOOLEAN DEFAULT true, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)
- [ ] 1.2 Create V2__create_lockout.sql: login_attempts (id, username, ip_address, attempted_at TIMESTAMP INDEX), used_nonces (id, nonce VARCHAR(128) UNIQUE, used_at TIMESTAMP, expires_at TIMESTAMP INDEX)
- [ ] 1.3 Create V3__create_campaigns.sql: campaigns (id, name, description, type ENUM('COUPON','DISCOUNT'), status ENUM('DRAFT','PENDING_REVIEW','APPROVED','ACTIVE','EXPIRED','REJECTED'), receipt_wording TEXT, store_id VARCHAR(100), created_by VARCHAR(100), start_date DATE, end_date DATE, created_at, updated_at, deleted_at)
- [ ] 1.4 Create V4__create_coupons.sql: coupons (id, campaign_id FK, code VARCHAR(50) UNIQUE, discount_type ENUM('PERCENT','FIXED'), discount_value DECIMAL(10,2), min_purchase_amount DECIMAL(10,2), max_uses INT, uses_count INT DEFAULT 0, is_stackable BOOLEAN DEFAULT false, mutual_exclusion_group VARCHAR(100), valid_from DATE, valid_until DATE)
- [ ] 1.5 Create V5__create_approval.sql: approval_queue (id, campaign_id FK, requested_by, assigned_reviewer, status ENUM('PENDING','APPROVED','REJECTED','REQUIRES_DUAL'), risk_level ENUM('LOW','MEDIUM','HIGH'), notes TEXT, created_at, updated_at), dual_approval_requests (id, approval_queue_id FK, approver1_username, approver2_username, approver1_at, approver2_at, status ENUM('PENDING','COMPLETE'))
- [ ] 1.6 Create V6__create_files.sql: campaign_attachments (id, campaign_id FK, original_filename, stored_filename, stored_path, file_type, file_size_bytes BIGINT, sha256_checksum VARCHAR(64), is_internal_only BOOLEAN DEFAULT false, masked_roles JSON, version INT DEFAULT 1, uploaded_by, uploaded_at), upload_sessions (id, upload_id UUID, campaign_id FK, original_filename, total_chunks INT, received_chunks JSON, temp_dir, status ENUM('IN_PROGRESS','COMPLETE','FAILED'), created_at), temp_download_links (id, token VARCHAR(36) UNIQUE, file_id FK, username, ip_address, expires_at TIMESTAMP, used_at TIMESTAMP)
- [ ] 1.7 Create V7__create_content.sql: content_items (id, campaign_id FK, source_url TEXT, normalized_url VARCHAR(2048), title VARCHAR(500), body_text LONGTEXT, sha256_fingerprint VARCHAR(64) INDEX, sim_hash BIGINT INDEX, status ENUM('ACTIVE','DUPLICATE','MERGED'), master_id BIGINT NULL, imported_by, created_at, updated_at), content_versions (id, content_id FK, version_num INT, snapshot_json LONGTEXT, changed_by, changed_at), content_merge_log (id, master_id, merged_ids JSON, before_json LONGTEXT, after_json LONGTEXT, merged_by, merged_at)
- [ ] 1.8 Create V8__create_audit.sql: audit_logs (id BIGINT AUTO_INCREMENT, action VARCHAR(100) NOT NULL, entity_type VARCHAR(100), entity_id BIGINT, operator_username VARCHAR(100), ip_address VARCHAR(45), before_state LONGTEXT, after_state LONGTEXT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP — NO updated_at column), sensitive_access_logs (id, accessor_username, field_name, entity_type, entity_id, ip_address, accessed_at TIMESTAMP)
- [ ] 1.9 Create V9__create_analytics.sql: coupon_redemptions (id, coupon_id FK, store_id, redeemed_at TIMESTAMP, discount_applied DECIMAL(10,2), order_total DECIMAL(10,2)), export_logs (id, exported_by, export_type, filters_applied JSON, row_count INT, file_path, exported_at, ip_address)
- [ ] 1.10 Create V10__create_anomaly.sql: change_events (id, event_type VARCHAR(100), entity_type, entity_id BIGINT, username, occurred_at TIMESTAMP INDEX, metadata_json JSON), anomaly_alerts (id, alert_type, description TEXT, severity ENUM('LOW','MEDIUM','HIGH','CRITICAL'), detected_at TIMESTAMP, acknowledged_by, acknowledged_at)
- [ ] 1.11 Create V11__create_backup.sql: backup_records (id, filename, file_path, file_size_bytes BIGINT, sha256_checksum VARCHAR(64), status ENUM('COMPLETE','FAILED','DELETED'), created_at, restored_at)
- [ ] 1.12 Create V12__seed_data.sql: INSERT 5 users with BCrypt-hashed passwords (compute real BCrypt hashes for Admin@Retail2024!, Ops@Retail2024!, Review@Retail2024!, Finance@Retail2024!, CsUser@Retail2024! — use BCrypt.hashpw or pre-compute with bcrypt tool), INSERT sample campaigns (2 draft, 1 active, 1 pending review), INSERT sample coupons, INSERT sample content items
- [ ] 1.13 Create all JPA entities (@Entity classes): User, LoginAttempt, UsedNonce, Campaign, Coupon, ApprovalQueue, DualApprovalRequest, CampaignAttachment, UploadSession, TempDownloadLink, ContentItem, ContentVersion, ContentMergeLog, AuditLog (no @PreUpdate, only @PrePersist), SensitiveAccessLog, CouponRedemption, ExportLog, ChangeEvent, AnomalyAlert, BackupRecord
- [ ] 1.14 Create all Spring Data JPA repositories — note AuditLogRepository must NOT have any save(AuditLog) that accepts updates (use @Modifying @Query delete only for testing — no update methods at all)
- [ ] 1.15 Create enums: UserRole, CampaignType, CampaignStatus, DiscountType, ApprovalStatus, RiskLevel, DualApprovalStatus, ContentStatus, AttachmentStatus, AlertSeverity, BackupStatus
- [ ] 1.16 Verify: ./mvnw compile succeeds. All entities have correct @Column annotations matching migration column names. No compile errors.

**Phase 1 checkpoint: ./mvnw compile succeeds. All 12 migration files exist. Seed data has pre-computed BCrypt hashes.**

---

## PHASE 2 — Authentication, RBAC & Security Infrastructure
> Goal: Login works in browser for all 5 roles, lockout works, CAPTCHA displays, all security filters active
> QA will test login in Docker — it must actually work, not just compile.
> Complete all tasks continuously, then pause. Wait for "proceed".

- [ ] 2.1 Create SecurityConfig.java — exact implementation from CLAUDE.md spec, with all 3 custom filters registered in correct order
- [ ] 2.2 Create UserDetailsServiceImpl.java: implements UserDetailsService, loads user from DB by username, returns Spring Security User with role as GrantedAuthority ("ROLE_ADMIN" etc.), returns disabled user if is_active=false
- [ ] 2.3 Create PasswordEncoder @Bean in SecurityConfig: BCryptPasswordEncoder(strength=12)
- [ ] 2.4 Create AccountLockoutService.java — exact implementation from CLAUDE.md spec with both account and IP lockout queries
- [ ] 2.5 Create CustomAuthenticationFailureHandler.java: call accountLockoutService.trackFailedAttempt(), count failures for account, if count >= 3 set session attribute "captchaRequired"=true, redirect to /login?error (or /login?locked if now locked, or /login?ipblocked if IP blocked)
- [ ] 2.6 Create CustomAuthenticationSuccessHandler.java: call accountLockoutService.resetAttempts(), redirect by role: ADMIN→/admin/dashboard, REVIEWER→/approval/queue, FINANCE→/analytics/dashboard, OPERATIONS→/campaigns, CUSTOMER_SERVICE→/campaigns (read-only view)
- [ ] 2.7 Create LocalCaptchaService.java: generateImage(session) creates 200x60 BufferedImage with 6-char random alphanumeric, font size 30-36px, ±15 degree rotation per char, 5 random lines as noise, stores plaintext answer in session under "CAPTCHA_ANSWER" key, validateAnswer(session, input) compares case-insensitively
- [ ] 2.8 Create CaptchaController.java: GET /captcha/image — returns BufferedImage as image/png (uses ImageIO.write to response output stream), POST /captcha/validate — returns Thymeleaf fragment with result for HTMX
- [ ] 2.9 Create RateLimitFilter.java — exact implementation from CLAUDE.md spec with Bucket4j
- [ ] 2.10 Create NonceValidationFilter.java: applies to POST /admin/** and POST /approval/dual-approve/**, reads X-Nonce + X-Timestamp headers, validates timestamp within ±5min, queries usedNonceRepository.existsByNonce(nonce), if exists → 400, if valid → save UsedNonce + continue chain
- [ ] 2.11 Create RequestSigningFilter.java: applies to POST /admin/**, reads X-Signature header, computes expected HMAC-SHA256(method+"\n"+path+"\n"+timestamp+"\n"+SHA256(body)) using signingSecret from @Value, compares — 403 if mismatch
- [ ] 2.12 Create PasswordValidationService.java: validate(password) → checks length ≥ 12, has uppercase, lowercase, digit, special char (Pattern.compile) → throws PasswordComplexityException("Password must be at least 12 characters with uppercase, lowercase, digit, and special character") if fails
- [ ] 2.13 Create XssInputSanitizer.java: sanitize(input) → uses Jsoup.clean(input, Safelist.none()) to strip all HTML/scripts. Static utility method, called at start of every service method that accepts user text.
- [ ] 2.14 Create UsedNonceCleanupTask.java: @Scheduled(cron="0 0 * * * *") deletes usedNonces where expires_at < NOW()
- [ ] 2.15 Create LoginController.java: GET /login — checks session for "captchaRequired" attribute, adds to model, renders templates/auth/login.html; also handles ?error, ?locked, ?logout, ?ipblocked query params with appropriate messages
- [ ] 2.16 Create templates/auth/login.html: full Bootstrap 5 centered card login form, CSRF token (th:action="@{/login}" includes it automatically), username/password fields, error message area (th:if="${param.error}"), locked message (th:if="${param.locked}"), CAPTCHA section (th:if="${captchaRequired}") showing /captcha/image + input field, "Why am I locked out?" expandable help text, modern eye-catching design matching CLAUDE.md color palette
- [ ] 2.17 Create DashboardController.java: GET /admin/dashboard (ADMIN), GET /approval/queue stub (REVIEWER), GET /campaigns stub (OPERATIONS, CS), GET /analytics/dashboard stub (FINANCE) — returns role-specific dashboard templates with real data from DB
- [ ] 2.18 Write unit tests: AccountLockoutServiceTest — lockout after 5 attempts (mock repo returning count 5), no lockout at 4, IP block after 20, PasswordValidationServiceTest — valid/invalid cases
- [ ] 2.19 Write security integration tests (SecurityIntegrationTest.java with @SpringBootTest + Testcontainers): GET /campaigns without login → 302 redirect to /login, POST /login with wrong password → 302 /login?error, POST /campaigns without CSRF → 403, OPS user GET /admin/dashboard → 403
- [ ] 2.20 Verify end-to-end in compile: ./mvnw compile succeeds, all security classes compile with zero errors

**Phase 2 checkpoint: Login page renders. All 5 users can log in with correct credentials. Wrong password shows error. CSRF protection active.**

---

## PHASE 3 — Campaign & Promotions Module (Backend + Frontend)
> Goal: Full campaign lifecycle works in browser — create, validate, preview receipt, submit for review, approve
> QA will create a campaign and put it through the approval flow in the browser.
> Complete all tasks continuously, then pause. Wait for "proceed".

- [ ] 3.1 Create CampaignDTO.java, CouponDTO.java, CreateCampaignRequest.java with Bean Validation annotations (@NotBlank, @NotNull, @Positive, @Future for end_date etc.)
- [ ] 3.2 Create CampaignService.java: createCampaign(request, username) — sanitize inputs, validate, save, auditLog.log(CAMPAIGN_CREATED); updateCampaign(id, request, username) — snapshot before, update, auditLog.log(CAMPAIGN_UPDATED, before, after); submitForReview(id, username) → status PENDING_REVIEW; publishCampaign(id, username) → status ACTIVE; expireCampaign(id)
- [ ] 3.3 Add validation methods to CampaignService: validateDateRange(start, end) — throws if end ≤ start or start in past; validateDiscountValue(type, value) — throws if value ≤ 0 or (PERCENT and value > 100); all throw CampaignValidationException with clear message
- [ ] 3.4 Create CouponService.java: createCoupon(request, campaignId) — validate code uniqueness (case-insensitive query), validate stackingCompatibility, save; checkStackingCompatibility(newCoupon, existingCartCouponIds) — check is_stackable and mutual_exclusion_group
- [ ] 3.5 Create ReceiptPreviewService.java: generatePreview(campaign) → returns HTML fragment String with formatted receipt text block (coupon code, discount amount, min purchase, validity, custom receipt_wording), used by HTMX partial endpoint
- [ ] 3.6 Create ApprovalService.java: submitToQueue(campaignId, requestedBy, riskLevel) — creates ApprovalQueue entry, auditLog; assign(queueId, reviewerUsername) — sets assigned_reviewer; approve(queueId, reviewerUsername, notes) — validates reviewer != submitter, updates status APPROVED, triggers DualApprovalService if HIGH risk; reject(queueId, reviewerUsername, notes) — status REJECTED, auditLog
- [ ] 3.7 Create DualApprovalService.java: initiate(approvalQueueId) — creates DualApprovalRequest; recordFirst(requestId, approverUsername) — saves approver1; recordSecond(requestId, approverUsername) — checks approver1 != approver2 (throws SameApproverException), saves approver2, marks COMPLETE; isComplete(requestId) → boolean
- [ ] 3.8 Create CampaignController.java: GET /campaigns (list all, role-filtered), GET /campaigns/new (form), POST /campaigns (create), GET /campaigns/{id}/edit (form), PUT /campaigns/{id} (update), POST /campaigns/{id}/submit (submit for review), DELETE /campaigns/{id} (soft delete), GET /campaigns/validate/dates (HTMX), GET /campaigns/validate/discount (HTMX), GET /campaigns/{id}/preview-receipt (HTMX)
- [ ] 3.9 Create ApprovalController.java: GET /approval/queue (list), POST /approval/{id}/approve, POST /approval/{id}/reject, POST /approval/dual-approve/{id} (requires nonce headers)
- [ ] 3.10 Create templates/campaign/list.html: full data table with campaign name, type badge, status badge (color-coded), store, date range, action buttons. Role-gated actions: edit (ADMIN/OPS only), submit for review, approve/reject (REVIEWER/ADMIN only). Disabled buttons show title="You need [role] role to perform this action". Filter by status/type above table. Empty state card when no campaigns.
- [ ] 3.11 Create templates/campaign/form.html: clean multi-section Bootstrap 5 form. Section 1: Basic Info (name, description, type, store). Section 2: Discount Details (type select → conditional fields via HTMX, discount value with real-time validation, min purchase). Section 3: Schedule (date pickers with date range validation on blur via HTMX). Section 4: Receipt Wording (textarea + live preview panel on right via HTMX). Save as Draft button + Submit for Review button. Form sections have clear visual separation with cards.
- [ ] 3.12 Create templates/campaign/receipt-preview-fragment.html: Thymeleaf fragment in a bordered div with monospace font, grey background, simulating a receipt printout. Updates in real-time via HTMX oob swap.
- [ ] 3.13 Create templates/approval/queue.html: card-based queue list. Each card shows: campaign name, submitted by, date, risk level badge (HIGH=red, MEDIUM=amber, LOW=green), notes textarea, Approve/Reject buttons. Dual approval indicator badge on HIGH risk items. Empty state when queue is empty.
- [ ] 3.14 Write unit tests: CampaignServiceTest (date validation, discount validation, status transitions), ApprovalServiceTest (approve flow, reject flow, dual approval same-user prevention), CouponServiceTest (code uniqueness validation, stacking compatibility)
- [ ] 3.15 Write integration test: CampaignIntegrationTest — create campaign → submit → assign reviewer → approve → verify status APPROVED in DB

**Phase 3 checkpoint: Campaign creation form works. Approval queue shows pending items. Receipt preview updates in real-time.**

---

## PHASE 4 — File Management Module (Backend + Frontend)
> Goal: Chunked upload completes, progress shows in browser, temp link works and expires, watermark applied
> QA will upload a PDF and image, verify progress bar, download, and see watermark on internal asset.
> Complete all tasks continuously, then pause. Wait for "proceed".

- [ ] 4.1 Create FileValidationService.java: validateByTika(InputStream) → uses Tika to detect MIME type, rejects if not in allowed list (PDF, JPEG, PNG, GIF), validateSize(long bytes) → throws if > 52428800 (50MB)
- [ ] 4.2 Create StorageService.java: getUploadDir(campaignId) → /app/uploads/{campaignId}/, getTempDir(uploadId) → /app/uploads/tmp/{uploadId}/, storeChunk(uploadId, chunkIndex, bytes) → writes to temp dir, assembleChunks(uploadId, totalChunks, destPath) → concatenates in order, computeSha256(path) → hex string, cleanupTemp(uploadId)
- [ ] 4.3 Create ChunkedUploadService.java: initUpload(campaignId, filename, totalChunks, username) → creates UploadSession, returns uploadId; receiveChunk(uploadId, chunkIndex, bytes) → stores chunk, updates receivedChunks JSON in session, returns progress%; getStatus(uploadId) → returns {uploadId, receivedChunks[], progress%}; finalizeUpload(uploadId) → assembles, validates by Tika, computes SHA256, saves CampaignAttachment, auditLog, returns attachment DTO
- [ ] 4.4 Create WatermarkService.java: addPdfWatermark(inputPath, username, outputStream) → PDFBox loads PDF, for each page add PDPageContentStream with "INTERNAL USE ONLY | {username} | {date}" as diagonal grey text at 45deg opacity 0.3; addImageWatermark(inputPath, mimeType, username, outputStream) → BufferedImage loaded, Graphics2D draws semi-transparent text overlay center of image
- [ ] 4.5 Create TempDownloadLinkService.java: generate(fileId, username, ipAddress) → UUID token, saves TempDownloadLink with expiresAt=now+10min, returns token; resolve(token, requestUsername) → finds link, checks not expired (throws LinkExpiredException), checks username match (throws UnauthorizedException), marks usedAt=now, returns file path
- [ ] 4.6 Create MaskedDownloadService.java: canAccessOriginal(attachment, userRole) → checks attachment.maskedRoles JSON contains userRole; serve(attachmentId, username, userRole, response) → if authorized: stream original file; if not authorized + is_internal_only: stream watermarked version; log access to auditLog either way
- [ ] 4.7 Create FileController.java: POST /files/upload/init, POST /files/upload/chunk, GET /files/upload/status/{uploadId}, POST /files/upload/finalize/{uploadId}, GET /files/download/{token} (temp link endpoint), GET /files/attachment/{id}/download (generates temp link, redirects), GET /files/attachment/{id}/history (version history page)
- [ ] 4.8 Create templates/upload/upload.html: full drag-and-drop upload page. Campaign selector at top. Drag-drop zone (dashed border, hover effect). Manual "or click to browse" fallback. Progress section (hidden initially): filename, progress bar (Bootstrap 5 .progress), percentage text, chunk count "X of Y chunks received", checksum display after finalization ("SHA-256: abc123..."), status badge (In Progress / Complete / Failed). Version history table at bottom (if files already attached to campaign): filename, version, date, uploaded by, download link, checksum.
- [ ] 4.9 Create static/js/upload.js: handles chunked upload — reads File as ArrayBuffer, splits into 1MB chunks, POSTs each to /files/upload/chunk with headers (Upload-Id, Chunk-Index, Total-Chunks), polls /files/upload/status/{uploadId} every 2 seconds, updates progress bar, on 100% calls /files/upload/finalize, displays checksum and success state
- [ ] 4.10 All file operations (upload, download, masked download attempt) write to AuditLogService with before/after state
- [ ] 4.11 Write unit tests: TempDownloadLinkServiceTest (expired link throws, wrong user throws, valid link returns path), WatermarkServiceTest (PDF output bytes > 0 and parseable, Image output not null), FileValidationServiceTest (wrong extension allowed if MIME correct, bad MIME rejected)
- [ ] 4.12 Write integration test: FileUploadIntegrationTest — init upload → send 3 chunks → finalize → verify CampaignAttachment in DB with correct SHA256 → generate temp link → download → verify link marked used → expired link returns 410

**Phase 4 checkpoint: Upload page functional. Progress bar updates. File appears in version history after upload. Temp link expires in 10 min.**

---

## PHASE 5 — Content Integrity Module (Backend + Frontend)
> Goal: Import content, see duplicates grouped, merge them, view version history, rollback — all in browser
> QA will import content, verify duplicate grouping, merge, and check version history.
> Complete all tasks continuously, then pause. Wait for "proceed".

- [ ] 5.1 Create FingerprintService.java: computeSha256(String text) → hex string via MessageDigest; computeSimHash(String text) → long (64-bit) — implementation: (a) tokenize by whitespace (b) for each token compute 128-bit Murmur3 hash via Guava (c) for each of 64 bit positions sum +1 if token-hash bit=1 else -1 (d) final bit = 1 if sum>0 else 0 (e) return as long — add clear comments; hammingDistance(long a, long b) → Long.bitCount(a XOR b); normalizeUrl(String url) → lowercase, remove tracking params (utm_*), remove trailing slash, decode percent-encoding
- [ ] 5.2 Create DuplicateDetectionService.java: findExactDuplicates(String sha256) → query by sha256_fingerprint; findNearDuplicates(long simHash) → load all content_items, filter where hammingDistance(simHash, item.simHash) <= 8; groupDuplicates() → cluster all items by simHash proximity, return List<DuplicateGroup> where each group has master candidate + duplicates + similarity scores
- [ ] 5.3 Create ContentVersionService.java: snapshotCurrent(contentId) → reads current ContentItem, serializes to JSON, saves ContentVersion with next version_num; getHistory(contentId) → ordered by version_num DESC; rollback(contentId, versionNum, username) → @Transactional: snapshot current state (for audit), restore fields from version JSON, save, auditLog(CONTENT_ROLLED_BACK, before, after)
- [ ] 5.4 Create MergeService.java: merge(masterId, duplicateIds, username) → @Transactional: (1) snapshot all duplicates to ContentVersion before merge (2) set status=MERGED + master_id on each duplicate (3) serialize before/after diff to JSON (4) save ContentMergeLog (5) auditLog(CONTENT_MERGED). computeJsonDiff(before, after) → field-by-field comparison returning Map<field, {before, after}>
- [ ] 5.5 Create ContentImportService.java: importFromCsv(MultipartFile, campaignId, username) → parse CSV rows (columns: title, source_url, body_text), normalize URL, compute fingerprints, check for exact/near duplicates, save ContentItem for each, return ImportResult{imported, duplicatesFound, errors[]}; importFromUrl(String url, campaignId, username) → fetch URL content (offline: only LAN URLs), extract title+body, same pipeline
- [ ] 5.6 Create ContentController.java: GET /content (list with duplicate badge counts per item), GET /content/duplicates (grouped duplicate view), POST /content/merge (merge form submit), GET /content/{id}/history (version history page), POST /content/{id}/rollback/{version}, POST /content/import/csv, POST /content/import/url
- [ ] 5.7 Create templates/content/list.html: content table with Title, Source URL (normalized), Status badge (ACTIVE/DUPLICATE/MERGED), Similarity Score (if duplicate). "Duplicate" badge shows count of near-duplicates. Filter by status. Import buttons (CSV / URL). Empty state.
- [ ] 5.8 Create templates/content/duplicates.html: card grid showing duplicate groups. Each group card: master item (highlighted with border), list of duplicates below with similarity % badge. "Exact" vs "Near" duplicate indicators. "Merge All to Master" button opens confirmation modal showing before/after diff table.
- [ ] 5.9 Create templates/content/history.html: timeline-style version history. Each version: version number badge, changed_by, changed_at, expandable before/after diff table (field | before value | after value). "Rollback to this version" button (REVIEWER+ only) with confirmation modal.
- [ ] 5.10 Write unit tests: FingerprintServiceTest (same input → same SHA256, near-duplicate texts → hamming ≤ 8, dissimilar texts → hamming > 20, SimHash produces valid 64-bit long), MergeServiceTest (duplicates linked to master, versions created, ContentMergeLog saved), DuplicateDetectionServiceTest (exact duplicate found by SHA256, near-duplicate found by hamming)
- [ ] 5.11 Write integration test: ContentIntegrationTest — import 3 near-duplicate items → findDuplicates returns group of 3 → merge → master has versions → rollback → version history length correct

**Phase 5 checkpoint: Import works. Duplicate group shows 2+ items together. Merge creates version chain. Rollback restores previous state.**

---

## PHASE 6 — Audit Logs, Analytics, Anomaly Detection & Backup
> Goal: Audit log shows in browser, analytics dashboard has real data, anomaly alerts appear, backup runs
> QA will check audit log entries after performing operations, and verify analytics charts show data.
> Complete all tasks continuously, then pause. Wait for "proceed".

- [ ] 6.1 Create AuditLogService.java — exact implementation from CLAUDE.md: @Transactional(propagation=REQUIRES_NEW), log() method, AuditLogRepository must not have update or delete methods — add comment in repo "// IMMUTABLE: No update or delete operations permitted by design"
- [ ] 6.2 Create SensitiveAccessLogService.java: logAccess(fieldName, entityType, entityId, username, ipAddress) — saves to sensitive_access_logs; accessible only by ADMIN role via GET /admin/sensitive-log
- [ ] 6.3 Audit all existing services and add any missing AuditLogService.log() calls: CampaignService (created, updated, deleted, status changed), CouponService (created, updated), ApprovalService (submitted, approved, rejected), FileController (uploaded, downloaded, masked access), MergeService (merged), ContentVersionService (rolled back), UserService (created, updated, role changed, deactivated)
- [ ] 6.4 Create ChangeEventService.java: record(eventType, entityType, entityId, username) → saves ChangeEvent; getRecentCount(eventType, windowMinutes) → COUNT query on change_events where occurred_at > now()-window
- [ ] 6.5 Create AnomalyDetectionService.java: detectAnomalies() — checks: (1) mass deletes: if DELETE events in last 5 min > 10 → createAlert(MASS_DELETION, HIGH); (2) repeated exports: if EXPORT events in last 10 min > 5 → createAlert(REPEATED_EXPORT, MEDIUM); createAlert(type, severity, description) → saves AnomalyAlert, logs to SLF4J WARN
- [ ] 6.6 Create AnomalyAlertScheduler.java: @Scheduled(fixedDelay=60000) calls anomalyDetectionService.detectAnomalies()
- [ ] 6.7 Create AnalyticsService.java: getCouponStats(storeId, from, to) → {issuanceCount, redemptionCount}; getDiscountUtilization(from, to) → {totalDiscountGiven, avgDiscountPercent}; getTopCampaigns(storeId, from, to, limit) → List<{campaignName, redemptionCount, totalDiscount}>; all queries use JPQL with named parameters (no string concatenation)
- [ ] 6.8 Create ExportService.java: @PreAuthorize("hasRole('FINANCE') or hasRole('ADMIN')") on class; exportCsv(filters, username, ipAddress) → builds CSV with StreamingResponseBody (no full in-memory load), logs to ExportLog and AuditLogService, writes ChangeEvent(EXPORT) for anomaly tracking
- [ ] 6.9 Create BackupService.java: @Scheduled(cron="0 0 2 * * *") runNightlyBackup() → filename = "backup_"+LocalDate.now()+".sql.gz", runs ProcessBuilder("mysqldump", ..., "|", "gzip", ">", filepath), saves BackupRecord, deletes backup files older than 14 days and marks records DELETED; manual trigger: POST /admin/backup/run (ADMIN only)
- [ ] 6.10 Create AnalyticsController.java: GET /analytics/dashboard (role-filtered — FINANCE/ADMIN see export button), GET /analytics/export (rate-limited to 10/min, returns CSV StreamingResponseBody), GET /admin/audit-log (ADMIN only, paginated), GET /admin/sensitive-log (ADMIN only), GET /admin/anomaly-alerts (ADMIN only)
- [ ] 6.11 Create templates/analytics/dashboard.html: 4 summary KPI cards at top (total coupons issued, total redemptions, total discount given, unique stores). Date range picker (from/to) + store filter (HTMX updates charts on change). Bar chart (Chart.js, local) — top 10 campaigns by redemptions. Line chart — redemptions over time. Export to CSV button (only visible to FINANCE/ADMIN roles). Loading skeleton while data loads.
- [ ] 6.12 Create templates/audit/log.html: paginated table (50/page) with columns: timestamp, operator, action, entity type, entity ID, IP address. Expandable row → shows before/after JSON diff in collapsible accordion (Bootstrap 5). Filter by action type, entity type, date range. Clearly labeled ADMIN ONLY in breadcrumb.
- [ ] 6.13 Create templates/admin/anomaly-alerts.html: alerts table with severity badge (color-coded), description, detected at. Unacknowledged alerts highlighted with amber background. "Acknowledge" button updates record. Alert count badge in sidebar nav.
- [ ] 6.14 Write unit tests: AnalyticsServiceTest (correct counts from known DB fixtures), AnomalyDetectionServiceTest (spike triggers alert, below threshold no alert), AuditLogServiceTest (verify save called, verify no update called on repo)
- [ ] 6.15 Write integration test: AuditLogIntegrationTest — perform campaign create → verify audit_logs has 1 entry with correct before/after JSON; ExportIntegrationTest — finance user GET /analytics/export → 200 with CSV, ExportLog created; non-finance GET → 403

**Phase 6 checkpoint: Audit log shows entries in browser. Analytics dashboard displays real data from seed. Finance export blocked for non-finance role.**

---

## PHASE 7 — Complete UI Polish, Role Dashboards & All Remaining Templates
> Goal: Every page is eye-catching, every role has a proper dashboard, no broken pages, empty states everywhere
> QA manually navigates every page — it must look and feel like a real product.
> Complete all tasks continuously, then pause. Wait for "proceed".

- [ ] 7.1 Create UserService.java: createUser(request) — validates password complexity, BCrypt hash, save, auditLog; updateUser(id, request) — before/after snapshot, update, auditLog; deactivateUser(id) — sets is_active=false, invalidates sessions; changeRole(userId, newRole) — auditLog(ROLE_CHANGED, before, after) — requires ADMIN
- [ ] 7.2 Create AdminController.java (full): GET /admin/dashboard, GET /admin/users (list), GET /admin/users/new (form), POST /admin/users (create), GET /admin/users/{id}/edit, PUT /admin/users/{id} (update), POST /admin/users/{id}/deactivate, GET /admin/users/check-username (HTMX unique check), POST /admin/backup/run, GET /admin/backup/history
- [ ] 7.3 Create templates/admin/dashboard.html: 6 KPI cards (total users, active campaigns, pending approvals, unacknowledged alerts, last backup status, pending content imports). Quick links to all admin functions. Alert banner if unacknowledged HIGH/CRITICAL anomaly alerts.
- [ ] 7.4 Create templates/dashboard/ops.html: My Campaigns table (with status badges + edit buttons), quick "Create Campaign" primary button, "Pending Review" count badge in header.
- [ ] 7.5 Create templates/dashboard/reviewer.html: approval queue count as large badge, list of high-risk pending items, recent approvals history table.
- [ ] 7.6 Create templates/dashboard/finance.html: analytics summary cards, recent export log, Export to CSV button prominent.
- [ ] 7.7 Create templates/dashboard/cs.html: read-only campaign list (no create/edit buttons), coupon code lookup form (enter code → see campaign details and validity).
- [ ] 7.8 Create templates/admin/users.html: full user management table (username, role badge, status badge, last login, actions). Create User button top right. Deactivate button (confirm modal). Role change inline select with "Save Role" button.
- [ ] 7.9 Create templates/admin/users-form.html: create/edit user form. Password field with JavaScript strength indicator (weak/medium/strong colored bar). HTMX check for username uniqueness on blur (calls /admin/users/check-username). Role select with role descriptions shown below.
- [ ] 7.10 Create templates/admin/backup.html: backup history table (filename, size, date, status, restore notes), "Run Backup Now" button (POST /admin/backup/run), recovery procedure section clearly documented.
- [ ] 7.11 Final pass — verify ALL templates use th:layout:decorate="~{layout/base}" and correctly extend base layout with no missing fragments
- [ ] 7.12 Final pass — verify ALL action buttons that are role-restricted have th:disabled="${!#authorization.expression('hasRole(...)')}" AND th:title="'[Role] permission required'" for "why blocked" message
- [ ] 7.13 Final pass — verify ALL pages have empty state handling: th:if="${#lists.isEmpty(items)}" shows empty state card with icon + message + primary action button
- [ ] 7.14 Final pass — verify ALL forms have success flash message handling: if model has "successMessage" attribute → Bootstrap 5 alert div visible
- [ ] 7.15 Add HTMX validation endpoints to CampaignController: GET /campaigns/validate/dates?startDate=&endDate= → returns fragment with validation message div; GET /campaigns/validate/discount?type=&value= → returns fragment; GET /campaigns/validate/code?code= → returns fragment showing available/taken
- [ ] 7.16 Verify visual consistency: all pages use same card style, same button classes, same badge styles, same spacing. No pages with raw unstyled HTML.

**Phase 7 checkpoint: All 5 roles can navigate their full dashboard without broken pages. Every page has empty state. All disabled actions show "why blocked".**

---

## PHASE 8 — Complete Test Suite, Security Hardening & Docker Verification
> Goal: docker compose up --build works perfectly, all tests pass, no System.out.println, clean code
> This is the final code phase. QA runs Docker here.
> Complete all tasks continuously, then pause. Wait for "proceed".

- [ ] 8.1 Final security audit: grep -r "@PermitAll\|permitAll()" src/main — must only be /login, /captcha/**, /actuator/health, /static/**. Fix any over-permissive rules.
- [ ] 8.2 Final audit log audit: list every service method that writes to DB → verify AuditLogService.log() is called. Add any missing calls. Comment each call with "// AUDIT: reason"
- [ ] 8.3 Final System.out.println audit: grep -r "System.out.println" src/main/java — must return zero results. Replace any found with log.info/warn/error via SLF4J.
- [ ] 8.4 Final configuration audit: grep -r "\${" src/main/resources/application.yml — must return zero results (no ${ENV_VAR} references allowed — all values must be hardcoded so QA needs zero configuration). Verify docker-compose.yml has no ${VAR} syntax either.
- [ ] 8.5 Write complete missing unit tests to cover all service classes:
       AuthServiceTest, CampaignServiceTest, CouponServiceTest, ApprovalServiceTest,
       DualApprovalServiceTest, FingerprintServiceTest, DuplicateDetectionServiceTest,
       MergeServiceTest, ChunkedUploadServiceTest, WatermarkServiceTest,
       TempDownloadLinkServiceTest, AnalyticsServiceTest, AnomalyDetectionServiceTest,
       BackupServiceTest, UserServiceTest
- [ ] 8.6 Write comprehensive security integration tests (SecurityIntegrationTest.java):
       - Anonymous request to /campaigns → 302 /login
       - POST /campaigns without CSRF token → 403
       - OPERATIONS user GET /admin/users → 403
       - FINANCE user GET /admin/users → 403
       - CS user POST /campaigns → 403 (read-only)
       - 11th export request within 1 minute → 429 with Retry-After header
       - Duplicate nonce on dual-approve → 400
       - 6th wrong password same account → subsequent login returns locked message
       - 21st failed attempt from same IP → subsequent login returns ip-blocked message
- [ ] 8.7 Write object-level authorization test: same reviewer cannot approve own submitted campaign (throws exception), dual approval same user both steps rejected
- [ ] 8.8 Run docker compose up --build → verify:
       - App starts and is accessible at http://localhost:8080
       - All 5 users can log in with correct credentials
       - Login page shows correctly (no broken CSS/JS since all vendor files are local)
       - Campaign list page loads with seed data
       - Analytics dashboard loads with seed data charts
       Fix any runtime errors before marking this task done.
- [ ] 8.9 Run docker compose -f docker-compose.test.yml run --build test → fix ALL test failures until exit code 0
- [ ] 8.10 Verify Flyway is sole schema manager: grep -r "ddl-auto" src/main/resources → only "validate" in production profiles, "create" allowed only in test profile
- [ ] 8.11 Verify no raw JDBC / SQL string concatenation: grep -r "Statement\b\|createStatement\|\"SELECT\|\"INSERT\|\"UPDATE\|\"DELETE" src/main/java -- include="*.java" | grep -v "//\|JPQL\|nativeQuery" → must return zero results. All DB access through JPA/JPQL named parameters.
- [ ] 8.12 Verify all vendor JS/CSS files are local: grep -r "cdn.jsdelivr\|unpkg.com\|cdn.bootstrap\|cdnjs" src/main/resources/templates → must return zero results (offline-first requirement)

**Phase 8 checkpoint: docker compose up --build → app works for all 5 roles. docker compose -f docker-compose.test.yml run test → exit 0. No System.out.println. No CDN references.**

---

## PHASE 9 — Documentation Generation
> Goal: Generate docs/design.md and docs/api-spec.md from actual implemented code
> Final phase — no pause needed after this one.

- [ ] 9.1 Create docs/design.md from actual implemented code:
       - ASCII architecture diagram (Browser → Nginx → Thymeleaf/Spring Boot → MySQL)
       - Docker service map with ports, volumes, dependencies
       - All 20 JPA entities and their key relationships (ERD as ASCII or table)
       - Security architecture: auth flow, lockout sequence, rate limit mechanism, nonce validation, CSRF flow, request signing
       - Business rules implemented (each from SPEC.md with class:method reference)
       - File storage layout: directory structure, permission inheritance
       - Content integrity pipeline: import → normalize → fingerprint → cluster → merge → rollback
       - Audit log design: why REQUIRES_NEW transaction, what triggers each audit action
       - Anomaly detection thresholds and schedule
       - Backup schedule, storage, retention and recovery procedure

- [ ] 9.2 Create docs/api-spec.md from actual implemented code:
       - Every controller endpoint: HTTP method, path, role required, request params/body (with validation rules), response shape, possible error codes
       - HTMX-specific endpoints noted (return HTML fragments, not JSON)
       - Auth endpoints: /login, /logout, /captcha/image, /captcha/validate
       - File upload endpoints with chunk protocol description
       - Content endpoints with merge and rollback details
       - Analytics/export with rate limit specification
       - Admin endpoints with nonce+signing headers specification
       - Standard error response format for all 4xx/5xx responses

---

## Execution Notes for Claude

- Complete ALL tasks in a phase without stopping between individual tasks
- Mark [x] immediately and continue to next task — never pause mid-phase
- Fix all errors within the same task before marking [x]
- Only pause at phase end after checkpoint passes
- At each pause: brief summary (files created, checkpoint result, any notable decisions)
- Wait for "proceed" before starting next phase
- Real data rule: if tempted to hardcode any value in a Thymeleaf template, stop and add a service method and repository query instead
- UI rule: every page must look polished and professional — QA will manually click through every screen
- Offline rule: no CDN links anywhere — all Bootstrap, HTMX, Chart.js, Inter font must be served from /static/vendor/
