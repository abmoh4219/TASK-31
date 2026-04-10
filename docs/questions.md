# questions.md — Retail Campaign Governance & Content Integrity
# Task ID: TASK-31
# Business Logic Questions & Clarifications Log
# Format: Question → My Understanding → Solution Implemented

---

## 1. SimHash Implementation — Which Java Library?

**Question:** The prompt requires SimHash/MinHash for near-duplicate detection. No specific Java library is mentioned. Guava, Simmetrics, and custom implementations all exist. Which approach fits an offline, no-internet-dependency deployment?

**My Understanding:** Since the system runs fully offline with no internet access, any library must be bundled in the Maven local repository. Guava is already a common Spring Boot transitive dependency. A custom SimHash implementation using Guava's Hashing.murmur3_128() as the per-token hash is the cleanest approach — no additional dependency, fully auditable code, and clearly readable for the static QA audit.

**Solution Implemented:** Custom SimHash in `FingerprintService.java`: tokenize text by whitespace, compute 128-bit Murmur3 hash per token, build 64-bit SimHash vector by summing bit contributions, finalize to long. Hamming distance ≤ 8 on the 64-bit result = near-duplicate. SHA-256 exact match check runs first (faster). Implementation is fully commented for QA traceability.

---

## 2. CAPTCHA — "Locally Generated" — No External Services

**Question:** The prompt says "optional on-screen CAPTCHA generated locally after repeated failures." Does this mean a custom image-based CAPTCHA, or can the system use reCAPTCHA or hCaptcha?

**My Understanding:** The prompt explicitly states "offline-first" and "without any internet dependency." External CAPTCHA services (reCAPTCHA, hCaptcha) require internet calls. "Locally generated" means the server generates the challenge image using Java's AWT/Graphics2D — no external service. This is sometimes called Kaptcha-style CAPTCHA.

**Solution Implemented:** `LocalCaptchaService.java` generates a `BufferedImage` (200×60px) with random 6-character alphanumeric text, drawn with randomized font size/angle, plus line noise and background noise using `java.awt.Graphics2D`. The answer is stored in `HttpSession`. Triggered after 3rd consecutive failed login for the same account. `/captcha/image` serves the image as `image/png`. The CAPTCHA challenge is session-scoped and regenerated on each refresh.

---

## 3. Chunked Upload Protocol — Tus or Custom?

**Question:** The prompt says "resumable large-file uploads." The Tus.io protocol is a standard for resumable uploads. Should the system implement Tus or a simpler custom chunked upload protocol?

**My Understanding:** Tus requires a specific server implementation and client library. For an offline-first back-office system where the client is a Thymeleaf page (server-rendered HTML + vanilla JS), a lightweight custom chunked protocol is more appropriate and keeps dependencies minimal. The requirement is resumability (not specifically Tus), meaning the client can resume from the last received chunk after a network interruption.

**Solution Implemented:** Custom chunked protocol with three endpoints: `POST /files/upload/init` (returns `uploadId`), `POST /files/upload/chunk` (sends chunk index + total chunks + binary data, server returns received chunks list for client state), `POST /files/upload/finalize` (assembles chunks, computes SHA-256, deletes temp files). Resume is achieved by calling `/files/upload/status/{uploadId}` which returns the list of already-received chunk indexes — the client skips those and resumes from the first missing chunk.

---

## 4. Watermarking — PDF and Images Both Required?

**Question:** The prompt says "downloads can be watermarked for internal-only assets." Does watermarking apply to both PDF and image file types, or only one?

**My Understanding:** Campaign attachments include "flyers, terms PDFs, store signage images" — so both PDFs and images are explicitly mentioned file types. Both must support watermarking when the asset is marked `is_internal_only = true`.

**Solution Implemented:** `WatermarkService.java` has two implementations: (1) PDF watermarking using Apache PDFBox — adds a diagonal semi-transparent text overlay "INTERNAL USE ONLY | {username} | {date}" on every page; (2) Image watermarking using imgscalr — overlays text on the image using Java Graphics2D with 50% alpha. The watermark is applied on download (not stored as a separate file) so the original is always preserved for authorized users.

