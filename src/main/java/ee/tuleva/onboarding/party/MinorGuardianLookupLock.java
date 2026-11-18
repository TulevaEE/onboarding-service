package ee.tuleva.onboarding.party;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
class MinorGuardianLookupLock {

  private static final String NAME = "MinorGuardianLookup";
  private static final Duration A_STOPPED_RUN_HOLDS_THE_LOCK_AT_MOST = Duration.ofHours(3);

  private final ObjectProvider<LockProvider> lockProvider;
  private final Clock clock;

  MinorGuardianLookupLock(ObjectProvider<LockProvider> lockProvider, Clock clock) {
    this.lockProvider = lockProvider;
    this.clock = clock;
  }

  Optional<SimpleLock> acquire() {
    LockProvider provider = lockProvider.getIfAvailable();
    if (provider == null) {
      throw new IllegalStateException(
          "Minor guardian lookup refused to run unlocked: no ShedLock LockProvider on this node,"
              + " lock="
              + NAME);
    }
    return provider.lock(
        new LockConfiguration(
            clock.instant(), NAME, A_STOPPED_RUN_HOLDS_THE_LOCK_AT_MOST, Duration.ZERO));
  }
}
