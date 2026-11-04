package ee.tuleva.onboarding.investment.transaction.ingest;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SebReconciliationLockTest {

  private static final Instant NOW = Instant.parse("2026-05-17T19:00:00Z");

  @Mock private ExtensibleLockProvider lockProvider;
  @Mock private SimpleLock lock;

  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  @Test
  void aRunHoldsTheLockThatAStoppedRunHoldsForTwoMinutesAtMostAndReleasesIt() {
    given(lockProvider.lock(any())).willReturn(Optional.of(lock));
    List<String> ran = new ArrayList<>();

    boolean wasRun = new SebReconciliationLock(lockProvider, clock).runIfFree(() -> ran.add("run"));

    assertThat(wasRun).isTrue();
    assertThat(ran).containsExactly("run");
    verify(lockProvider)
        .lock(
            argThat(
                configuration ->
                    configuration.getName().equals("SebPendingTransactionReconciliationJob")
                        && configuration
                            .getLockAtMostUntil()
                            .equals(NOW.plus(Duration.ofMinutes(2)))
                        && configuration.getLockAtLeastFor().isZero()));
    verify(lock).unlock();
  }

  @Test
  void aRunThatFindsTheLockHeldDoesNotRunAndSaysSo() {
    given(lockProvider.lock(any())).willReturn(Optional.empty());
    List<String> ran = new ArrayList<>();

    boolean wasRun = new SebReconciliationLock(lockProvider, clock).runIfFree(() -> ran.add("run"));

    assertThat(wasRun).isFalse();
    assertThat(ran).isEmpty();
  }

  @Test
  void theLockIsReleasedWhenTheRunFails() {
    given(lockProvider.lock(any())).willReturn(Optional.of(lock));
    SebReconciliationLock reconciliationLock = new SebReconciliationLock(lockProvider, clock);

    assertThatThrownBy(
            () ->
                reconciliationLock.runIfFree(
                    () -> {
                      throw new IllegalStateException("database down");
                    }))
        .isInstanceOf(IllegalStateException.class);
    verify(lock).unlock();
  }

  @Test
  void theLockIsExtendedEveryMinuteWhileTheRunGoesOnAndTheKeepAliveStopsAfterIt() {
    ScheduledExecutorService keepAliveExecutor = mock(ScheduledExecutorService.class);
    AtomicReference<Runnable> keepAlive = new AtomicReference<>();
    willAnswer(
            schedule -> {
              keepAlive.set(schedule.getArgument(0));
              return mock(ScheduledFuture.class);
            })
        .given(keepAliveExecutor)
        .scheduleAtFixedRate(any(), eq(60_000L), eq(60_000L), eq(MILLISECONDS));
    given(lockProvider.lock(any())).willReturn(Optional.of(lock));
    SebReconciliationLock reconciliationLock =
        new SebReconciliationLock(lockProvider, Clock.systemUTC(), () -> keepAliveExecutor);

    reconciliationLock.runIfFree(() -> keepAlive.get().run());

    verify(lock).extend(Duration.ofMinutes(2), Duration.ZERO);
    verify(lock).unlock();
    verify(keepAliveExecutor).shutdownNow();
  }

  @Test
  void aSkippedRunStopsItsKeepAliveToo() {
    ScheduledExecutorService keepAliveExecutor = mock(ScheduledExecutorService.class);
    given(lockProvider.lock(any())).willReturn(Optional.empty());

    new SebReconciliationLock(lockProvider, clock, () -> keepAliveExecutor).runIfFree(() -> {});

    verify(keepAliveExecutor).shutdownNow();
    verify(lock, never()).unlock();
  }

  @Test
  void aLockProviderThatCannotKeepTheLockAliveIsRefusedAtStartup() {
    LockProvider providerThatCannotExtend = mock(LockProvider.class);

    assertThatThrownBy(() -> new SebReconciliationLock(providerThatCannotExtend, clock))
        .isInstanceOf(IllegalStateException.class);
  }
}