---

## 5. Masked Download — Exact Behavior for Unauthorized Users

**Question:** The prompt says "sensitive attachments can be 'masked download' where only authorized roles can retrieve the original." What does an unauthorized user receive when they try to download a masked file — an error, a watermarked preview, or nothing?

**My Understanding:** The intent is a graduated access model, not a simple block. Unauthorized users should receive a watermarked/degraded version (so they know the file exists and can see non-sensitive content) while the original is protected. A hard block (403) would prevent any business use. The "masked" terminology suggests partial visibility.

**Solution Implemented:** `MaskedDownloadService.java` checks the file's `maskedRoles` JSON list against the requester's role. If authorized → serve original file via temp download link. If unauthorized → serve watermarked version (PDF: all text replaced with lorem ipsum + "MASKED CONTENT" overlay; images: pixelated + watermark overlay). If the file is binary-only (no watermarkable content) → return 403 with message "You do not have permission to access the original file. Contact your administrator." All access attempts (authorized and unauthorized) are audit-logged.

---

## 6. Account Lockout — Account vs IP — Different Tables?

**Question:** The prompt specifies two separate lockout rules: 5 failed attempts per account (15-min lock) and 20 failed attempts per IP per hour. Are these tracked in the same table or separately?

**My Understanding:** They are logically separate but can share the same `login_attempts` table with both `username` and `ip_address` columns. Two separate queries check the two different lockout conditions independently. An account can be locked even from a different IP (if 5 attempts come from multiple IPs targeting the same account), and an IP can be blocked even if it's attempting multiple different accounts.

**Solution Implemented:** Single `login_attempts` table with columns `(id, username, ip_address, attempted_at)`. `AccountLockoutService` runs two independent queries: `countByUsernameAndAttemptedAtAfter(username, now-15min) >= 5` for account lock, and `countByIpAddressAndAttemptedAtAfter(ip, now-60min) >= 20` for IP block. Both conditions are checked before allowing the authentication attempt to proceed. Lockout status is checked in `CustomAuthenticationFailureHandler` and surfaced to the login form via different query parameters (`?error`, `?locked`, `?ipblocked`).

---

## 7. Receipt Wording Preview — Format and Real-Time Rendering

**Question:** The prompt says "preview the customer-facing receipt wording before publishing." What format is this preview — plain text, HTML, or a receipt-style formatted block?

**My Understanding:** In retail/POS context, receipt wording is typically plain text formatted in a monospace style to simulate an actual receipt printout. The preview should show exactly what the customer would see on their receipt — not a design mockup, but the text content.

**Solution Implemented:** `ReceiptPreviewService.java` generates a text block from the campaign fields: discount description, conditions (min purchase), coupon code (if applicable), validity period, and any custom receipt_wording text. It's rendered as an HTMX partial response in a `<pre>` tag with monospace font and a border to simulate a receipt. The HTMX call fires `on change` from the receipt wording textarea, giving real-time preview as the user types. The preview panel is positioned to the right of the form.

---

## 8. Nonce Anti-Replay — Storage and Cleanup

**Question:** The prompt requires "anti-replay via nonce and timestamp validation." Where are used nonces stored and how long are they retained?

**My Understanding:** Nonces need server-side storage to detect replays. The timestamp window determines the relevant retention period. If the timestamp must be within ±5 minutes of server time, nonces only need to be retained for 10 minutes (5 min before + 5 min after the valid window). Storing nonces in MySQL is appropriate for this on-premise system.

**Solution Implemented:** `used_nonces` table with `(id, nonce VARCHAR(64), used_at TIMESTAMP, expires_at TIMESTAMP)`. `NonceValidationFilter` checks timestamp within ±5 minutes, then queries `used_nonces` for the nonce value. If found → 400 Replay Detected. If not found → insert into `used_nonces` with `expires_at = now + 10 minutes`. `UsedNonceCleanupTask` runs hourly (`@Scheduled(cron="0 0 * * * *")`) and deletes all rows where `expires_at < now`.

