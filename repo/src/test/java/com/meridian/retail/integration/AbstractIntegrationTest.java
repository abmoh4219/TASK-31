package com.meridian.retail.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * Base class for all integration tests.
 *
 * Two modes:
 *   1. LOCAL DEV — Testcontainers spins up a fresh MySQL 8 container per JVM run (started
 *      lazily in {@link #registerProps}).
 *   2. INSIDE the compose `test` profile — the {@code mysql-test} sibling service from
 *      docker-compose.yml (profiles: ["test"]) is already running on the same docker
 *      network. We detect this via the IT_DATASOURCE_URL environment variable and skip
 *      Testcontainers entirely (Testcontainers' "host.docker.internal" port mapping
 *      cannot reach a docker-spawned container from inside another container).
 *
 * In either mode Flyway runs the real migrations and {@code ddl-auto} is forced to "validate".
 *
 * Note: We deliberately do NOT use {@code @Testcontainers} / {@code @Container} annotations
 * because they require an always-non-null container field, which prevents the conditional
 * external-DB mode.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    private static final String EXTERNAL_URL = System.getenv("IT_DATASOURCE_URL");
    private static final boolean USE_EXTERNAL = EXTERNAL_URL != null && !EXTERNAL_URL.isBlank();

    private static MySQLContainer<?> MYSQL;

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry r) {
        if (USE_EXTERNAL) {
            // Inside the compose `test` profile — connect to the sibling mysql-test service.
            r.add("spring.datasource.url", () -> EXTERNAL_URL);
            r.add("spring.datasource.username",
                    () -> System.getenv().getOrDefault("IT_DATASOURCE_USERNAME", "retail_user"));
            r.add("spring.datasource.password",
                    () -> System.getenv().getOrDefault("IT_DATASOURCE_PASSWORD", "retail_pass"));
        } else {
            // Local dev — lazily start a single MySQL container shared across the whole JVM.
            if (MYSQL == null) {
                MYSQL = new MySQLContainer<>("mysql:8.0")
                        .withDatabaseName("retail_campaign_test")
                        .withUsername("retail_user")
                        .withPassword("retail_pass")
                        // V14 installs immutability triggers. With binary logging enabled
                        // (MySQL 8 default) a non-SUPER user can't create triggers unless
                        // log_bin_trust_function_creators=ON.
                        .withCommand("--log-bin-trust-function-creators=ON");
                MYSQL.start();
                Runtime.getRuntime().addShutdownHook(new Thread(MYSQL::stop));
            }
            r.add("spring.datasource.url", MYSQL::getJdbcUrl);
            r.add("spring.datasource.username", MYSQL::getUsername);
            r.add("spring.datasource.password", MYSQL::getPassword);
        }
        r.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        r.add("spring.flyway.enabled", () -> "true");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}
