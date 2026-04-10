#!/bin/sh
set -e

echo "========================================"
echo "  Retail Campaign Test Suite"
echo "========================================"

# Use system mvn from the maven:3.9-eclipse-temurin-17-alpine base image
# Never use ./mvnw — QA machines may not have Maven wrapper locally installed
# and the Docker image already has mvn available globally
MVN="mvn --no-transfer-progress"

UNIT_FAILED=0
INTEGRATION_FAILED=0

echo ""
echo "--- Unit Tests (Service layer, no DB required) ---"
$MVN test \
  -Dtest="*ServiceTest,*FilterTest,*ValidatorTest,*UtilTest,*MapperTest" \
  -DfailIfNoTests=false \
  -Dspring.profiles.active=test 2>&1 || UNIT_FAILED=1

if [ $UNIT_FAILED -eq 0 ]; then
  echo "✅ Unit Tests PASSED"
else
  echo "❌ Unit Tests FAILED"
fi

echo ""
echo "--- Integration Tests (Full Spring context + real MySQL) ---"
$MVN test \
  -Dtest="*IntegrationTest,*IT,SecurityIntegrationTest" \
  -DfailIfNoTests=false \
  -Dspring.profiles.active=test 2>&1 || INTEGRATION_FAILED=1

if [ $INTEGRATION_FAILED -eq 0 ]; then
  echo "✅ Integration Tests PASSED"
else
  echo "❌ Integration Tests FAILED"
fi

echo ""
echo "========================================"
if [ $UNIT_FAILED -eq 0 ] && [ $INTEGRATION_FAILED -eq 0 ]; then
  echo "  ALL TESTS PASSED"
  exit 0
else
  echo "  SOME TESTS FAILED"
  echo "  Unit: $([ $UNIT_FAILED -eq 0 ] && echo PASS || echo FAIL)"
  echo "  Integration: $([ $INTEGRATION_FAILED -eq 0 ] && echo PASS || echo FAIL)"
  exit 1
fi
