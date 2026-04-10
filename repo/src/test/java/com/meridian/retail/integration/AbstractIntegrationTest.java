package com.meridian.retail.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for all integration tests. Spins up a real MySQL 8 container via Testcontainers
 * and points the Spring Boot data source at it. Flyway is enabled here so the tests exercise
 * the same migrations production runs.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource") // container is reused for the JVM lifetime
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("retail_campaign_test")
            .withUsername("retail_user")
            .withPassword("retail_pass");

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry r) {
        if (!MYSQL.isRunning()) MYSQL.start();
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
        r.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        r.add("spring.flyway.enabled", () -> "true");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}
