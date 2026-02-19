package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile({"production", "staging"})
public class NavCalculationJob {

  private final NavCalculationService navCalculationService;
  private final NavNotifier navNotifier;
  private final PublicHolidays publicHolidays;
  private final Clock clock;

  @Scheduled(cron = "0 30 15 * * MON-FRI", zone = "Europe/Tallinn")
  @SchedulerLock(name = "NavCalculationJob_TKF100", lockAtMostFor = "30m", lockAtLeastFor = "5m")
  public void calculateDailyNav() {
    LocalDate today = LocalDate.now(clock);

    if (!publicHolidays.isWorkingDay(today)) {
      log.info("Skipping NAV calculation on non-working day: date={}", today);
      return;
    }

    LocalDate calculationDate = publicHolidays.previousWorkingDay(today);

    log.info(
        "Starting scheduled NAV calculation: fund={}, calculationDate={}", TKF100, calculationDate);

    try {
      NavCalculationResult result = navCalculationService.calculate(TKF100, calculationDate);
      navNotifier.notify(result);
      log.info(
          "Completed scheduled NAV calculation: fund={}, calculationDate={}",
          TKF100,
          calculationDate);
    } catch (Exception exception) {
      log.error(
          "Failed scheduled NAV calculation: fund={}, calculationDate={}",
          TKF100,
          calculationDate,
          exception);
      throw exception;
    }
  }
}
