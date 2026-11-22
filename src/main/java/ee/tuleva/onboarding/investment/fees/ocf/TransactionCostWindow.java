package ee.tuleva.onboarding.investment.fees.ocf;

import ee.tuleva.onboarding.investment.fees.ocf.OcfCalculationService.TransactionCost;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

record TransactionCostWindow(
    LocalDate start,
    LocalDate end,
    BigDecimal fees,
    BigDecimal averageAum,
    List<LocalDate> navDates) {

  TransactionCost at(BigDecimal rate, List<OcfGap> gaps) {
    return new TransactionCost(rate, start, end, fees, averageAum, navDates, gaps);
  }

  TransactionCost annualised() {
    return at(AnnualisedRate.of(start, end, fees, averageAum), List.of());
  }
}
