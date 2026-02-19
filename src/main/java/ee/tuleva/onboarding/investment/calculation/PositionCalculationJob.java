package ee.tuleva.onboarding.investment.calculation;

import static ee.tuleva.onboarding.fund.TulevaFund.getPillar2Funds;
import static ee.tuleva.onboarding.fund.TulevaFund.getPillar3Funds;
import static ee.tuleva.onboarding.fund.TulevaFund.getSavingsFunds;
import static ee.tuleva.onboarding.investment.JobRunSchedule.*;
import static ee.tuleva.onboarding.investment.calculation.ValidationStatus.NO_PRICE_DATA;
import static ee.tuleva.onboarding.investment.calculation.ValidationStatus.PRICE_DISCREPANCY;
import static ee.tuleva.onboarding.investment.calculation.ValidationStatus.YAHOO_MISSING;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Stream;
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

  private static final LocalTime MORNING_CUTOFF = LocalTime.of(11, 30);
  private static final LocalTime AFTERNOON_CUTOFF = LocalTime.of(15, 30);

  private final PositionCalculationService calculationService;
  private final PositionCalculationPersistenceService persistenceService;
  private final PositionCalculationNotifier notifier;

  @Scheduled(cron = CALCULATE_MORNING, zone = TIMEZONE)
  @SchedulerLock(
      name = "PositionCalculationJob_morning",
      lockAtMostFor = "55m",
      lockAtLeastFor = "5m")
  public void calculatePositionsMorning() {
    calculateForFunds(getPillar2Funds(), MORNING_CUTOFF);
  }

  @Scheduled(cron = CALCULATE_AFTERNOON, zone = TIMEZONE)
  @SchedulerLock(
      name = "PositionCalculationJob_afternoon",
      lockAtMostFor = "55m",
      lockAtLeastFor = "5m")
  public void calculatePositionsAfternoon() {
    calculateForFunds(
        Stream.concat(getPillar3Funds().stream(), getSavingsFunds().stream()).toList(),
        AFTERNOON_CUTOFF);
  }

  public void calculateForFunds(List<TulevaFund> funds) {
    calculateForFunds(funds, null);
  }

  public void calculateForFunds(List<TulevaFund> funds, LocalTime cutoffTime) {
    log.info("Starting position calculation: funds={}", funds);

    try {
      List<PositionCalculation> calculations =
          calculationService.calculateForLatestDate(funds, cutoffTime);
      persistenceService.saveAll(calculations);

      notifyIssues(calculations);

      log.info(
          "Position calculation completed: funds={}, calculationCount={}",
          funds,
          calculations.size());

    } catch (Exception e) {
      log.error("Position calculation failed: funds={}", funds, e);
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
