package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValueIndexingJob;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
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
  private final NavPublisher navPublisher;
  private final PublicHolidays publicHolidays;
  private final FundValueIndexingJob fundValueIndexingJob;
  private final Clock clock;

  @Scheduled(cron = "0 30 15 * * MON-FRI", zone = "Europe/Tallinn")
  @SchedulerLock(name = "NavCalculationJob_TKF100", lockAtMostFor = "30m", lockAtLeastFor = "5m")
  public void calculateDailyNav() {
    calculateForFund(TKF100);
  }

  @Scheduled(cron = "0 0 11 * * MON-FRI", zone = "Europe/Tallinn")
  @SchedulerLock(name = "NavCalculationJob_Pillar2", lockAtMostFor = "30m", lockAtLeastFor = "5m")
  public void calculatePillar2Nav() {
    calculateForFunds(TulevaFund.getPillar2Funds());
  }

  @Scheduled(cron = "0 0 15 * * MON-FRI", zone = "Europe/Tallinn")
  @SchedulerLock(name = "NavCalculationJob_Pillar3", lockAtMostFor = "30m", lockAtLeastFor = "5m")
  public void calculatePillar3Nav() {
    calculateForFunds(TulevaFund.getPillar3Funds());
  }

  private void calculateForFunds(List<TulevaFund> funds) {
    LocalDate today = LocalDate.now(clock);

    if (!publicHolidays.isWorkingDay(today)) {
      log.info("Skipping NAV calculation on non-working day: date={}", today);
      return;
    }

    fundValueIndexingJob.refreshAll();

    funds.stream()
        .filter(TulevaFund::hasNavCalculation)
        .forEach(
            fund -> {
              try {
                refreshAndCalculate(fund, today);
              } catch (Exception e) {
                log.error(
                    "Failed NAV calculation, continuing with next fund: fund={}, date={}",
                    fund,
                    today,
                    e);
              }
            });
  }

  private void calculateForFund(TulevaFund fund) {
    LocalDate today = LocalDate.now(clock);

    if (!publicHolidays.isWorkingDay(today)) {
      log.info("Skipping NAV calculation on non-working day: date={}", today);
      return;
    }

    log.info("Starting scheduled NAV calculation: fund={}, calculationDate={}", fund, today);

    fundValueIndexingJob.refreshAll();

    refreshAndCalculate(fund, today);
  }

  private void refreshAndCalculate(TulevaFund fund, LocalDate today) {
    try {
      NavCalculationResult result = navCalculationService.calculate(fund, today);
      navPublisher.publish(result);
      log.info("Completed scheduled NAV calculation: fund={}, calculationDate={}", fund, today);
    } catch (Exception exception) {
      log.error(
          "Failed scheduled NAV calculation: fund={}, calculationDate={}", fund, today, exception);
      throw exception;
    }
  }
}
