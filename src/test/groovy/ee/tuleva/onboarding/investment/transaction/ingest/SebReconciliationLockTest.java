package ee.tuleva.onboarding.investment.transaction.ingest;

import static java.time.temporal.ChronoUnit.MINUTES;
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

import ee.tuleva.onboarding.time.MutableClock;
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
  @Mock private SimpleLock extendedLock;

  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final ScheduledExecutorService keepAliveExecutor = mock(ScheduledExecutorService.class);
  private final AtomicReference<Runnable> keepAlive = new AtomicReference<>();

  @Test
  void aRunHoldsTheLockThatAStoppedRunHoldsForTwoMinutesAtMostAndReleasesIt() {
    given(lockProvider.lock(any())).willReturn(Optional.of(lock));
    List<Boolean> heldWhileRunning = new ArrayList<>();

    boolean ran =
        lockOver(lockProvider, clock)
            .runIfFree(stillHeld -> heldWhileRunning.add(stillHeld.getAsBoolean()));

    assertThat(ran).isTrue();
    assertThat(heldWhileRunning).containsExactly(true);
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
    verify(keepAliveExecutor).shutdownNow();
  }

  @Test
  void aRunThatFindsTheLockHeldDoesNotRunAndSaysSo() {
    given(lockProvider.lock(any())).willReturn(Optional.empty());
    List<String> ran = new ArrayList<>();

    boolean wasRun = lockOver(lockProvider, clock).runIfFree(stillHeld -> ran.add("run"));

    assertThat(wasRun).isFalse();
    assertThat(ran).isEmpty();
  }

  @Test
  void theLockIsReleasedWhenTheRunFails() {
    given(lockProvider.lock(any())).willReturn(Optional.of(lock));
    SebReconciliationLock reconciliationLock = lockOver(lockProvider, clock);

    assertThatThrownBy(
            () ->
                reconciliationLock.runIfFree(
                    stillHeld -> {
                      throw new IllegalStateException("database down");
                    }))
        .isInstanceOf(IllegalStateException.class);
    verify(lock).unlock();
  }

  @Test
  void theLockIsExtendedEveryMinuteWhileTheRunGoesOn() {
    givenTheKeepAliveIsScheduledEveryMinute();
    given(lockProvider.lock(any())).willReturn(Optional.of(lock));
    given(lock.extend(Duration.ofMinutes(2), Duration.ZERO)).willReturn(Optional.of(extendedLock));
    List<Boolean> heldAfterExtending = new ArrayList<>();

    lockOver(lockProvider, clock)
        .runIfFree(
            stillHeld -> {
              keepAlive.get().run();
              heldAfterExtending.add(stillHeld.getAsBoolean());
            });

    assertThat(heldAfterExtending).containsExactly(true);
    verify(extendedLock).unlock();
  }

  @Test
  void aRefusedExtensionTellsTheRunItLostTheLockAndLeavesTheLockToItsNewHolder() {
    givenTheKeepAliveIsScheduledEveryMinute();
    given(lockProvider.lock(any())).willReturn(Optional.of(lock));
    given(lock.extend(Duration.ofMinutes(2), Duration.ZERO)).willReturn(Optional.empty());
    List<Boolean> heldAfterExtending = new ArrayList<>();

    lockOver(lockProvider, clock)
        .runIfFree(
            stillHeld -> {
              keepAlive.get().run();
              heldAfterExtending.add(stillHeld.getAsBoolean());
            });

    assertThat(heldAfterExtending).containsExactly(false);
    verify(lock, never()).unlock();
  }

  @Test
  void aFailingExtensionTellsTheRunItLostTheLock() {
    givenTheKeepAliveIsScheduledEveryMinute();
    given(lockProvider.lock(any())).willReturn(Optional.of(lock));
    given(lock.extend(Duration.ofMinutes(2), Duration.ZERO))
        .willThrow(new IllegalStateException("database down"));
    List<Boolean> heldAfterExtending = new ArrayList<>();

    lockOver(lockProvider, clock)
        .runIfFree(
            stillHeld -> {
              keepAlive.get().run();
              heldAfterExtending.add(stillHeld.getAsBoolean());
            });

    assertThat(heldAfterExtending).containsExactly(false);
  }

  @Test
  void aLockThatWasNotExtendedForTwoMinutesCountsAsLost() {
    MutableClock running = new MutableClock(NOW);
    given(lockProvider.lock(any())).willReturn(Optional.of(lock));
    List<Boolean> heldTwoMinutesIn = new ArrayList<>();

    lockOver(lockProvider, running)
        .runIfFree(
            stillHeld -> {
              running.tick(2, MINUTES);
              heldTwoMinutesIn.add(stillHeld.getAsBoolean());
            });

    assertThat(heldTwoMinutesIn).containsExactly(false);
  }

  @Test
  void aNodeWithoutALockProviderRefusesToRunInsteadOfRunningUnlocked() {
    SebReconciliationLock withoutProvider =
        new SebReconciliationLock(Optional.empty(), clock, () -> keepAliveExecutor);
    List<String> ran = new ArrayList<>();

    assertThatThrownBy(() -> withoutProvider.runIfFree(stillHeld -> ran.add("run")))
        .isInstanceOf(IllegalStateException.class);
    assertThat(ran).isEmpty();
  }

  @Test
  void aLockProviderThatCannotKeepTheLockAliveIsRefusedAtStartup() {
    LockProvider providerThatCannotExtend = mock(LockProvider.class);

    assertThatThrownBy(
            () ->
                new SebReconciliationLock(
                    Optional.of(providerThatCannotExtend), clock, () -> keepAliveExecutor))
        .isInstanceOf(IllegalStateException.class);
  }

  private SebReconciliationLock lockOver(LockProvider provider, Clock clock) {
    return new SebReconciliationLock(Optional.of(provider), clock, () -> keepAliveExecutor);
  }

  private void givenTheKeepAliveIsScheduledEveryMinute() {
    willAnswer(
            schedule -> {
              keepAlive.set(schedule.getArgument(0));
              return mock(ScheduledFuture.class);
            })
        .given(keepAliveExecutor)
        .scheduleAtFixedRate(any(), eq(60_000L), eq(60_000L), eq(MILLISECONDS));
  }
}
