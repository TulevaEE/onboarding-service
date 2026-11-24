package ee.tuleva.onboarding.investment.fees.instrument;

import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.investment.event.JobBusyException;
import ee.tuleva.onboarding.investment.event.RunInstrumentFeeAccrualBackfillRequested;
import ee.tuleva.onboarding.investment.event.RunInstrumentFeeAccrualRequested;
import java.time.Clock;
import java.time.Instant;
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

class InstrumentFeeAccrualJobTest {

  private final InstrumentFeeAccrualService service =
      Mockito.mock(InstrumentFeeAccrualService.class);
  private final RecordingLockProvider lockProvider = new RecordingLockProvider();

  @Test
  void theScheduledRunRecalculatesTheRecentDaysUnderItsLock() {
    try (var context = lockedContext()) {
      context.getBean(InstrumentFeeAccrualJob.class).accrueTheRecentDays();
    }

    assertThat(lockProvider.lockNames).containsExactly("InstrumentFeeAccrualJob");
    Mockito.verify(service).accrueTheRecentDays();
  }

  @Test
  void anAdHocRequestRecalculatesTheRecentDaysUnderTheSameLock() {
    try (var context = lockedContext()) {
      context.publishEvent(new RunInstrumentFeeAccrualRequested());
    }

    assertThat(lockProvider.lockNames).containsExactly("InstrumentFeeAccrualJob");
    Mockito.verify(service).accrueTheRecentDays();
  }

  @Test
  void aBackfillRequestAccruesEveryDayFromItsFirstDayUnderTheSameLock() {
    var firstDay = LocalDate.of(2026, 3, 1);

    try (var context = lockedContext()) {
      context.publishEvent(new RunInstrumentFeeAccrualBackfillRequested(firstDay));
    }

    assertThat(lockProvider.lockNames).containsExactly("InstrumentFeeAccrualJob");
    Mockito.verify(service).accrueSince(firstDay);
  }

  @Test
  void aBackfillAskedForWhileTheLockIsHeldIsBusySoItsTriggerWaitsForTheNextPoll() {
    lockProvider.available = false;

    try (var context = lockedContext()) {
      assertThatThrownBy(
              () ->
                  context.publishEvent(
                      new RunInstrumentFeeAccrualBackfillRequested(LocalDate.of(2026, 3, 1))))
          .isInstanceOf(JobBusyException.class);
    }

    Mockito.verifyNoInteractions(service);
  }

  @Test
  void aRecalculationAskedForWhileTheLockIsHeldIsBusyToo() {
    lockProvider.available = false;

    try (var context = lockedContext()) {
      assertThatThrownBy(() -> context.publishEvent(new RunInstrumentFeeAccrualRequested()))
          .isInstanceOf(JobBusyException.class);
    }

    Mockito.verifyNoInteractions(service);
  }

  private AnnotationConfigApplicationContext lockedContext() {
    var context = new AnnotationConfigApplicationContext();
    context.getEnvironment().setActiveProfiles("production");
    context.registerBean(LockProvider.class, () -> lockProvider);
    context.registerBean(
        Clock.class, () -> Clock.fixed(Instant.parse("2026-11-23T18:00:00Z"), UTC));
    context.registerBean(InstrumentFeeAccrualService.class, () -> service);
    context.register(SchedulerLockOnly.class, InstrumentFeeAccrualJob.class);
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
