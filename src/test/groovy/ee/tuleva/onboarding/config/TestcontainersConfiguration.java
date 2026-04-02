package ee.tuleva.onboarding.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers configuration for local PostgreSQL tests.
 *
 * <p>Activated by the "pg" profile (SPRING_PROFILES_ACTIVE=pg,test).
 *
 * <p>A static singleton container is shared across all Spring contexts within a JVM fork, so Flyway
 * runs once per fork. Spring manages transactions for test isolation.
 *
 * <p>In CI, PostgreSQL is provided as a CircleCI service container instead.
 */
@TestConfiguration(proxyBeanMethods = false)
@Profile("pg")
public class TestcontainersConfiguration {

  @SuppressWarnings("resource")
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"))
          .withCommand(
              "postgres", "-c", "timezone=UTC", "-c", "fsync=off", "-c", "max_connections=300");

  static {
    POSTGRES.start();
  }

  @Bean
  @ServiceConnection
  PostgreSQLContainer<?> postgresContainer() {
    return POSTGRES;
  }
}
