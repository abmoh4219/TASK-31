# audit_report-2 Fix Check (Static Re-Inspection)

## Re-check Verdict

- **Overall:** Most previously reported issues are now statically addressed.
- **Result summary:**
  - **Fixed:** 8
  - **Partially Fixed:** 0
  - **Not Fixed / Still Partial:** 0

> **Update:** `RequestSigningFilter` now also covers the approval completion
> endpoints (`POST /approval/{id}/approve-first`, `POST /approval/{id}/approve-second`,
> `POST /approval/dual-approve/**`), closing the prior signing-scope gap on the
> high-risk dual-approval surface. Browser flows fetch a server-issued
> `_signature` from `POST /approval/sign-form`, and new integration tests in
> `SecurityIntegrationTest` assert both accept (valid signature) and reject
> (missing / invalid signature) paths.

Static-only boundary: this check is based on source/templates/docs inspection only; no runtime execution, Docker, or tests were run.

---

## Issue-by-Issue Status

### 1) High — Anti-replay/signing do not protect actual high-risk approval completion endpoint

- **Previous:** Fail
- **Current status:** **Fixed (statically)**
- **Evidence:**
  - Nonce filter now explicitly covers `/approval/{id}/approve-first|approve-second`: `src/main/java/com/meridian/retail/security/NonceValidationFilter.java:95-103`
  - UI still posts to `/{id}/approve-second`, but those forms are now nonce-armed (`class="js-nonce-form"`): `src/main/resources/templates/approval/queue.html:75-99`
  - Nonce injection script exists and auto-arms privileged approval/admin forms: `src/main/resources/static/js/nonce-form.js:1-121`
  - Script is globally loaded in base layout: `src/main/resources/templates/layout/base.html:72-73`
  - Nonce endpoints exist (`/approval/nonce`, `/admin/nonce`): `src/main/java/com/meridian/retail/controller/NonceController.java:29-44`

### 2) High — Request-signing/nonce controls broadly bypassed for admin browser form mutations

- **Previous:** Partial Fail
- **Current status:** **Fixed (statically)**
- **Evidence:**
  - Request-signing filter no longer bypasses browser form posts (`shouldNotFilter` returns false): `src/main/java/com/meridian/retail/security/RequestSigningFilter.java:153-154`
  - Filter enforces signature presence in header-or-form mode: `src/main/java/com/meridian/retail/security/RequestSigningFilter.java:123`
  - Admin form-signing endpoint exists: `src/main/java/com/meridian/retail/controller/NonceController.java:62`
  - Browser nonce/signature script now injects `_signature`: `src/main/resources/static/js/nonce-form.js:146`
- **Conclusion:** prior browser-form signing bypass finding is no longer accurate in the current static code.

### 3) High — Approval workflow does not enforce stacking/mutual-exclusion verification

- **Previous:** Fail
- **Current status:** **Fixed (statically)**
- **Evidence:**
  - Approval paths now call pre-approval conflict validation: `src/main/java/com/meridian/retail/service/ApprovalService.java:106-111`, `149-151`, `196-203`
  - New validation implementation for approval-time coupon conflicts: `src/main/java/com/meridian/retail/service/CouponService.java:170-228`

### 4) High — Analytics issuance metric semantically incorrect and not filter-aware

- **Previous:** Fail
- **Current status:** **Fixed (statically)**
- **Evidence:**
  - Issuance now uses filter-aware repository aggregation (store/date overlap): `src/main/java/com/meridian/retail/service/AnalyticsService.java:30-40`
  - New repository query for filtered issuance proxy: `src/main/java/com/meridian/retail/repository/CouponRepository.java:24-33`

### 5) High — Weekly restore-test automation not implemented

- **Previous:** Fail
- **Current status:** **Fixed (statically)**
- **Evidence:**
  - Weekly scheduled restore drill added: `src/main/java/com/meridian/retail/backup/RestoreService.java:149-167` (`@Scheduled(cron = "0 0 3 ? * SUN")`)

### 6) Medium — “Why blocked” permission explanation inconsistent across screens

- **Previous:** Partial Fail
- **Current status:** **Fixed (statically)**
- **Evidence:**
  - Sidebar now renders disabled role-gated items with explicit reason tooltips (`nav-disabled` + `title=...requires...`): `src/main/resources/templates/layout/sidebar.html:24-25`, `45-46`, `54-55`, `72-73`, `90-91`, `122-123`
  - Screen-level disabled action explanations remain present (campaign/coupon lists): `src/main/resources/templates/campaign/list.html:22`, `121`; `src/main/resources/templates/coupon/list.html:22`, `81`
- **Conclusion:** role-block explanation affordance is now consistently present across primary navigation and key action surfaces.

### 7) Medium — Encryption-at-rest narrowly applied only to backup file path

- **Previous:** Partial Fail
- **Current status:** **Fixed (for the previously reported narrow-scope defect)**
- **Evidence:**
  - Encryption converter now also applied to `BackupRecord.notes`: `src/main/java/com/meridian/retail/entity/BackupRecord.java:56`
  - Additional encrypted field in anomaly module: `src/main/java/com/meridian/retail/entity/AnomalyAlert.java:31`
- **Note:** Whether encryption coverage is fully sufficient to all threat models remains a separate architecture question, but the specific “only backup file path” finding is no longer accurate.

### 8) Low — README Docker-centric, no optional local fallback

- **Previous:** Partial
- **Current status:** **Fixed**
- **Evidence:**
  - Optional non-Docker local section now documented: `README.md:29-52`

---

## Final Fix-Check Conclusion

- The project has materially improved against the prior `audit_report-2` findings.
- **Remaining material gap from this list:** none identified in this re-check pass.
- Manual/runtime confirmation is still required for behavioral effectiveness of the nonce JS flow and operational restore drill execution cadence.
