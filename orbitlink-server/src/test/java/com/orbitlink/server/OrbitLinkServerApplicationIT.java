package com.orbitlink.server;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the whole application against a real PostgreSQL container.
 *
 * <p>Named *IT so Failsafe runs it during `verify` rather than Surefire
 * running it during `test`. That split matters: this test needs Docker, and
 * keeping it out of the fast unit-test phase means `mvn test` stays runnable
 * on a machine with no Docker daemon.
 *
 * <p>An in-memory database such as H2 would make this faster, but it would
 * also test a database this project never runs on. Postgres-specific
 * behaviour — types, constraint semantics, and the Flyway migrations added in
 * phase 2 — is exactly what needs verifying, so the container is the point.
 */
/*
 * disabledWithoutDocker: skip rather than fail when no usable Docker daemon is
 * present. On Linux CI this runs for real. On Docker Desktop 4.83 for macOS it
 * currently skips: that build's socket answers docker-java's /info probe with
 * HTTP 400 and a stub body, which Testcontainers reports as "Could not find a
 * valid Docker environment" even though the CLI and curl both work fine
 * against the same socket.
 *
 * The tradeoff is real and worth knowing: a skip is green, so if CI ever lost
 * its Docker daemon this test would quietly stop verifying anything. Phase 8
 * should make Docker availability an explicit CI precondition rather than an
 * assumption.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class OrbitLinkServerApplicationIT {

    /**
     * static, so one container is shared by every test method in this class
     * rather than started and torn down per method. Testcontainers stops it
     * when the JVM exits.
     */
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("orbitlink")
                    .withUsername("orbitlink")
                    .withPassword("orbitlink");

    /**
     * The container's port is assigned at runtime, so the datasource URL
     * cannot be a static value in a properties file. @DynamicPropertySource
     * injects it after the container starts but before the Spring context is
     * built.
     */
    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoadsAndDatabaseIsReachable() throws Exception {
        assertThat(dataSource).isNotNull();
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.isValid(5)).isTrue();
            assertThat(connection.getMetaData().getDatabaseProductName())
                    .isEqualTo("PostgreSQL");
        }
    }
}
