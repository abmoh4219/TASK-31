# api-spec.md — Retail Campaign Governance HTTP Endpoints
Task ID: TASK-31

Generated from the actual implemented controllers at the end of Phase 8. Every path
below points at a real `@Mapping` in `repo/src/main/java/com/meridian/retail/controller/`.

---

## Conventions

- **Authentication**: Spring Security form-login on `/login`. All endpoints except the narrow public list below require an authenticated session.
- **Session cookie**: `JSESSIONID` (HttpOnly).
- **CSRF**: `XSRF-TOKEN` cookie (readable by JS), form token under hidden `_csrf` field, or header `X-CSRF-TOKEN` / `X-XSRF-TOKEN` for HTMX POSTs. Any POST/PUT/DELETE without the token → HTTP 403.
- **Content type**: most UI endpoints return HTML (Thymeleaf templates). HTMX validation endpoints return small HTML fragments. REST-style endpoints for file upload/status return `application/json`.
- **Error response format** (JSON endpoints and filter rejections):
  ```json
  {"error": "human-readable reason"}
  ```
  or when the default Spring Boot error handler fires:
  ```json
  {"timestamp":"...","status":403,"error":"Forbidden","path":"/..."}
  ```
- **Role names used below**: ADMIN, OPERATIONS, REVIEWER, FINANCE, CUSTOMER_SERVICE.

---

## Public endpoints (no authentication)

Matcher list in `SecurityConfig.filterChain` → `permitAll()`:
`/login`, `/captcha/**`, `/health`, `/actuator/health`, `/css/**`, `/js/**`, `/vendor/**`, `/error/**`

| Method | Path                        | Description |
|--------|-----------------------------|-------------|
| GET    | `/login`                    | Render `templates/auth/login.html`. Query params: `error`, `locked`, `ipblocked`, `logout`. Session attr `captchaRequired` drives the CAPTCHA section. |
| POST   | `/login`                    | Handled by Spring Security's `UsernamePasswordAuthenticationFilter`. Body: `username`, `password`, `_csrf` (form hidden), optional `captcha` when required. Success → role-based redirect. Failure → `/login?error` or `/login?locked` or `/login?ipblocked`. |
| POST   | `/logout`                   | Invalidates session, deletes cookies, redirects to `/login?logout`. |
| GET    | `/captcha/image`            | PNG of freshly generated CAPTCHA; plaintext stashed in session under `CAPTCHA_ANSWER`. Response headers: `Cache-Control: no-store`. |
| POST   | `/captcha/validate`         | Body `captcha=<input>`. Returns HTML fragment: ✓ accepted / ✗ incorrect (HTMX live check). |
| GET    | `/health`                   | `{"status":"UP","service":"retail-campaign"}` — custom health endpoint. |
| GET    | `/actuator/health`          | Spring Boot Actuator health (returns `UP` or `DOWN`). |
| GET    | `/css/**`, `/js/**`, `/vendor/**` | Static assets (Bootstrap 5, HTMX, Chart.js, Inter font, Bootstrap-Icons — all bundled locally). |

---

## Dashboard router

| Method | Path         | Role required   | Description |
|--------|--------------|-----------------|-------------|
| GET    | `/`          | authenticated   | Redirects to `/dashboard`. |
| GET    | `/dashboard` | authenticated   | Role router: ADMIN→`/admin/dashboard`, REVIEWER→`/approval/queue`, FINANCE→`/analytics/dashboard`, OPERATIONS/CS→`/campaigns`. |

---

## Campaigns (`CampaignController`)

| Method | Path                                 | Role required              | Request                                 | Response                                  |
|--------|--------------------------------------|----------------------------|-----------------------------------------|-------------------------------------------|
| GET    | `/campaigns`                         | any authenticated          | Query: `status`, `type` (filters)       | `templates/campaign/list.html` — paginated table. |
| GET    | `/campaigns/new`                     | OPERATIONS, ADMIN          | —                                       | `templates/campaign/form.html` — empty form. |
| POST   | `/campaigns`                         | OPERATIONS, ADMIN          | Form body: `CreateCampaignRequest` (`@Valid`) | Success → redirect `/campaigns` with flash. Validation error → form re-render with `errorMessage`. |
| GET    | `/campaigns/{id}/edit`               | OPERATIONS, ADMIN          | —                                       | `templates/campaign/form.html` — pre-populated. |
| PUT    | `/campaigns/{id}`                    | OPERATIONS, ADMIN          | Form body: `CreateCampaignRequest` (`@Valid`). Uses HiddenHttpMethodFilter (`_method=put`). | Success → redirect `/campaigns`. |
| POST   | `/campaigns/{id}/submit`             | OPERATIONS, ADMIN          | —                                       | Transitions DRAFT → PENDING_REVIEW, enqueues for review. Redirect `/campaigns`. |
| DELETE | `/campaigns/{id}`                    | OPERATIONS, ADMIN          | `_method=delete`                        | Soft delete (sets `deleted_at`). |

