# Security Design Decisions

This document records security-relevant design choices that are not obvious from the
code alone. Each entry explains the rule, the tradeoff, and the compensating controls.

## 1. Privileged browser POSTs use nonce + server-issued signature form fields

**Rule (R4 audit re-check update).** Both `NonceValidationFilter` and
`RequestSigningFilter` are now active for every privileged browser POST — there is no
form-encoded bypass on either. Browser submissions reach the controller layer only when
they carry all three of:

- `_nonce` (single-use UUID, persisted to `used_nonces` after first use)
- `_timestamp` (epoch milliseconds, must be within ±5 minutes of server time)
- `_signature` (hex HMAC-SHA256 over `method + "\n" + path + "\n" + timestamp + "\n" + nonce`)

The signature is **issued by the server**, never computed in the browser, by
`POST /admin/sign-form` (and `POST /approval/sign-form`). These endpoints are gated by
Spring Security (`ROLE_ADMIN` / `ROLE_REVIEWER` + authenticated session + CSRF) and
return only the HMAC over the requested canonical tuple. The HMAC secret therefore
never leaves the JVM. `static/js/nonce-form.js` orchestrates the three-step flow
(fetch nonce → fetch signature → submit form).

**Why omit the body hash from the form-mode canonical?** The body itself contains the
`_signature` parameter — including the hash would create a chicken-and-egg loop. Body
integrity in form mode is provided by:

1. CSRF token (CookieCsrfTokenRepository) bound to the session.
2. Single-use nonce that prevents replay with a tampered body.
3. Same-origin policy + session auth that bind the request to the user.
4. Audit log on every mutation (immutable, DB-trigger-enforced).

Programmatic JSON / multipart callers continue to use the **header mode** of
`RequestSigningFilter`, which DOES include the body hash:

```
HMAC = HMAC-SHA256( method + "\n" + path + "\n" + X-Timestamp + "\n" + SHA256(body, hex) )
sent as X-Signature header alongside X-Nonce / X-Timestamp.
```

The two modes are mutually exclusive on a per-request basis: if `X-Signature` is
present the filter uses header mode, otherwise it falls back to form mode and reads
`_signature` / `_nonce` / `_timestamp` from the parameters.

**Why server-side signing instead of browser-side HMAC?** Browser-side HMAC requires
the shared secret to live in JavaScript, which trivially leaks it to any user with
devtools. By moving the signing operation to a server endpoint gated by Spring
Security, the HMAC secret never leaves the JVM. The endpoint is effectively a
"sign this canonical tuple if you are an authenticated admin" service — equivalent to
how short-lived OAuth tokens work, scoped to a single nonce + timestamp.

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
7. **No filter bypass for browser forms (R4 audit re-check fix)** — both
   `NonceValidationFilter` and `RequestSigningFilter` now run for every POST under
   `/admin/**`. Browser forms include `_nonce` / `_timestamp` / `_signature` hidden
   fields injected by `static/js/nonce-form.js`, with the signature fetched from
   `POST /admin/sign-form` (the only endpoint excluded from the filters, gated by
   Spring Security `ROLE_ADMIN`). Covered by
   `AdminFilterBypassTest.{nonceFilterRejectsAdminFormPostWithoutNonce,nonceFilterAcceptsAdminFormPostWithNonceFormFields,signingFilterRejectsAdminFormPostWithoutSignature,signingFilterAcceptsAdminFormPostWithValidSignatureFormFields,signingFilterRejectsAdminFormPostWithBadSignature,signingFilterSkipsSignFormEndpoint}`
   and `SecurityIntegrationTest.{adminFormPostWithSignedNonceFieldsPassesAllFilters,adminFormPostWithoutNonceRejectedByFilter,adminFormPostWithoutSignatureRejectedBySigningFilter}`.

8. **Multipart still rejected without headers** — `multipart/form-data` and JSON
   callers under `/admin/**` must still carry `X-Signature` + `X-Nonce` headers.
   Covered by
   `AdminFilterBypassTest.{signingFilterRejectsAdminMultipartWithoutSignature,nonceFilterRejectsAdminMultipartWithoutHeaders,nonceFilterStillRejectsAdminJsonWithoutHeaders}`.

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

### Data classification — encrypted-at-rest fields

| Entity                      | Field         | Why encrypted                                                  |
|-----------------------------|---------------|----------------------------------------------------------------|
| `BackupRecord`              | `filePath`    | Reveals on-disk backup location to a DB-dump attacker          |
| `BackupRecord`              | `notes`       | Includes operator names, exit codes, exception messages        |
| `AnomalyAlert`              | `description` | Reveals detection thresholds and attacker behaviour patterns   |

### Fields deliberately NOT encrypted (with justification)

| Entity                      | Field         | Reason                                                         |
|-----------------------------|---------------|----------------------------------------------------------------|
| `User`                      | `username`    | Used for login lookups (`WHERE username = ?`), not a secret    |
| `User`                      | `passwordHash`| Already a one-way BCrypt hash, encryption adds nothing         |
| `User`                      | `fullName`    | Display-only PII, low blast radius, kept queryable for admin UI|
| `Coupon`                    | `code`        | Looked up by value at redemption time (`WHERE code = ?`)       |
| `TempDownloadLink`          | `token`       | Looked up by value when the user clicks the link               |
| `Campaign` / `Coupon` body  | (all)         | Not classified as secret, used in queries and joins            |

Encryption is non-deterministic (random IV), so encrypted columns cannot be used in
`WHERE` clauses. We pick fields for encryption that are only ever loaded by primary
key — see the "deliberately NOT encrypted" table for the lookup-by-value exclusions.
- The master key has NO plaintext fallback in `application.yml` — it must come from
  `ENCRYPTION_KEY` (or the legacy `APP_ENCRYPTION_KEY`) env var. `docker-compose.yml`
  supplies a local-dev default so `docker compose up --build` still works without
  manual configuration. In production, inject from a secret manager.

## 4. Backup tool never sees the password on its command line

`BackupService` invokes `mysqldump` via `ProcessBuilder` and sets the password in the
`MYSQL_PWD` environment variable rather than as an `-p<password>` flag. This keeps the
secret out of `/proc/*/cmdline` and shell history. The new `RestoreService` follows
the same pattern.
