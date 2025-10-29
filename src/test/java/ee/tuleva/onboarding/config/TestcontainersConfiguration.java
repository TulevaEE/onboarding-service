package ee.tuleva.onboarding.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers configuration that provides a PostgreSQL container for tests running in CI mode.
 *
 * <p>When CI=true environment variable is set, Spring Boot's {@code @ServiceConnection}
 * automatically configures the datasource from the container's connection details. This is the
 * idiomatic Spring Boot 3.1+ approach for Testcontainers integration.
 *
 * <p>Each Spring test context gets its own isolated PostgreSQL container, similar to how H2
 * provides isolation. This ensures true test independence and avoids connection pool issues.
 *
 * <p>For local development without CI=true, tests use H2 in-memory database instead.
 *
 * <p>This configuration is automatically registered by {@link CiProfileActivatorFactory} when
 * CI=true.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  @Bean
  @ServiceConnection
  PostgreSQLContainer<?> postgresContainer() {
    return new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"))
        .withCommand("postgres", "-c", "timezone=UTC");
  }
}
