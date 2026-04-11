#!/bin/sh
set -e

echo "========================================"
echo "  Retail Campaign Test Suite"
echo "========================================"

# Use system mvn from the maven:3.9-eclipse-temurin-17-alpine base image.
# QA machines may not have the Maven wrapper locally — Docker image has mvn globally.
MVN="mvn --no-transfer-progress"

UNIT_FAILED=0
INTEGRATION_FAILED=0

echo ""
echo "--- Unit Tests (com.meridian.retail.unit.**) ---"
echo "    Pure JUnit/Mockito, no Spring context, no MySQL."
# Surefire is scoped to com/meridian/retail/unit/** in pom.xml.
$MVN test \
  -DfailIfNoTests=false \
  -Dspring.profiles.active=test 2>&1 || UNIT_FAILED=1

if [ $UNIT_FAILED -eq 0 ]; then
  echo "✅ Unit Tests PASSED"
else
  echo "❌ Unit Tests FAILED"
fi

echo ""
echo "--- Integration Tests (com.meridian.retail.integration.**) ---"
echo "    @SpringBootTest + real MySQL 8 (Testcontainers or mysql-test sibling)."
# Failsafe is scoped to com/meridian/retail/integration/** in pom.xml.
# `verify` drives the integration-test + verify goals from the failsafe plugin.
# `-Dskip.surefire.tests` would also work, but `-DskipTests` on surefire lets the
# `verify` phase still execute failsafe. We use -Dsurefire.skip=true explicitly so
# the unit phase is not re-run here.
$MVN verify \
  -Dsurefire.skip=true \
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
  echo "  Unit:        PASS"
  echo "  Integration: PASS"
  exit 0
else
  echo "  SOME TESTS FAILED"
  echo "  Unit:        $([ $UNIT_FAILED -eq 0 ] && echo PASS || echo FAIL)"
  echo "  Integration: $([ $INTEGRATION_FAILED -eq 0 ] && echo PASS || echo FAIL)"
  exit 1
fi