### HTMX validation endpoints (return HTML fragments, NOT JSON)

| Method | Path                                   | Description |
|--------|----------------------------------------|-------------|
| GET    | `/campaigns/validate/dates`            | Query: `startDate`, `endDate`. Returns ✓/✗ fragment. |
| GET    | `/campaigns/validate/discount`         | Query: `type` (`PERCENT`\|`FIXED`), `value`. Returns ✓/✗ fragment. |
| GET    | `/campaigns/validate/code`             | Query: `code`. Returns "Available" / "Already taken". |
| POST   | `/campaigns/preview-receipt`           | Form body: current `CreateCampaignRequest` fields. Returns `campaign/receipt-preview-fragment :: receipt` (monospace receipt block). |
| GET    | `/campaigns/{id}/preview-receipt`      | Same fragment for an existing campaign. |

### Validation rules for `CreateCampaignRequest`

```java
@NotBlank name (max 255)
@Size(max=4000) description
@NotNull CampaignType type  // COUPON | DISCOUNT
@NotNull RiskLevel riskLevel // LOW | MEDIUM | HIGH
@NotNull startDate
@NotNull endDate
```
Plus business rules enforced in `CampaignService.validateDateRange` (end > start, start ≥ today) and `validateDiscountValue` (> 0, percent ≤ 100).

---

## Approvals (`ApprovalController`)

Class-level `@PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")`.

| Method | Path                                   | Request                    | Response |
|--------|----------------------------------------|----------------------------|----------|
| GET    | `/approval/queue`                      | —                          | `templates/approval/queue.html` — card grid of PENDING items with risk badges. |
| POST   | `/approval/{id}/approve`               | Optional: `notes`          | 302 → `/approval/queue`. Throws `SameApproverException` → flash error if reviewer == requestedBy. |
| POST   | `/approval/{id}/reject`                | Optional: `notes`          | 302 → `/approval/queue`. Same same-approver guard. |
| POST   | `/approval/dual-approve/{requestId}`   | **Required headers**: `X-Nonce`, `X-Timestamp`. | Throws `SameApproverException` if approver1 == approver2. Also subject to `NonceValidationFilter`. |

**Object-level rule**: a reviewer cannot approve/reject a campaign they themselves submitted. Enforced at the service layer (`ApprovalService.approve/reject`) so the rule holds no matter which UI calls the method.

---

## Files (`FileController`) — Chunked upload + masked download

All upload endpoints require `OPERATIONS` or `ADMIN`.

### Upload landing page

| Method | Path           | Query       | Response |
|--------|----------------|-------------|----------|
| GET    | `/files/upload`| `campaignId`| `templates/upload/upload.html` — drag-drop zone + version history table. |

### Chunked upload protocol (JSON)

```
POST /files/upload/init?campaignId=<id>&filename=<name>&totalChunks=<n>
→ 200 JSON: {"uploadId": "<uuid>", "totalChunks": <n>}

POST /files/upload/chunk
  Headers: Upload-Id: <uuid>, Chunk-Index: <int>
  Body: multipart/form-data, part "chunk"
→ 200 JSON: {"uploadId":"...","totalChunks":N,"receivedChunks":[0,1,2],"progress":60,"status":"IN_PROGRESS"}

GET  /files/upload/status/{uploadId}
→ 200 JSON (same shape as above) or 404 if unknown

POST /files/upload/finalize/{uploadId}
→ 200 JSON: {"id": 42, "filename":"...", "version":1, "sha256":"<hex>", "size": <bytes>}
  Server-side: assemble chunks → Tika MIME check → SHA-256 → persist CampaignAttachment → audit log → cleanup temp
```

### Download

| Method | Path                                    | Role           | Description |
|--------|-----------------------------------------|----------------|-------------|
| GET    | `/files/attachment/{id}/download`       | authenticated  | Generates a temp link (10-min, user-bound) and redirects to `/files/download/{token}`. |
| GET    | `/files/download/{token}`               | authenticated  | Resolves the token via `TempDownloadLinkService.resolve` — marks link used (single-use), enforces username match and expiry. Then calls `MaskedDownloadService.serve` which streams either the original (if authorized) or a watermarked rendition (if internal-only + role not in `masked_roles`) or returns 403 (non-watermarkable binary). |
| GET    | `/files/attachment/{id}/history`        | authenticated  | Version history page. |

