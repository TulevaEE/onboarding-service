package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.investment.JobRunSchedule.KID_CALCULATION_EACH_WORKING_DAY;
import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;

import ee.tuleva.onboarding.investment.event.RunKidCalculationRequested;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Profile({"production", "staging"})
public class KidCalculationJob {

  private static final String LOCK = "KidCalculationJob";

  private final KidCalculationService service;
  private final KidCalculationNotifier notifier;

  @Scheduled(cron = KID_CALCULATION_EACH_WORKING_DAY, zone = TIMEZONE)
  @SchedulerLock(name = LOCK, lockAtMostFor = "PT15M", lockAtLeastFor = "PT1M")
  void calculateTheMissingMonths() {
    notifier.notifyRuns(service.calculateTheMissingMonths());
  }

  @EventListener(RunKidCalculationRequested.class)
  @SchedulerLock(name = LOCK, lockAtMostFor = "PT15M", lockAtLeastFor = "PT0S")
  void onKidCalculationRequested() {
    notifier.notifyRuns(service.calculateTheMissingMonths());
  }
}
