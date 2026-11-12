package ee.tuleva.onboarding.investment.report.publishing;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;

@ExtendWith(MockitoExtension.class)
class InvestmentReportPublishingLockTest {

  private static final Instant NOW = Instant.parse("2026-04-10T07:00:00Z");
  private static final Duration LEASE = Duration.ofMinutes(2);

  @Mock private ExtensibleLockProvider lockProvider;
  @Mock private SimpleLock lock;
  @Mock private SimpleLock extendedLock;
  @Mock private JdbcClient jdbcClient;
  @Mock private ScheduledExecutorService keepAliveExecutor;
  private final SettableClock clock = new SettableClock(NOW);
  private final AtomicReference<Runnable> keepAlive = new AtomicReference<>();
  private InvestmentReportPublishingLock publishingLock;

  @BeforeEach
  void setUp() {
    lenient()
        .doAnswer(
            schedule -> {
              keepAlive.set(schedule.getArgument(0));
              return mock(ScheduledFuture.class);
            })
        .when(keepAliveExecutor)
        .scheduleAtFixedRate(any(), eq(60_000L), eq(60_000L), eq(MILLISECONDS));
    publishingLock =
        new InvestmentReportPublishingLock(
            lockProvider, jdbcClient, clock, () -> keepAliveExecutor);
  }

  @Test
  void aPublishRunsUnderTheOneLockThatAStoppedPublishHoldsForTwoMinutesAtMostAndReleasesIt() {
    given(lockProvider.lock(any())).willReturn(Optional.of(lock));

    var heldWhilePublishing =
        publishingLock.whileNoOtherPublishRuns(
            lockStillHeld -> lockStillHeld.getAsBoolean(), refusal -> false);

    assertThat(heldWhilePublishing).isTrue();
    verify(lockProvider)
        .lock(
            argThat(
                configuration ->
                    configuration.getName().equals("investmentReportPublishing")
                        && configuration.getLockAtMostUntil().equals(NOW.plus(LEASE))
                        && configuration.getLockAtLeastFor().isZero()));
    verify(lock).unlock();
    verify(keepAliveExecutor).shutdownNow();
  }

  @Test
  void theLockIsExtendedEveryMinuteAndTheExtendedLockIsTheOneReleased() {
    given(lockProvider.lock(any())).willReturn(Optional.of(lock));
    given(lock.extend(LEASE, Duration.ZERO)).willReturn(Optional.of(extendedLock));

    var heldAfterTheLeaseItWasTakenFor =
        publishingLock.whileNoOtherPublishRuns(
            lockStillHeld -> {
              clock.advance(Duration.ofMinutes(1));
              keepAlive.get().run();
              clock.advance(Duration.ofMinutes(1).plusSeconds(30));
              return lockStillHeld.getAsBoolean();
            },
            refusal -> false);

    assertThat(heldAfterTheLeaseItWasTakenFor).isTrue();
    verify(extendedLock).unlock();
    verify(lock, never()).unlock();
  }

  @Test
  void aLockWhoseExtensionFailsIsNoLongerHeldAndIsNotReleased() {
    given(lockProvider.lock(any())).willReturn(Optional.of(lock));
    given(lock.extend(LEASE, Duration.ZERO))
        .willThrow(new DataAccessResourceFailureException("database unreachable"));

    var heldAfterTheFailedExtension =
        publishingLock.whileNoOtherPublishRuns(
            lockStillHeld -> {
              keepAlive.get().run();
              return lockStillHeld.getAsBoolean();
            },
            refusal -> true);

    assertThat(heldAfterTheFailedExtension).isFalse();
    verify(lock, never()).unlock();
  }

  @Test
  void aLockWhoseExtensionIsRefusedIsNoLongerHeldAndIsNotReleased() {
    given(lockProvider.lock(any())).willReturn(Optional.of(lock));
    given(lock.extend(LEASE, Duration.ZERO)).willReturn(Optional.empty());

    var heldAfterTheRefusedExtension =
        publishingLock.whileNoOtherPublishRuns(
            lockStillHeld -> {
              keepAlive.get().run();
              return lockStillHeld.getAsBoolean();
            },
            refusal -> true);

    assertThat(heldAfterTheRefusedExtension).isFalse();
    verify(lock, never()).unlock();
  }

  @Test
  void aLockNotExtendedForItsWholeLeaseIsNoLongerHeldAndIsNotReleased() {
    given(lockProvider.lock(any())).willReturn(Optional.of(lock));

    var heldAfterTheLease =
        publishingLock.whileNoOtherPublishRuns(
            lockStillHeld -> {
              clock.advance(LEASE);
              return lockStillHeld.getAsBoolean();
            },
            refusal -> true);

    assertThat(heldAfterTheLease).isFalse();
    verify(lock, never()).unlock();
  }

  @Test
  void theLockIsReleasedWhenAPublishFails() {
    given(lockProvider.lock(any())).willReturn(Optional.of(lock));

    assertThatThrownBy(
            () ->
                publishingLock.whileNoOtherPublishRuns(
                    lockStillHeld -> {
                      throw new IllegalStateException("database down");
                    },
                    refusal -> refusal))
        .isInstanceOf(IllegalStateException.class);
    verify(lock).unlock();
    verify(keepAliveExecutor).shutdownNow();
  }

  @Test
  void aLockProviderThatCannotKeepTheLockAliveIsRefusedAtStartup() {
    var providerThatCannotExtend = mock(LockProvider.class);
    var fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);

    assertThatThrownBy(
            () ->
                new InvestmentReportPublishingLock(
                    providerThatCannotExtend, jdbcClient, fixedClock))
        .isInstanceOf(IllegalStateException.class);
  }

  private static final class SettableClock extends Clock {

    private Instant now;

    private SettableClock(Instant now) {
      this.now = now;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
