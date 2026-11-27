package ee.tuleva.onboarding.investment.fees.instrument;

import static ee.tuleva.onboarding.investment.JobRunSchedule.INSTRUMENT_FEE_ACCRUAL;
import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;

import ee.tuleva.onboarding.investment.event.JobBusyException;
import ee.tuleva.onboarding.investment.event.RunInstrumentFeeAccrualBackfillRequested;
import ee.tuleva.onboarding.investment.event.RunInstrumentFeeAccrualRequested;
import java.time.Clock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Profile({"production", "staging"})
public class InstrumentFeeAccrualJob {

  private static final String LOCK = "InstrumentFeeAccrualJob";
  private static final String LOCK_AT_MOST_FOR = "PT2H";

  private final InstrumentFeeAccrualService service;
  private final LockProvider lockProvider;
  private final Clock clock;

  @Scheduled(cron = INSTRUMENT_FEE_ACCRUAL, zone = TIMEZONE)
  @SchedulerLock(name = LOCK, lockAtMostFor = LOCK_AT_MOST_FOR, lockAtLeastFor = "PT1M")
  void accrueTheRecentDays() {
    service.accrueTheRecentDays();
  }

  @EventListener(RunInstrumentFeeAccrualRequested.class)
  void onAccrualRequested() {
    underTheLock(service::accrueTheRecentDays);
  }

  @EventListener
  void onBackfillRequested(RunInstrumentFeeAccrualBackfillRequested request) {
    underTheLock(() -> service.accrueSince(request.firstDay()));
  }

  private void underTheLock(Runnable run) {
    var lock =
        lockProvider
            .lock(
                new LockConfiguration(
                    clock.instant(), LOCK, Duration.parse(LOCK_AT_MOST_FOR), Duration.ZERO))
            .orElseThrow(
                () ->
                    new JobBusyException(
                        "Instrument fee accrual already running: lockName=" + LOCK));
    try {
      run.run();
    } finally {
      lock.unlock();
    }
  }
}
