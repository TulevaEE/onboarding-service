package ee.tuleva.onboarding.investment.kid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.investment.event.RunKidCalculationRequested;
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

class KidCalculationJobTest {

  private final KidCalculationService service = Mockito.mock(KidCalculationService.class);
  private final KidCalculationNotifier notifier = Mockito.mock(KidCalculationNotifier.class);
  private final RecordingLockProvider lockProvider = new RecordingLockProvider();

  @Test
  void theScheduledRunCalculatesTheMissingMonthsUnderItsLockAndReportsWhatItStored() {
    given(service.calculateTheMissingMonths()).willReturn(List.of());

    try (var context = lockedContext()) {
      context.getBean(KidCalculationJob.class).calculateTheMissingMonths();
    }

    assertThat(lockProvider.lockNames).containsExactly("KidCalculationJob");
    Mockito.verify(notifier).notifyRuns(List.of());
  }

  @Test
  void anAdHocRequestHoldsTheSameLockAsTheScheduledRunAndReportsWhatItStored() {
    given(service.calculateTheMissingMonths()).willReturn(List.of());

    try (var context = lockedContext()) {
      context.publishEvent(new RunKidCalculationRequested());
    }

    assertThat(lockProvider.lockNames).containsExactly("KidCalculationJob");
    Mockito.verify(notifier).notifyRuns(List.of());
  }

  @Test
  void anAdHocRequestDoesNotRunWhileTheScheduledRunHoldsTheLock() {
    lockProvider.available = false;

    try (var context = lockedContext()) {
      context.publishEvent(new RunKidCalculationRequested());
    }

    assertThat(lockProvider.lockNames).containsExactly("KidCalculationJob");
    Mockito.verifyNoInteractions(service, notifier);
  }

  private AnnotationConfigApplicationContext lockedContext() {
    var context = new AnnotationConfigApplicationContext();
    context.getEnvironment().setActiveProfiles("production");
    context.registerBean(LockProvider.class, () -> lockProvider);
    context.registerBean(KidCalculationService.class, () -> service);
    context.registerBean(KidCalculationNotifier.class, () -> notifier);
    context.register(SchedulerLockOnly.class, KidCalculationJob.class);
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
