package ee.tuleva.onboarding.investment.calculation;

import static ee.tuleva.onboarding.investment.calculation.TulevaFund.getPillar2Funds;
import static ee.tuleva.onboarding.investment.calculation.TulevaFund.getPillar3Funds;
import static ee.tuleva.onboarding.investment.calculation.ValidationStatus.EODHD_MISSING;
import static ee.tuleva.onboarding.investment.calculation.ValidationStatus.NO_PRICE_DATA;
import static ee.tuleva.onboarding.investment.calculation.ValidationStatus.PRICE_DISCREPANCY;
import static ee.tuleva.onboarding.investment.calculation.ValidationStatus.YAHOO_MISSING;
import static ee.tuleva.onboarding.time.ClockHolder.clock;

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
public class PositionCalculationJob {

  private final PositionCalculationService calculationService;
  private final PositionCalculationPersistenceService persistenceService;
  private final PositionCalculationNotifier notifier;

  @Scheduled(cron = "0 30 11 * * *", zone = "Europe/Tallinn")
  @SchedulerLock(name = "PositionCalculationJob_1130", lockAtMostFor = "55m", lockAtLeastFor = "5m")
  public void calculatePositions1130() {
    calculateForFunds(getPillar2Funds());
  }

  @Scheduled(cron = "0 30 15 * * *", zone = "Europe/Tallinn")
  @SchedulerLock(name = "PositionCalculationJob_1530", lockAtMostFor = "55m", lockAtLeastFor = "5m")
  public void calculatePositions1530() {
    calculateForFunds(getPillar3Funds());
  }

  public void calculateForFunds(List<TulevaFund> funds) {
    LocalDate date = LocalDate.now(clock());
    log.info("Starting position calculation: funds={}, date={}", funds, date);

    try {
      List<PositionCalculation> calculations = calculationService.calculate(funds, date);
      persistenceService.saveAll(calculations);

      notifyIssues(calculations);

      log.info(
          "Position calculation completed: funds={}, date={}, calculationCount={}",
          funds,
          date,
          calculations.size());

    } catch (Exception e) {
      log.error("Position calculation failed: funds={}, date={}", funds, date, e);
    }
  }

  private void notifyIssues(List<PositionCalculation> calculations) {
    for (PositionCalculation calculation : calculations) {
      ValidationStatus status = calculation.validationStatus();

      if (status == PRICE_DISCREPANCY) {
        notifier.notifyPriceDiscrepancy(
            calculation.fund(),
            calculation.isin(),
            calculation.date(),
            calculation.eodhdPrice(),
            calculation.yahooPrice(),
            calculation.priceDiscrepancyPercent());
      } else if (status == YAHOO_MISSING) {
        notifier.notifyYahooMissing(
            calculation.fund(), calculation.isin(), calculation.date(), calculation.eodhdPrice());
      } else if (status == EODHD_MISSING) {
        notifier.notifyEodhdMissing(
            calculation.fund(), calculation.isin(), calculation.date(), calculation.yahooPrice());
      } else if (status == NO_PRICE_DATA) {
        notifier.notifyNoPriceData(calculation.fund(), calculation.isin(), calculation.date());
      }

      if (isStalePrice(calculation)) {
        notifier.notifyStalePrice(
            calculation.fund(), calculation.isin(), calculation.date(), calculation.priceDate());
      }
    }
  }

  private boolean isStalePrice(PositionCalculation calculation) {
    return calculation.priceDate() != null && !calculation.priceDate().equals(calculation.date());
  }
}
