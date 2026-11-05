package ee.tuleva.onboarding.config;

import java.time.Duration;
import java.util.Optional;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Test configuration that provides a no-op LockProvider for integration tests. This allows
 * scheduled methods with @SchedulerLock annotations to be called directly in tests without
 * requiring actual database locking.
 */
@Configuration
public class TestSchedulerLockConfiguration {

  @Bean
  @Primary
  @ConditionalOnProperty(
      name = "spring.test.context.cache.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public LockProvider testLockProvider() {
    return (ExtensibleLockProvider) _ -> Optional.of(new NoOpLock());
  }

  private static final class NoOpLock implements SimpleLock {

    @Override
    public void unlock() {}

    @Override
    public Optional<SimpleLock> extend(Duration lockAtMostFor, Duration lockAtLeastFor) {
      return Optional.of(this);
    }
  }
}
