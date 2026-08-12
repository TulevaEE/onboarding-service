package ee.tuleva.onboarding.investment.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.investment.event.RunRiskIndicatorRequested;
import ee.tuleva.onboarding.investment.risk.RiskIndicatorService.RiskIndicatorRun;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;

class RiskIndicatorJobTest {

  private final RiskIndicatorService service = Mockito.mock(RiskIndicatorService.class);
  private final RiskIndicatorNotifier notifier = Mockito.mock(RiskIndicatorNotifier.class);
  private final RiskIndicatorJob job = new RiskIndicatorJob(service, notifier);
  private final RecordingLockProvider lockProvider = new RecordingLockProvider();

  @Test
  void theDailyRunEvaluatesTheDefaultLookbackAndNotifies() {
    var run = new RiskIndicatorRun(LocalDate.of(2026, 8, 6), List.of(), List.of());
    given(service.evaluateAllFunds(RiskIndicatorService.DEFAULT_LOOKBACK_MONTHS)).willReturn(run);

    job.evaluate();

    Mockito.verify(notifier).notify(run);
  }

  @Test
  void aBackfillRequestEvaluatesTheRequestedLookback() {
    var run = new RiskIndicatorRun(LocalDate.of(2026, 8, 6), List.of(), List.of());
    given(service.evaluateAllFunds(120)).willReturn(run);

    job.onRiskIndicatorRequested(new RunRiskIndicatorRequested(120));

    Mockito.verify(notifier).notify(run);
  }

  @Test
  void anAdHocRequestHoldsTheSameLockAsTheScheduledRun() {
    var run = new RiskIndicatorRun(LocalDate.of(2026, 8, 6), List.of(), List.of());
    given(service.evaluateAllFunds(120)).willReturn(run);

    try (var context = lockedContext()) {
      context.publishEvent(new RunRiskIndicatorRequested(120));
    }

    assertThat(lockProvider.lockNames).containsExactly("RiskIndicatorJob");
    Mockito.verify(notifier).notify(run);
  }

  @Test
  void anAdHocRequestDoesNotRunWhileTheScheduledRunHoldsTheLock() {
    lockProvider.available = false;

    try (var context = lockedContext()) {
      context.publishEvent(new RunRiskIndicatorRequested(120));
    }

    assertThat(lockProvider.lockNames).containsExactly("RiskIndicatorJob");
    Mockito.verifyNoInteractions(service, notifier);
  }

  private AnnotationConfigApplicationContext lockedContext() {
    var context = new AnnotationConfigApplicationContext();
    context.getEnvironment().setActiveProfiles("production");
    context.registerBean(LockProvider.class, () -> lockProvider);
    context.registerBean(RiskIndicatorService.class, () -> service);
    context.registerBean(RiskIndicatorNotifier.class, () -> notifier);
    context.register(SchedulerLockOnly.class, RiskIndicatorJob.class);
    context.refresh();
    return context;
  }

  @Configuration
  @EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
  static class SchedulerLockOnly {}

  private static class RecordingLockProvider implements LockProvider {
    private final List<String> lockNames = new ArrayList<>();
    private boolean available = true;

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
      lockNames.add(lockConfiguration.getName());
      return available ? Optional.of(() -> {}) : Optional.empty();
    }
  }
}
