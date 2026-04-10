# Security Design Decisions

This document records security-relevant design choices that are not obvious from the
code alone. Each entry explains the rule, the tradeoff, and the compensating controls.

## 1. Admin browser-form POSTs are exempt from nonce + signing

**Rule.** `NonceValidationFilter` and `RequestSigningFilter` bypass requests to
`/admin/**` that carry `Content-Type: application/x-www-form-urlencoded`. All other
content types under `/admin/**` (JSON, multipart/form-data, plain text) are subject to
the full anti-replay + HMAC signature check.

**Why not full signing for browser forms?** Admin UI actions are submitted by the
Thymeleaf forms in `templates/admin/*.html`. Generating per-request `X-Nonce`,
`X-Timestamp`, `X-Signature` headers from the browser would require every admin form
to be HTMX-driven with client-side signing JS and a nonce-issuance endpoint — a large
piece of work that does not materially raise the bar for our threat model. The browser
surface is already protected by a stack of independent controls (below). We revisit
this decision if any of those controls weakens.

**Compensating controls** (all active and testable):

1. **CSRF** — Spring Security's `CookieCsrfTokenRepository.withHttpOnlyFalse()` protects
   every POST. A missing/incorrect CSRF token yields HTTP 403 at the filter chain level
   before any controller runs. Covered by `SecurityIntegrationTest.postWithoutCsrfRejected`.
2. **Session auth** — Admin endpoints require an authenticated session. Covered by
   `SecurityIntegrationTest.anonymousAccessRedirectsToLogin`.
3. **ROLE_ADMIN** — Every controller method under `AdminController` is gated by class-
   level `@PreAuthorize("hasRole('ADMIN')")`, and `SecurityConfig` additionally enforces
   URL-level `.requestMatchers("/admin/**").hasRole("ADMIN")`. Double enforcement.
   Covered by `SecurityIntegrationTest.opsUserCannotAccessAdmin` and related tests.
4. **Audit log on every mutation** — `AdminController` calls `AuditLogService.log(...)`
   (directly or via downstream services) for every user/backup/role/alert mutation.
   Covered by `AuditLogIntegrationTest`.
5. **Rate limit** — `RateLimitFilter` throttles authenticated users to 60 req/min
   (10/min for export), per `application.yml` `app.rate-limit.*`. A brute-forcer who
   steals a session still hits the rate limit.
6. **Pre-auth lockout + CAPTCHA** — `PreAuthLockoutFilter` prevents credential
   stuffing from ever reaching the admin surface.
7. **Tight bypass shape** — Only `application/x-www-form-urlencoded` is exempt.
   `multipart/form-data` is NOT exempt, because there are no admin file-upload forms
   today, so any multipart POST to `/admin/**` must be a programmatic caller and is
   required to carry valid nonce + signature headers. Covered by
   `AdminFilterBypassTest.{signingFilterRejectsAdminMultipartWithoutSignature,nonceFilterRejectsAdminMultipartWithoutHeaders}`.

**What is still protected by nonce + signing?**

- JSON POSTs under `/admin/**` (API clients).
- Multipart POSTs under `/admin/**`.
- `POST /approval/dual-approve/**` (nonce-protected).
- Any future `/api/**` prefix.

**When to revisit.** Implement full browser-side signing if:

- CSRF protection is downgraded or moved to opt-in.
- The session model changes (stateless JWTs, etc.) such that session auth no longer
  provides per-request binding.
- Admin endpoints start accepting cross-origin POSTs.
- The audit log gains a mutation surface (today it is immutable — see #2 below).

## 2. audit_logs is immutable by construction AND enforced at the DB

- The `AuditLog` entity has no `@Setter`. Instances are constructed via `@Builder`.
- `AuditLogRepository` extends `Repository<AuditLog, Long>` directly instead of
  `JpaRepository`, so `delete*` and `saveAll` methods are not exposed.
- Flyway migration `V14__audit_log_immutability.sql` installs a MySQL trigger that
  rejects UPDATE and DELETE on `audit_logs` with a SQLSTATE 45000 error. Even a direct
  SQL client with full DB credentials cannot tamper with an existing row.
- The only legitimate write path is `AuditLogService.log(...)`, which always
  constructs a new row.

## 3. At-rest encryption uses AES-256-GCM with an env-injected master key

- `EncryptionService` derives a 256-bit AES key from `app.encryption.key` via SHA-256.
- `EncryptedStringConverter` is a JPA `AttributeConverter<String, String>` applied with
  `@Convert(converter = EncryptedStringConverter.class)` on sensitive String fields.
  First target: `BackupRecord.filePath`.
- The master key has NO plaintext fallback in `application.yml` — it must come from
  `ENCRYPTION_KEY` (or the legacy `APP_ENCRYPTION_KEY`) env var. `docker-compose.yml`
  supplies a local-dev default so `docker compose up --build` still works without
  manual configuration. In production, inject from a secret manager.
- Because the IV is random per call, encryption is non-deterministic. Encrypted
  columns cannot be used in `WHERE` clauses — pick fields for encryption that are
  only looked up by primary key.

## 4. Backup tool never sees the password on its command line

`BackupService` invokes `mysqldump` via `ProcessBuilder` and sets the password in the
`MYSQL_PWD` environment variable rather than as an `-p<password>` flag. This keeps the
secret out of `/proc/*/cmdline` and shell history. The new `RestoreService` follows
the same pattern.
