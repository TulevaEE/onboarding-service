package ee.tuleva.onboarding.config;

import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers configuration that provides PostgreSQL containers for tests running in CI mode.
 *
 * <p>Activated by the "ci" profile (set via SPRING_PROFILES_ACTIVE=ci,test in CircleCI).
 *
 * <p>Spring Boot's {@code @ServiceConnection} automatically configures the datasource from the
 * container's connection details. This is the idiomatic Spring Boot 3.1+ approach for
 * Testcontainers integration.
 *
 * <p>Each Spring test context gets its own isolated PostgreSQL container. This provides true test
 * independence when running with Gradle's maxParallelForks. Spring's context caching minimizes the
 * number of containers needed. The containers use tmpfs for PostgreSQL data (faster) and
 * test-optimized settings (fsync=off).
 *
 * <p>For local development without the ci profile, tests use H2 in-memory database instead.
 */
@TestConfiguration(proxyBeanMethods = false)
@Profile("ci")
public class TestcontainersConfiguration {

  @Bean
  @ServiceConnection
  @SuppressWarnings("resource")
  PostgreSQLContainer<?> postgresContainer() {
    return new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"))
        .withTmpFs(Map.of("/var/lib/postgresql/data", "rw")) // Use tmpfs for speed
        .withCommand(
            "postgres",
            "-c",
            "timezone=UTC",
            "-c",
            "fsync=off", // Disable fsync for tests (faster writes)
            "-c",
            "max_connections=100",
            "-c",
            "shared_buffers=256MB"); // Optimize for test workload
  }
}
