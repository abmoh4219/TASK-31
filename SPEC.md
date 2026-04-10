# SPEC.md — Retail Campaign Governance & Content Integrity System
# Task ID: TASK-31
# This is the single source of truth. Every decision traces back to this file.

## Original Business Prompt (Verbatim — Do Not Modify)

Develop a Retail Campaign Governance & Content Integrity system for brick-and-mortar and offline-first retail organizations to configure promotions, manage campaign assets, and maintain trustworthy marketing content without any internet dependency. The English UI is rendered with Thymeleaf templates and optimized for back-office speed: Operations users create coupons and limited-time discounts with clear, guided forms that show real-time validation feedback (e.g., invalid date ranges, negative discount values) and preview the customer-facing receipt wording before publishing. Administrators assign staff to built-in roles including operations, reviewer, customer service, finance, and admin, and each screen reflects permissions by hiding or disabling restricted actions while providing "why blocked" explanations. Reviewers work from an approval queue to verify campaign stacking and mutual-exclusion rules and can require dual approval for permission changes and high-risk campaign edits. Staff can upload campaign attachments (flyers, terms PDFs, store signage images) through a unified upload page that supports resumable large-file uploads and shows progress, checksum status, and version history; downloads can be watermarked for internal-only assets and sensitive attachments can be "masked download" where only authorized roles can retrieve the original. Content integrity tooling lets users import or add marketing content from multiple sources and see suspected duplicates grouped together; operators can merge duplicates into a single master record while retaining prior versions and a readable before/after change view. Campaign analytics dashboards summarize coupon issuance and redemption counts, discount utilization, and top-performing campaigns by store and date range, with export actions available to finance under strict logging. The backend uses Spring Boot to expose REST-style endpoints consumed by the Thymeleaf UI and to enforce business rules consistently at the service layer, persisting all data in MySQL with transactional integrity. Authentication is strictly local username and password; passwords must be at least 12 characters with complexity rules, stored as salted hashes, and logins lock for 15 minutes after 5 failed attempts per account and 20 failed attempts per IP per hour, with an optional on-screen CAPTCHA generated locally after repeated failures. All critical operations generate immutable audit logs capturing operator, IP, timestamp, and before/after diffs for campaign edits, coupon issuance rules, file access, import/export, approvals, and role/permission changes; sensitive-field access (e.g., employee notes) is separately logged and visible only to admins. API security includes CSRF protection for form posts, standardized input sanitization to reduce XSS risk, prepared statements/ORM protections against SQL injection, anti-replay via nonce and timestamp validation, request signing for privileged endpoints, and rate limiting (for example, 60 requests/minute per user for standard actions and 10/minute for exports). Attachments are stored locally with permission inheritance from the parent campaign, validated by allowed type and size (default 50 MB per file, chunked uploads supported), and checked using local file signatures and hashes rather than cloud scanning; temporary download links expire after 10 minutes and are bound to the requesting user. Deduplication normalizes URLs, computes fingerprints (hash plus SimHash/MinHash) to detect near-duplicates, and maintains version chains with rollback capability; change events are recorded so anomaly alerts can flag unusual spikes such as mass deletions or repeated exports. Reliability requirements include nightly local backups with 14-day retention, on-demand restore testing weekly, encryption at rest for critical secrets, and a documented recovery procedure to restore operations within 4 hours on a single on-prem server.

## Project Metadata

- Task ID: TASK-31
- Project Type: fullstack
- Language: Java 17
- Frontend: Thymeleaf + Bootstrap 5 + HTMX + Vanilla JS
- Backend: Spring Boot 3.x (Java)
- Database: MySQL 8
- ORM: Spring Data JPA + Hibernate
- Migrations: Flyway
- Infrastructure: Docker + docker-compose (fully containerized, no internet required)
- Testing: JUnit 5 + Spring Boot Test + Testcontainers (real MySQL)

> PRIORITY RULE: The original business prompt above takes absolute priority over metadata.
> Metadata supports the prompt — it never overrides it.

## Roles (from prompt — all must be implemented)

