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
echo "--- Unit Tests (Mockito + plain JUnit, no Spring context, no DB) ---"
# Unit tests here are pure Mockito tests under src/test/java/com/meridian/retail/service,
# security, backup. They do NOT load Spring and do NOT touch MySQL — safe to run on any
# machine. (The *Policy/*Filter/*Validator/*Service name patterns all match this set.)
$MVN test \
  -Dtest="*ServiceTest,*FilterTest,*ValidatorTest,*UtilTest,*MapperTest,*PolicyTest,*SafetyTest" \
  -DfailIfNoTests=false \
  -Dspring.profiles.active=test 2>&1 || UNIT_FAILED=1

if [ $UNIT_FAILED -eq 0 ]; then
  echo "✅ Unit Tests PASSED"
else
  echo "❌ Unit Tests FAILED"
fi

echo ""
echo "--- Integration Tests (full Spring context + REAL MySQL 8) ---"
# Integration tests boot @SpringBootTest and require a live MySQL 8 instance. Under
# docker-compose.test.yml the tests connect to the sibling mysql-test service (via the
# IT_DATASOURCE_URL env var set in that compose file). Outside compose, the
# AbstractIntegrationTest base class starts a Testcontainers MySQL instance on demand.
# These tests are the only ones that touch the DB and verify Flyway migrations, JPA
# schema validation, and the @PreAuthorize filter chain end-to-end.
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
