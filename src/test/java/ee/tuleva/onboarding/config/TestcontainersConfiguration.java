package ee.tuleva.onboarding.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers configuration that provides a PostgreSQL container for tests running in CI mode.
 *
 * <p>Activated by the "ci" profile (set via SPRING_PROFILES_ACTIVE=ci,test in CircleCI).
 *
 * <p>Spring Boot's {@code @ServiceConnection} automatically configures the datasource from the
 * container's connection details. This is the idiomatic Spring Boot 3.1+ approach for
 * Testcontainers integration.
 *
 * <p>Each Spring test context gets its own isolated PostgreSQL container, similar to how H2
 * provides isolation. This ensures true test independence and avoids connection pool issues.
 *
 * <p>For local development without the ci profile, tests use H2 in-memory database instead.
 */
@TestConfiguration(proxyBeanMethods = false)
@Profile("ci")
public class TestcontainersConfiguration {

  @Bean
  @ServiceConnection
  PostgreSQLContainer<?> postgresContainer() {
    return new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"))
        .withCommand("postgres", "-c", "timezone=UTC");
  }
}
