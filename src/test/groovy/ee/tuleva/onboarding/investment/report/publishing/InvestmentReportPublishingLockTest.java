package ee.tuleva.onboarding.investment.report.publishing;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.simple.JdbcClient;

@ExtendWith(MockitoExtension.class)
class InvestmentReportPublishingLockTest {

  private static final Instant NOW = Instant.parse("2026-04-10T07:00:00Z");

  @Mock private ExtensibleLockProvider lockProvider;
  @Mock private SimpleLock lock;
  @Mock private JdbcClient jdbcClient;
  private InvestmentReportPublishingLock publishingLock;

  @BeforeEach
  void setUp() {
    publishingLock =
        new InvestmentReportPublishingLock(
            lockProvider, jdbcClient, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void aPublishRunsUnderTheOneLockThatAStoppedPublishHoldsForTwoMinutesAtMostAndReleasesIt() {
    given(lockProvider.lock(any())).willReturn(Optional.of(lock));

    var result = publishingLock.whileNoOtherPublishRuns(() -> "published", refusal -> refusal);

    assertThat(result).isEqualTo("published");
    verify(lockProvider)
        .lock(
            argThat(
                configuration ->
                    configuration.getName().equals("investmentReportPublishing")
                        && configuration
                            .getLockAtMostUntil()
                            .equals(NOW.plus(Duration.ofMinutes(2)))
                        && configuration.getLockAtLeastFor().isZero()));
    verify(lock).unlock();
  }

  @Test
  void theLockIsReleasedWhenAPublishFails() {
    given(lockProvider.lock(any())).willReturn(Optional.of(lock));

    assertThatThrownBy(
            () ->
                publishingLock.whileNoOtherPublishRuns(
                    () -> {
                      throw new IllegalStateException("database down");
                    },
                    refusal -> refusal))
        .isInstanceOf(IllegalStateException.class);
    verify(lock).unlock();
  }

  @Test
  void theLockIsExtendedEveryMinuteWhileThePublishRunsAndTheKeepAliveStopsAfterIt() {
    var keepAliveExecutor = mock(ScheduledExecutorService.class);
    var keepAlive = new AtomicReference<Runnable>();
    willAnswer(
            schedule -> {
              keepAlive.set(schedule.getArgument(0));
              return mock(ScheduledFuture.class);
            })
        .given(keepAliveExecutor)
        .scheduleAtFixedRate(any(), eq(60_000L), eq(60_000L), eq(MILLISECONDS));
    given(lockProvider.lock(any())).willReturn(Optional.of(lock));
    var publishingLock =
        new InvestmentReportPublishingLock(
            lockProvider, jdbcClient, Clock.systemUTC(), () -> keepAliveExecutor);

    publishingLock.whileNoOtherPublishRuns(
        () -> {
          keepAlive.get().run();
          return "published";
        },
        refusal -> refusal);

    verify(lock).extend(Duration.ofMinutes(2), Duration.ZERO);
    verify(lock).unlock();
    verify(keepAliveExecutor).shutdownNow();
  }

  @Test
  void aLockProviderThatCannotKeepTheLockAliveIsRefusedAtStartup() {
    var providerThatCannotExtend = mock(LockProvider.class);
    var clock = Clock.fixed(NOW, ZoneOffset.UTC);

    assertThatThrownBy(
            () -> new InvestmentReportPublishingLock(providerThatCannotExtend, jdbcClient, clock))
        .isInstanceOf(IllegalStateException.class);
  }
}
