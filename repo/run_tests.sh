#!/bin/sh
# Run all four test suites through Docker.
# Requirements: Docker and Docker Compose only — no local Java, Maven, or Node needed.
# Exit code: 0 if all suites pass, 1 if any fail.

set -e

UNIT_FAILED=0
API_FAILED=0
FRONTEND_FAILED=0
E2E_FAILED=0

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "========================================"
echo "  Retail Campaign Full Test Suite"
echo "========================================"

# ── 1. Backend unit tests (JUnit 5 + Mockito, no database) ──────────────────
echo ""
echo "--- [1/4] Backend Unit Tests ---"
echo "    JUnit 5 + Mockito, no Spring context, no database."
docker compose --profile unit-test run --rm --build unit-test 2>&1 || UNIT_FAILED=1

if [ $UNIT_FAILED -eq 0 ]; then
  echo "✅ Backend Unit Tests PASSED"
else
  echo "❌ Backend Unit Tests FAILED"
fi

# ── 2. Backend API tests (TestRestTemplate + real MySQL via Testcontainers) ──
echo ""
echo "--- [2/4] Backend API Tests ---"
echo "    Real HTTP (TestRestTemplate) + real MySQL (mysql-test sibling)."
docker compose --profile api-test run --rm --build api-test 2>&1 || API_FAILED=1

if [ $API_FAILED -eq 0 ]; then
  echo "✅ Backend API Tests PASSED"
else
  echo "❌ Backend API Tests FAILED"
fi

# ── 3. Frontend JavaScript unit tests (Jest inside Node.js container) ────────
echo ""
echo "--- [3/4] Frontend JS Unit Tests ---"
echo "    Jest + jsdom, covers upload.js and nonce-form.js."
docker run --rm \
  -v "${SCRIPT_DIR}/src:/src" \
  -w /src/test/frontend/unit_tests \
  node:20-alpine \
  sh -c "npm install --silent && npm test -- --forceExit" 2>&1 || FRONTEND_FAILED=1

if [ $FRONTEND_FAILED -eq 0 ]; then
  echo "✅ Frontend JS Unit Tests PASSED"
else
  echo "❌ Frontend JS Unit Tests FAILED"
fi

# ── 4. Playwright e2e tests (headless Chromium against the running app) ──────
echo ""
echo "--- [4/4] Playwright E2E Tests ---"
echo "    Headless Chromium against the real running Spring Boot app."
echo "    docker compose starts mysql+app and waits for service_healthy before launching."

# The playwright service (profile: e2e-test) declares depends_on: app: service_healthy.
# docker compose handles starting mysql → app → playwright in the correct order
# and waits for the app healthcheck to pass before running any Playwright tests.
docker compose --profile e2e-test run --rm --build playwright 2>&1 || E2E_FAILED=1

# Tear down the app stack (leave no containers running)
docker compose down --remove-orphans 2>/dev/null || true

if [ $E2E_FAILED -eq 0 ]; then
  echo "✅ Playwright E2E Tests PASSED"
else
  echo "❌ Playwright E2E Tests FAILED"
fi

# ── Summary ──────────────────────────────────────────────────────────────────
echo ""
echo "========================================"
echo "  Test Results"
echo "  Unit (backend):    $([ $UNIT_FAILED -eq 0 ] && echo PASS || echo FAIL)"
echo "  API (backend):     $([ $API_FAILED -eq 0 ] && echo PASS || echo FAIL)"
echo "  Unit (frontend):   $([ $FRONTEND_FAILED -eq 0 ] && echo PASS || echo FAIL)"
echo "  E2E (Playwright):  $([ $E2E_FAILED -eq 0 ] && echo PASS || echo FAIL)"
echo "========================================"

if [ $UNIT_FAILED -eq 0 ] && [ $API_FAILED -eq 0 ] && \
   [ $FRONTEND_FAILED -eq 0 ] && [ $E2E_FAILED -eq 0 ]; then
  echo "  ALL SUITES PASSED"
  exit 0
else
  echo "  SOME SUITES FAILED"
  exit 1
fi
