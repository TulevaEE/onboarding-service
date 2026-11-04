package ee.tuleva.onboarding.investment.transaction.ingest;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile({"production", "staging"})
class SebReconciliationLock {

  static final String NAME = "SebPendingTransactionReconciliationJob";
  private static final Duration A_STOPPED_RUN_HOLDS_THE_LOCK_AT_MOST = Duration.ofMinutes(2);
  private static final Duration EXTENDED_EVERY = A_STOPPED_RUN_HOLDS_THE_LOCK_AT_MOST.dividedBy(2);
  private static final Duration ANOTHER_NODES_FIRING_OF_THE_SAME_SCHEDULE_FINDS_IT_HELD_FOR =
      Duration.ofMinutes(1);

  private final @Nullable ExtensibleLockProvider lockProvider;
  private final Clock clock;
  private final Supplier<ScheduledExecutorService> keepAliveExecutor;

  @Autowired
  SebReconciliationLock(ObjectProvider<LockProvider> lockProvider, Clock clock) {
    this(
        Optional.ofNullable(lockProvider.getIfAvailable()),
        clock,
        SebReconciliationLock::keepAliveThread);
  }

  SebReconciliationLock(
      Optional<LockProvider> lockProvider,
      Clock clock,
      Supplier<ScheduledExecutorService> keepAliveExecutor) {
    this.lockProvider = lockProvider.map(SebReconciliationLock::extensible).orElse(null);
    this.clock = clock;
    this.keepAliveExecutor = keepAliveExecutor;
  }

  static ScheduledExecutorService keepAliveThread() {
    return Executors.newSingleThreadScheduledExecutor(
        Thread.ofPlatform().name("seb-reconciliation-lock").daemon().factory());
  }

  boolean runIfFree(Consumer<BooleanSupplier> task) {
    return runHolding(Duration.ZERO, task);
  }

  boolean runScheduledIfFree(Consumer<BooleanSupplier> task) {
    return runHolding(ANOTHER_NODES_FIRING_OF_THE_SAME_SCHEDULE_FINDS_IT_HELD_FOR, task);
  }

  private boolean runHolding(Duration lockAtLeastFor, Consumer<BooleanSupplier> task) {
    if (lockProvider == null) {
      throw new IllegalStateException(
          "SEB reconciliation refused to run unlocked: no ShedLock LockProvider on this node"
              + " (scheduling.enabled=false), lock="
              + NAME);
    }
    Instant lockedAt = clock.instant();
    Optional<SimpleLock> lock =
        lockProvider.lock(
            new LockConfiguration(
                lockedAt, NAME, A_STOPPED_RUN_HOLDS_THE_LOCK_AT_MOST, lockAtLeastFor));
    if (lock.isEmpty()) {
      return false;
    }
    KeptAliveLock keptAlive = new KeptAliveLock(lock.get(), clock, lockedAt);
    ScheduledExecutorService keepAlive = keepAliveExecutor.get();
    try {
      keepAlive.scheduleAtFixedRate(
          keptAlive::extend, EXTENDED_EVERY.toMillis(), EXTENDED_EVERY.toMillis(), MILLISECONDS);
      task.accept(keptAlive::stillHeld);
      return true;
    } finally {
      keepAlive.shutdownNow();
      keptAlive.releaseIfStillHeld();
    }
  }

  private static ExtensibleLockProvider extensible(LockProvider lockProvider) {
    if (lockProvider instanceof ExtensibleLockProvider extensibleLockProvider) {
      return extensibleLockProvider;
    }
    throw new IllegalStateException(
        "SEB reconciliation needs a lock provider that can keep its lock alive: provider="
            + lockProvider.getClass().getName());
  }

  private static final class KeptAliveLock {

    private final Clock clock;
    private SimpleLock lock;
    private Instant heldAtLeastUntil;
    private boolean lost;

    private KeptAliveLock(SimpleLock lock, Clock clock, Instant lockedAt) {
      this.lock = lock;
      this.clock = clock;
      this.heldAtLeastUntil = lockedAt.plus(A_STOPPED_RUN_HOLDS_THE_LOCK_AT_MOST);
    }

    private synchronized void extend() {
      if (lost) {
        return;
      }
      Instant extendingAt = clock.instant();
      try {
        Optional<SimpleLock> extended =
            lock.extend(A_STOPPED_RUN_HOLDS_THE_LOCK_AT_MOST, Duration.ZERO);
        if (extended.isEmpty()) {
          log.error("SEB reconciliation lost its lock, the extension was refused: lock={}", NAME);
          lost = true;
          return;
        }
        lock = extended.get();
        heldAtLeastUntil = extendingAt.plus(A_STOPPED_RUN_HOLDS_THE_LOCK_AT_MOST);
      } catch (RuntimeException e) {
        log.error("SEB reconciliation could not extend its lock: lock={}", NAME, e);
        lost = true;
      }
    }

    private synchronized boolean stillHeld() {
      return !lost && clock.instant().isBefore(heldAtLeastUntil);
    }

    private synchronized void releaseIfStillHeld() {
      if (stillHeld()) {
        lock.unlock();
      }
    }
  }
}
