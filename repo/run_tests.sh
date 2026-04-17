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

# Start app + mysql (default profile)
docker compose up -d mysql app

echo "Waiting for app to become healthy..."
WAIT=0
until docker compose exec -T app wget -qO- http://localhost:8080/health > /dev/null 2>&1; do
  WAIT=$((WAIT + 5))
  if [ $WAIT -ge 180 ]; then
    echo "App did not start within 180 s — skipping e2e tests."
    E2E_FAILED=1
    break
  fi
  sleep 5
done

if [ $E2E_FAILED -eq 0 ]; then
  # Reuse the already-pulled Playwright image (mcr.microsoft.com/playwright:v1.59.1-jammy).
  # Mount the e2e folder and point BASE_URL at the app service on the retail-net network.
  docker run --rm \
    --network retail-net \
    -v "${SCRIPT_DIR}/src/test/e2e:/e2e" \
    -e BASE_URL=http://app:8080 \
    mcr.microsoft.com/playwright:v1.59.1-jammy \
    sh -c "cd /e2e && npm install --silent && npx playwright install chromium && npx playwright test" 2>&1 \
    || E2E_FAILED=1
fi

# Tear down the app stack (leave no containers running)
docker compose down

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