---

## 9. Request Signing — Algorithm and Key Management

**Question:** The prompt says "request signing for privileged endpoints." What signing algorithm, key format, and key storage approach fits an offline on-premise system?

**My Understanding:** HMAC-SHA256 is the standard for request signing in enterprise systems — it's symmetric (same key to sign and verify), stateless, and requires no PKI infrastructure. The signing secret must be externalized from source code (environment variable) and encrypted at rest.

**Solution Implemented:** `RequestSigningFilter.java` verifies an `X-Signature` header on all `POST /admin/**` and `POST /approval/dual-approve/**` requests. The signature is `HMAC-SHA256(method + "\n" + path + "\n" + timestamp + "\n" + SHA256(requestBody))` using `SIGNING_SECRET` from the `SIGNING_SECRET` environment variable (default dev value provided in docker-compose, must be changed in production). The secret is loaded via `@Value("${app.signing.secret}") String signingSecret` and never logged. Key rotation is documented: update `SIGNING_SECRET` env var and restart the container.

---

## 10. Campaign Stacking Rules — Mutual Exclusion vs Stackable

**Question:** The prompt mentions "campaign stacking and mutual-exclusion rules." How do stackable campaigns interact with mutual-exclusion groups? Can a campaign be in a mutual-exclusion group AND be marked stackable?

**My Understanding:** These are two separate but complementary controls: `is_stackable = true` means the coupon can be combined with other coupons in the same cart. `mutual_exclusion_group` (e.g., "SUMMER_PROMO") means only one coupon from that group can be applied per transaction. A coupon can be stackable with coupons from other groups but mutually exclusive within its own group.

**Solution Implemented:** `CouponService.checkStackingCompatibility(couponId, cartCouponIds)` applies two rules in sequence: (1) if the new coupon's `is_stackable = false` → reject if any coupon already in cart (full exclusion); (2) if the new coupon has a `mutual_exclusion_group` → reject if any cart coupon shares the same group (group exclusion). The `ApprovalService.validateStackingRules()` checks these rules at approval time across all active campaigns to detect system-wide conflicts before a campaign goes live.

---

## 11. Backup Strategy — mysqldump Inside Docker Container

**Question:** The prompt requires "nightly local backups with 14-day retention." In a Docker environment, how is mysqldump executed and where are backups stored?

**My Understanding:** In a docker-compose setup, the Spring Boot app container can execute `mysqldump` using `ProcessBuilder` if the `mysqldump` binary is available. Alternatively, the MySQL container can run the dump. For simplicity and reliability, the backup is run by a Spring `@Scheduled` task in the app container that invokes `mysqldump` with connection details, writing to a volume-mounted `/app/backups/` directory accessible to both containers.

**Solution Implemented:** `BackupService.java` uses `ProcessBuilder` to run `mysqldump -h{host} -u{user} -p{password} {database} | gzip > /app/backups/backup_{yyyyMMdd_HHmmss}.sql.gz`. The backup directory is a Docker named volume `backup-data` mounted at `/app/backups` in the app container. After each successful backup, a `BackupRecord` is saved to DB with filename, path, SHA-256 checksum, and status. Cleanup deletes files older than 14 days and marks their records as `DELETED`. The schedule runs at 2:00 AM daily.

---

## 12. Dual Approval — Same User Prevention

**Question:** The prompt says reviewers "can require dual approval for permission changes and high-risk campaign edits." Can the same reviewer approve both steps of a dual approval?

**My Understanding:** Dual approval exists specifically to prevent single-point compromise — one person should not be able to approve both steps. The system must enforce that `approver1_id != approver2_id`.

**Solution Implemented:** `DualApprovalService.recordSecondApproval(requestId, approverId)` checks `dualApprovalRequest.getApprover1Id().equals(approverId)` and throws `SameApproverException("The same user cannot provide both approvals")` if matched. This check happens at the service layer (not controller), so it cannot be bypassed by direct API calls. The error surfaces to the UI as a styled error message on the approval form. The restriction is also documented in the audit log when rejected.