**Error responses for `/files/download/{token}`**:
- **410 GONE** — `LinkExpiredException` (expired or already used)
- **403 Forbidden** — token bound to a different user OR non-watermarkable binary + unauthorized role
- **200** — streams the file with `Content-Disposition: attachment; filename="..."`

**Allowed MIME types**: `application/pdf`, `image/jpeg`, `image/png`, `image/gif`. Validated by Apache Tika magic-byte sniff, NOT by file extension. Max size 50 MB (configurable via `app.upload.max-file-size-bytes`).

---

## Content Integrity (`ContentController`)

Class-level `@PreAuthorize("hasAnyRole('OPERATIONS','REVIEWER','ADMIN')")`.

| Method | Path                                        | Role required           | Request | Response |
|--------|---------------------------------------------|-------------------------|---------|----------|
| GET    | `/content`                                  | OPERATIONS/REVIEWER/ADMIN | —     | `templates/content/list.html` — table + import modal. |
| GET    | `/content/duplicates`                       | OPERATIONS/REVIEWER/ADMIN | —     | `templates/content/duplicates.html` — card grid of duplicate groups. |
| POST   | `/content/merge`                            | REVIEWER, ADMIN          | `masterId` (Long), `duplicateIds` (List<Long>) | Calls `MergeService.merge` — snapshots before, sets `MERGED` status + `master_id`, persists `ContentMergeLog`. |
| GET    | `/content/{id}/history`                     | OPERATIONS/REVIEWER/ADMIN | —     | `templates/content/history.html` — version timeline. |
| POST   | `/content/{id}/rollback/{version}`          | REVIEWER, ADMIN          | —      | Snapshots current, restores target version fields, audits. |
| POST   | `/content/import/csv`                       | OPERATIONS/REVIEWER/ADMIN | multipart `file`, `campaignId` | Parses CSV (columns `title,source_url,body_text`), normalizes URL, fingerprints, saves each row. Returns flash summary. |
| POST   | `/content/import/single`                    | OPERATIONS/REVIEWER/ADMIN | `campaignId`, `title`, `sourceUrl`, `body` | Manual single-item import. |

---

## Analytics (`AnalyticsController`)

| Method | Path                      | Role required            | Request | Response |
|--------|---------------------------|--------------------------|---------|----------|
| GET    | `/analytics/dashboard`    | FINANCE, ADMIN, OPERATIONS | Query: `storeId`, `from` (YYYY-MM-DD), `to` (YYYY-MM-DD) | `templates/analytics/dashboard.html` — 4 KPI cards + top-campaigns bar chart (Chart.js, local). |
| GET    | `/analytics/export`       | FINANCE, ADMIN            | —       | `text/csv` + `Content-Disposition: attachment; filename="redemptions.csv"` via `StreamingResponseBody`. **Rate-limited to 10 req/min per user** by `RateLimitFilter` (other endpoints allow 60/min). |

The export call writes `ExportLog` + `AuditLog(EXPORT_GENERATED)` + `ChangeEvent(EXPORT)` BEFORE streaming begins so even a partial download leaves a tamper-evident trail. Repeated exports within 10 min trigger a `REPEATED_EXPORT` anomaly alert.

---

## Administration (`AdminController`)

Class-level `@PreAuthorize("hasRole('ADMIN')")`. URL also matched by `/admin/**` in SecurityConfig — defence in depth.

### Dashboard

| Method | Path                  | Response |
|--------|------------------------|----------|
| GET    | `/admin/dashboard`     | `templates/dashboard/admin.html` — 6 KPI cards from real DB queries (totalUsers, activeUsers, activeCampaigns, draftCampaigns, pendingApprovals, unacknowledgedAlerts, contentItemCount) + last backup card + warning banner for unacknowledged HIGH alerts. |

### Audit logs

| Method | Path                  | Query        | Response |
|--------|------------------------|--------------|----------|
| GET    | `/admin/audit-log`     | `page` (default 0) | `templates/audit/log.html` — paginated 50/page, expandable before/after JSON diff. ADMIN ONLY badge. |
| GET    | `/admin/sensitive-log` | `page` (default 0) | `templates/audit/sensitive-log.html` — sensitive-field access history. |

### Anomaly alerts

| Method | Path                              | Response |
|--------|-----------------------------------|----------|
| GET    | `/admin/anomaly-alerts`           | `templates/admin/anomaly-alerts.html` — table with severity badges, unacknowledged highlighted amber. |
| POST   | `/admin/anomaly-alerts/{id}/ack`  | Sets `acknowledged_by` + `acknowledged_at`. 302 → `/admin/anomaly-alerts`. |

### Backups

