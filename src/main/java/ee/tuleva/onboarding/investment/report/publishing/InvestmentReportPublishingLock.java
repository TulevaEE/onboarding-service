package ee.tuleva.onboarding.investment.report.publishing;

import static java.time.ZoneOffset.UTC;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.support.KeepAliveLockProvider;
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

  <T> T whileNoOtherPublishRuns(Supplier<T> publish, Function<String, T> whenRefused) {
    var keepAlive = keepAliveExecutor.get();
    try {
      var lock =
          new KeepAliveLockProvider(lockProvider, keepAlive)
              .lock(
                  new LockConfiguration(
                      clock.instant(),
                      ONE_PUBLISH_AT_A_TIME_LOCK,
                      A_STOPPED_PUBLISH_HOLDS_THE_LOCK_AT_MOST,
                      Duration.ZERO));
      if (lock.isEmpty()) {
        return whenRefused.apply(whyRefused());
      }
      try {
        return publish.get();
      } finally {
        lock.get().unlock();
      }
    } finally {
      keepAlive.shutdownNow();
    }
  }

  private String whyRefused() {
    try {
      var heldUntil =
          jdbcClient
              .sql("SELECT lock_until FROM shedlock WHERE name = :name")
              .param("name", ONE_PUBLISH_AT_A_TIME_LOCK)
              .query(LocalDateTime.class)
              .single()
              .toInstant(UTC);
      return heldUntil.isAfter(clock.instant())
          ? ("Another investment report publish holds the lock and extends it while it runs; one"
                  + " that stopped loses it by heldUntil: heldUntil=%s")
              .formatted(heldUntil)
          : "Another investment report publish released the lock just now, try again: heldUntil=%s"
              .formatted(heldUntil);
    } catch (RuntimeException e) {
      log.error("Could not read until when the investment report publishing lock is held", e);
      return "Another investment report publish holds the lock, and when it lapses could not be"
          + " read: error="
          + e.getClass().getSimpleName();
    }
  }
}