| Role | Permissions |
|---|---|
| operations | Create coupons, discounts, upload attachments, view analytics |
| reviewer | Approval queue, verify stacking/exclusion rules, require dual approval |
| customer_service | View campaigns, view content, limited read access |
| finance | Export analytics, view audit logs for exports |
| admin | Full access, user/role management, sensitive field access, restore/backup |

## Core Modules (all must be implemented)

1. **Authentication & Security** — Local login only, 12-char password complexity, salted BCrypt hashes, account lockout (5 attempts/account, 20/IP/hour, 15-min lock), local CAPTCHA after repeated failures, CSRF on all forms, nonce+timestamp anti-replay, request signing for privileged endpoints, rate limiting (60/min standard, 10/min exports)
2. **Campaign & Promotions** — Coupons + limited-time discounts, guided forms with real-time validation (HTMX), receipt wording preview before publish, stacking/mutual-exclusion rules, approval queue, dual approval for high-risk edits
3. **RBAC & Permission UI** — Role-based screen rendering via Thymeleaf Security dialect, "why blocked" explanations for disabled actions, dual approval for permission changes
4. **File Management** — Resumable chunked uploads (50MB default), progress + checksum + version history display, watermarked downloads for internal assets, masked download (role-gated original retrieval), temp download links (10-min expiry, user-bound), local file signature + hash validation (no cloud scanning), permission inheritance from parent campaign
5. **Content Integrity** — URL normalization, SHA-256 fingerprinting + SimHash near-duplicate detection, duplicate grouping UI, merge-to-master with version chain retention, before/after change view, rollback capability
6. **Audit Logs** — Immutable audit records (operator, IP, timestamp, before/after JSON diff) for every critical operation, separate sensitive-field access log (admin-only view)
7. **Analytics & Export** — Coupon issuance/redemption counts, discount utilization, top campaigns by store and date range, finance-only export with strict rate limiting + audit logging
8. **Anomaly Detection** — Change event recording, spike detection (mass deletions, repeated exports), alert surfacing in admin dashboard
9. **Reliability** — Nightly backup script (14-day retention), on-demand restore test, AES-256 encryption for secrets at rest, 4-hour recovery SLA documented in README

## QA Acceptance Criteria (built into every phase)

### 1. Hard Gates
- `docker compose up --build` → app at http://localhost:8080, no manual steps
- Static code is self-evidently correct (static-only audit — no runtime execution by QA)
- Every security feature visible in code with clear file:line traceability

### 2. Delivery Completeness
- All 9 modules fully implemented end-to-end
- Zero hardcoded/mock data in place of real logic
- Complete project structure, meaningful README

### 3. Engineering Quality
- Clear Spring Boot module/package separation (no god classes)
- Flyway migrations (never `spring.jpa.hibernate.ddl-auto=create`)
- Service layer enforces all business rules (not just controllers)

### 4. Security (highest priority for QA audit)
- Spring Security config clearly readable and correct
- CSRF filter active for all form POSTs
- Rate limiter visible in code (Bucket4j or equivalent)
- Nonce+timestamp middleware with clear implementation
- Account lockout clearly implemented in AuthService
- Audit log service called on EVERY critical operation

### 5. Tests
- JUnit 5 unit tests for every service class
- Spring Boot integration tests (Testcontainers + real MySQL) for every critical flow
- Security tests: 401, 403, lockout, CSRF rejection, rate limit breach
- `docker compose -f docker-compose.test.yml run --build test` → exit 0

### 6. UI Quality
- Bootstrap 5 + custom CSS, modern back-office aesthetic
- Every form: real-time validation feedback via HTMX
- Every page: loading state, empty state, error state
- "Why blocked" tooltip/message on every disabled action

## Non-Negotiable Delivery Rules

- `docker compose up --build` → app at http://localhost:8080
- `docker compose -f docker-compose.test.yml run --build test` → exit 0
- `run_tests.sh` exists at repo/ root, is chmod +x, called by Dockerfile.test
- All tests use real MySQL via Testcontainers — zero H2/in-memory DB
- Zero hardcoded data in Thymeleaf templates — all data from service layer
- Minimal README: Run / Test / Stop / Login only
- `.gitignore` covers: target/, *.class, .env*, *.log, mysql-data/
- No manual setup required after `git clone`
