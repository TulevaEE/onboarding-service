package ee.tuleva.onboarding.investment.transaction.ingest;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"production", "staging"})
@RequiredArgsConstructor
class SebReconciliationLock {

  static final String NAME = "SebPendingTransactionReconciliationJob";

  private final LockProvider lockProvider;
  private final Clock clock;

  boolean runIfFree(Duration lockAtMostFor, Runnable task) {
    Optional<SimpleLock> lock =
        lockProvider.lock(
            new LockConfiguration(clock.instant(), NAME, lockAtMostFor, Duration.ZERO));
    lock.ifPresent(held -> runThenUnlock(held, task));
    return lock.isPresent();
  }

  private static void runThenUnlock(SimpleLock held, Runnable task) {
    try {
      task.run();
    } finally {
      held.unlock();
    }
  }
}
