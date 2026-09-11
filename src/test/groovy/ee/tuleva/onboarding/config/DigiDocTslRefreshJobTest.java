package ee.tuleva.onboarding.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mockStatic;

import io.sentry.IScope;
import io.sentry.ScopeCallback;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import org.digidoc4j.Configuration;
import org.digidoc4j.TSLCertificateSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DigiDocTslRefreshJobTest {

  @Mock private Configuration digiDocConfiguration;
  @Mock private TSLCertificateSource tsl;
  @InjectMocks private DigiDocTslRefreshJob digiDocTslRefreshJob;

  @BeforeEach
  void setUp() {
    digiDocTslRefreshJob.backoffBaseSeconds = 0;
  }

  @Test
  void retriesOnTransientFailure() {
    given(digiDocConfiguration.getTSL()).willReturn(tsl);
    willThrow(new RuntimeException("LoTL download failed")).willDoNothing().given(tsl).refresh();

    digiDocTslRefreshJob.refreshTslOnStartup();

    verify(tsl, times(2)).refresh();
  }

  @Test
  void succeedsOnFirstAttempt() {
    given(digiDocConfiguration.getTSL()).willReturn(tsl);

    digiDocTslRefreshJob.refreshTslOnStartup();

    verify(tsl, times(1)).refresh();
  }

  @Test
  void retriesAllAttemptsBeforeGivingUp() {
    given(digiDocConfiguration.getTSL()).willReturn(tsl);
    willThrow(new RuntimeException("LoTL download failed")).given(tsl).refresh();

    digiDocTslRefreshJob.refreshTslOnStartup();

    verify(tsl, times(8)).refresh();
  }

  @Test
  void scheduledRefreshCallsRefreshWithRetry() {
    given(digiDocConfiguration.getTSL()).willReturn(tsl);

    digiDocTslRefreshJob.scheduledRefreshTsl();

    verify(tsl, times(1)).refresh();
  }

  @Test
  void reportsToSentryWithFatalLevelAfterExhaustingAllRetries() {
    given(digiDocConfiguration.getTSL()).willReturn(tsl);
    willThrow(new RuntimeException("LoTL download failed")).given(tsl).refresh();
    IScope scope = mock(IScope.class);

    try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class)) {
      sentry
          .when(() -> Sentry.withScope(any(ScopeCallback.class)))
          .thenAnswer(
              invocation -> {
                ScopeCallback callback = invocation.getArgument(0);
                callback.run(scope);
                return null;
              });

      digiDocTslRefreshJob.refreshTslOnStartup();

      verify(scope).setLevel(SentryLevel.FATAL);
      verify(scope).setTag("action", "redeploy");
      verify(scope).setTag("component", "digidoc4j-tsl");
      verify(scope).setFingerprint(java.util.List.of("tsl-refresh-exhausted-retries"));
      verify(scope).setExtra(eq("cause"), any());
      sentry.verify(() -> Sentry.withScope(any(ScopeCallback.class)));
    }
  }

  @Test
  void doesNotReportToSentryWhenAnAttemptEventuallySucceeds() {
    given(digiDocConfiguration.getTSL()).willReturn(tsl);
    willThrow(new RuntimeException("LoTL download failed")).willDoNothing().given(tsl).refresh();

    try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class)) {
      digiDocTslRefreshJob.refreshTslOnStartup();

      sentry.verifyNoInteractions();
    }
  }

  @Test
  @Timeout(15)
  void refreshWithRetryBacksOffExponentiallyBetweenAttempts() {
    digiDocTslRefreshJob.backoffBaseSeconds = 1;
    given(digiDocConfiguration.getTSL()).willReturn(tsl);
    // Fails twice (backoff 1s, then 3s), then succeeds: ~4s total.
    willThrow(new RuntimeException("fail 1"))
        .willThrow(new RuntimeException("fail 2"))
        .willDoNothing()
        .given(tsl)
        .refresh();

    long start = System.nanoTime();
    digiDocTslRefreshJob.refreshTslOnStartup();
    long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

    verify(tsl, times(3)).refresh();
    assertThat(elapsedMillis).isBetween(3_500L, 10_000L);
  }

  @Test
  @Timeout(5)
  void sleepPausesForTheGivenDuration() throws Exception {
    Method sleep = DigiDocTslRefreshJob.class.getDeclaredMethod("sleep", long.class);
    sleep.setAccessible(true);

    long start = System.nanoTime();
    sleep.invoke(digiDocTslRefreshJob, 1L);
    long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

    assertThat(elapsedMillis).isGreaterThanOrEqualTo(900L);
  }

  @Test
  @Timeout(5)
  void sleepPreservesTheInterruptedFlagWhenInterrupted() throws Exception {
    Method sleep = DigiDocTslRefreshJob.class.getDeclaredMethod("sleep", long.class);
    sleep.setAccessible(true);
    AtomicBoolean interruptedAfterSleep = new AtomicBoolean();
    Thread worker =
        new Thread(
            () -> {
              try {
                sleep.invoke(digiDocTslRefreshJob, 10L);
              } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
              }
              interruptedAfterSleep.set(Thread.currentThread().isInterrupted());
            });

    worker.start();
    Thread.sleep(200);
    worker.interrupt();
    worker.join();

    assertThat(interruptedAfterSleep.get()).isTrue();
  }
}
