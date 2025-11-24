package ee.tuleva.onboarding.config;

import net.javacrumbs.shedlock.core.LockConfiguration;
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

  /**
   * Provides a no-op LockProvider for tests. This bean takes precedence over the production
   * LockProvider in test contexts, allowing scheduled methods to execute without actual locking.
   */
  @Bean
  @Primary
  @ConditionalOnProperty(
      name = "spring.test.context.cache.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public LockProvider testLockProvider() {
    return new NoOpLockProvider();
  }

  private static class NoOpLockProvider implements LockProvider {
    @Override
    public java.util.Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
      // Always return a successful lock for tests
      return java.util.Optional.of(
          new SimpleLock() {
            @Override
            public void unlock() {
              // No-op
            }
          });
    }
  }
}
