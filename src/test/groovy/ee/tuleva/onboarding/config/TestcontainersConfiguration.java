package ee.tuleva.onboarding.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers configuration for PostgreSQL tests running in CI mode.
 *
 * <p>Activated by the "ci" profile (SPRING_PROFILES_ACTIVE=ci,test).
 *
 * <p>Container reuse is enabled for better performance - the same container is reused across test
 * runs via Testcontainers' built-in mechanism. Spring manages transactions for test isolation.
 *
 * <p>Local development uses H2 by default (without ci profile).
 */
@TestConfiguration(proxyBeanMethods = false)
@Profile("ci")
public class TestcontainersConfiguration {

  @Bean
  @ServiceConnection
  @SuppressWarnings("resource")
  PostgreSQLContainer<?> postgresContainer() {
    return new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"))
        .withCommand("postgres", "-c", "timezone=UTC", "-c", "fsync=off");
  }
}
