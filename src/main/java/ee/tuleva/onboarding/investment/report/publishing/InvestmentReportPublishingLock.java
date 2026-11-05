package ee.tuleva.onboarding.investment.report.publishing;

import static java.time.ZoneOffset.UTC;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "investment-report-publishing.enabled", havingValue = "true")
class InvestmentReportPublishingLock {

  static final String ONE_PUBLISH_AT_A_TIME_LOCK = "investmentReportPublishing";
  private static final Duration A_STOPPED_PUBLISH_HOLDS_THE_LOCK_AT_MOST = Duration.ofMinutes(2);
  private static final Duration EXTENDED_EVERY =
      A_STOPPED_PUBLISH_HOLDS_THE_LOCK_AT_MOST.dividedBy(2);

  private final ExtensibleLockProvider lockProvider;
  private final JdbcClient jdbcClient;
  private final Clock clock;
  private final Supplier<ScheduledExecutorService> keepAliveExecutor;

  @Autowired
  InvestmentReportPublishingLock(LockProvider lockProvider, JdbcClient jdbcClient, Clock clock) {
    this(
        lockProvider,
        jdbcClient,
        clock,
        () ->
            Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("investment-report-publishing-lock").daemon().factory()));
  }

  InvestmentReportPublishingLock(
      LockProvider lockProvider,
      JdbcClient jdbcClient,
      Clock clock,
      Supplier<ScheduledExecutorService> keepAliveExecutor) {
    if (!(lockProvider instanceof ExtensibleLockProvider extensibleLockProvider)) {
      throw new IllegalStateException(
          "Investment report publishing needs a lock provider that can keep its lock alive:"
              + " provider="
              + lockProvider.getClass().getName());
    }
    this.lockProvider = extensibleLockProvider;
    this.jdbcClient = jdbcClient;
    this.clock = clock;
    this.keepAliveExecutor = keepAliveExecutor;
  }

  <T> T whileNoOtherPublishRuns(
      Function<BooleanSupplier, T> publish, Function<String, T> whenRefused) {
    var lockedAt = clock.instant();
    var lock =
        lockProvider.lock(
            new LockConfiguration(
                lockedAt,
                ONE_PUBLISH_AT_A_TIME_LOCK,
                A_STOPPED_PUBLISH_HOLDS_THE_LOCK_AT_MOST,
                Duration.ZERO));
    if (lock.isEmpty()) {
      return whenRefused.apply(whyRefused());
    }
    var keptAlive = new KeptAliveLock(lock.get(), clock, lockedAt);
    var keepAlive = keepAliveExecutor.get();
    try {
      keepAlive.scheduleAtFixedRate(
          keptAlive::extend, EXTENDED_EVERY.toMillis(), EXTENDED_EVERY.toMillis(), MILLISECONDS);
      return publish.apply(keptAlive::stillHeld);
    } finally {
      keepAlive.shutdownNow();
      keptAlive.releaseIfStillHeld();
    }
  }

  private String whyRefused() {
    try {
      return jdbcClient
          .sql(
              "SELECT lock_until, CURRENT_TIMESTAMP AS database_now FROM shedlock"
                  + " WHERE name = :name")
          .param("name", ONE_PUBLISH_AT_A_TIME_LOCK)
          .query(
              (row, rowNumber) ->
                  describeHeld(
                      row.getObject("lock_until", LocalDateTime.class).toInstant(UTC),
                      row.getObject("database_now", OffsetDateTime.class).toInstant()))
          .single();
    } catch (RuntimeException e) {
      log.error("Could not read until when the investment report publishing lock is held", e);
      return "Another investment report publish holds the lock, and when it lapses could not be"
          + " read: error="
          + e.getClass().getSimpleName();
    }
  }

  private static String describeHeld(Instant heldUntil, Instant databaseNow) {
    return heldUntil.isAfter(databaseNow)
        ? ("Another investment report publish holds the lock and extends it while it runs; one"
                + " that stopped loses it by heldUntil: heldUntil=%s")
            .formatted(heldUntil)
        : "Another investment report publish released the lock just now, try again: heldUntil=%s"
            .formatted(heldUntil);
  }

  private static final class KeptAliveLock {

    private final Clock clock;
    private SimpleLock lock;
    private Instant heldAtLeastUntil;
    private boolean lost;

    private KeptAliveLock(SimpleLock lock, Clock clock, Instant lockedAt) {
      this.lock = lock;
      this.clock = clock;
      this.heldAtLeastUntil = lockedAt.plus(A_STOPPED_PUBLISH_HOLDS_THE_LOCK_AT_MOST);
    }

    private synchronized void extend() {
      if (lost) {
        return;
      }
      var extendingAt = clock.instant();
      try {
        var extended = lock.extend(A_STOPPED_PUBLISH_HOLDS_THE_LOCK_AT_MOST, Duration.ZERO);
        if (extended.isEmpty()) {
          log.error(
              "Investment report publishing lost its lock: lock={}", ONE_PUBLISH_AT_A_TIME_LOCK);
          lost = true;
          return;
        }
        lock = extended.get();
        heldAtLeastUntil = extendingAt.plus(A_STOPPED_PUBLISH_HOLDS_THE_LOCK_AT_MOST);
      } catch (RuntimeException e) {
        log.error(
            "Investment report publishing could not extend its lock: lock={}",
            ONE_PUBLISH_AT_A_TIME_LOCK,
            e);
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