| Method | Path                  | Response |
|--------|------------------------|----------|
| GET    | `/admin/backup`        | `templates/admin/backup.html` — history table + Run Backup Now button + recovery procedure. |
| POST   | `/admin/backup/run`    | Calls `BackupService.runManualBackup` — ProcessBuilder mysqldump + gzip → SHA-256 → persist record → prune expired (>14 days). 302 → `/admin/backup`. |

### User management

| Method | Path                                    | Response |
|--------|-----------------------------------------|----------|
| GET    | `/admin/users`                          | `templates/admin/users.html` — full user table with role/status badges, last-login column. |
| GET    | `/admin/users/new`                      | `templates/admin/users-form.html` — blank form with password strength meter + HTMX username uniqueness. |
| POST   | `/admin/users`                          | Body: `username`, `password`, `fullName`, `role`. Runs `PasswordValidationService.validate` (12 chars + upper + lower + digit + special). BCrypt strength-12 hash. |
| GET    | `/admin/users/{id}/edit`                | Edit form. |
| POST   | `/admin/users/{id}/update`              | Body: `fullName`, `role`. Emits `USER_UPDATED` + `USER_ROLE_CHANGED` (if role diff). |
| POST   | `/admin/users/{id}/deactivate`          | Sets `is_active = false`. |
| GET    | `/admin/users/check-username`           | HTMX endpoint. Query: `username`. Returns Available / Already-taken HTML fragment. |

### POST /admin/** special headers

All POST endpoints under `/admin/**` are additionally subject to:
- **`NonceValidationFilter`** — requires `X-Nonce` and `X-Timestamp` headers, validates ±5-min skew and no replay
- **`RequestSigningFilter`** — requires `X-Signature` header containing `HMAC-SHA256(method + "\n" + path + "\n" + X-Timestamp + "\n" + SHA256(body, hex))` using the `app.signing.secret` key

---

## Customer Service (`CustomerServiceController`)

Class-level `@PreAuthorize("hasAnyRole('CUSTOMER_SERVICE','ADMIN')")`.

| Method | Path           | Query      | Response |
|--------|----------------|------------|----------|
| GET    | `/cs/lookup`   | `code`     | `templates/dashboard/cs.html` — coupon lookup form + result panel with campaign + discount details. |

---

## Rate Limit Specification

| Endpoint pattern       | Limit           | Implementation |
|------------------------|-----------------|----------------|
| `/analytics/export/**` | 10 req / min / user | `RateLimitFilter` export bucket |
| all other authenticated | 60 req / min / user | `RateLimitFilter` standard bucket |
| anonymous (login page) | no Bucket4j limit | Mitigated by lockout: 20 failures / 60 min / IP |

On exhaustion the filter writes **HTTP 429** with headers `Retry-After: 60` and body `{"error":"Rate limit exceeded. Try again in 60 seconds."}`.

---

## Standard Error Responses

| HTTP | When | Body |
|------|------|------|
| 302  | Anonymous access to authenticated endpoint | `Location: /login` |
| 302  | Login failure | `Location: /login?error`, `/login?locked`, or `/login?ipblocked` |
| 400  | `NonceValidationFilter` — missing headers, skew out of range, replay detected | `{"error":"..."}` |
| 403  | CSRF failure (no form `_csrf` or wrong value) | Spring default error template (`templates/error/403.html`) |
| 403  | `@PreAuthorize` denies access | `templates/error/403.html` via Spring Boot's `BasicErrorController` |
| 403  | `RequestSigningFilter` — missing or mismatched X-Signature | `{"error":"Invalid request signature"}` |
| 403  | `TempDownloadLinkService.resolve` — token bound to different user | Spring default |
| 404  | Controller `not found` exceptions (e.g. bad campaign id) | Spring default `templates/error/404.html` |
| 410  | `LinkExpiredException` — temp download link expired or already used | `Gone` status sent via `response.sendError` |
| 429  | Rate limit exceeded | `{"error":"Rate limit exceeded..."}` + `Retry-After: 60` |
| 500  | Unhandled exception | Spring default `templates/error/500.html` |

---

## Filter Chain Order

Registered in `SecurityConfig.filterChain`, all inserted `addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)`:

```
1. RateLimitFilter          — throttle first, stops attackers before any work
2. NonceValidationFilter    — reject replays before we spend CPU on signing
3. RequestSigningFilter     — verify HMAC on /admin/** POSTs
4. [Spring Security stock]  — CSRF, Session, UsernamePasswordAuth, @PreAuthorize
```

Spring Security's standard filter order (CSRF, Session, Auth) runs after ours so our custom filters fire before any authentication logic — this means rate limit applies to anonymous traffic too (useful for `/analytics/export` in authenticated mode, harmless otherwise).
