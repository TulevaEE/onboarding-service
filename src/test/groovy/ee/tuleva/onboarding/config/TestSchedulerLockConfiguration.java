package ee.tuleva.onboarding.config;

import java.util.Optional;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test configuration that provides a no-op LockProvider for integration tests. This allows
 * scheduled methods with @SchedulerLock annotations to be called directly in tests without
 * requiring actual database locking.
 */
@TestConfiguration
public class TestSchedulerLockConfiguration {

  @Bean
  @Primary
  @ConditionalOnProperty(
      name = "spring.test.context.cache.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public LockProvider testLockProvider() {
    return _ -> {
      // Always return a successful lock for tests
      SimpleLock noOp = () -> {};
      return Optional.of(noOp);
    };
  }
}
