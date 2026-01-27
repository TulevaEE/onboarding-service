package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.investment.JobRunSchedule.*;
import static ee.tuleva.onboarding.time.ClockHolder.clock;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@Profile({"production", "staging"})
public class FeeCalculationScheduledJob {

  private final FeeCalculationService feeCalculationService;

  @Scheduled(cron = FEE_CALCULATION, zone = TIMEZONE)
  @SchedulerLock(name = "FeeCalculationScheduledJob", lockAtMostFor = "30m", lockAtLeastFor = "5m")
  public void calculateDailyFees() {
    LocalDate yesterday = LocalDate.now(clock()).minusDays(1);
    log.info("Starting daily fee calculation: date={}", yesterday);

    try {
      feeCalculationService.calculateDailyFees(yesterday);
      log.info("Completed daily fee calculation: date={}", yesterday);
    } catch (Exception exception) {
      log.error("Failed daily fee calculation: date={}", yesterday, exception);
      throw exception;
    }
  }
}
