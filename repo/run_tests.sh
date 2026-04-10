#!/bin/sh
set -e

echo "========================================"
echo "  Retail Campaign Test Suite"
echo "========================================"

FAILED=0

echo ""
echo "--- Unit Tests ---"
./mvnw test -Dtest="**/*Test,**/*Tests" \
  -Dspring.profiles.active=test \
  --no-transfer-progress 2>&1 || FAILED=1

echo ""
echo "--- Integration Tests (Real MySQL via Testcontainers) ---"
./mvnw test -Dtest="**/*IntegrationTest,**/*IT" \
  -Dspring.profiles.active=test \
  --no-transfer-progress 2>&1 || FAILED=1

echo ""
echo "========================================"
if [ $FAILED -eq 0 ]; then
  echo "  ALL TESTS PASSED"
else
  echo "  SOME TESTS FAILED"
fi
echo "========================================"

exit $FAILED
