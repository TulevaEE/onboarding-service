package ee.tuleva.onboarding.investment.transaction.ingest;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.support.KeepAliveLockProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"production", "staging"})
class SebReconciliationLock {

  static final String NAME = "SebPendingTransactionReconciliationJob";
  private static final Duration A_STOPPED_RUN_HOLDS_THE_LOCK_AT_MOST = Duration.ofMinutes(2);

  private final ExtensibleLockProvider lockProvider;
  private final Clock clock;
  private final Supplier<ScheduledExecutorService> keepAliveExecutor;

  @Autowired
  SebReconciliationLock(LockProvider lockProvider, Clock clock) {
    this(
        lockProvider,
        clock,
        () ->
            Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("seb-reconciliation-lock").daemon().factory()));
  }

  SebReconciliationLock(
      LockProvider lockProvider,
      Clock clock,
      Supplier<ScheduledExecutorService> keepAliveExecutor) {
    if (!(lockProvider instanceof ExtensibleLockProvider extensibleLockProvider)) {
      throw new IllegalStateException(
          "SEB reconciliation needs a lock provider that can keep its lock alive: provider="
              + lockProvider.getClass().getName());
    }
    this.lockProvider = extensibleLockProvider;
    this.clock = clock;
    this.keepAliveExecutor = keepAliveExecutor;
  }

  boolean runIfFree(Runnable task) {
    ScheduledExecutorService keepAlive = keepAliveExecutor.get();
    try {
      return new DefaultLockingTaskExecutor(new KeepAliveLockProvider(lockProvider, keepAlive))
          .executeWithLock(
              () -> {
                task.run();
                return NAME;
              },
              new LockConfiguration(
                  clock.instant(), NAME, A_STOPPED_RUN_HOLDS_THE_LOCK_AT_MOST, Duration.ZERO))
          .wasExecuted();
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable e) {
      throw new IllegalStateException("SEB reconciliation lock failed: lock=" + NAME, e);
    } finally {
      keepAlive.shutdownNow();
    }
  }
}
