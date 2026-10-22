package ee.tuleva.onboarding.investment.fees.ocf;

import static ee.tuleva.onboarding.investment.fees.ocf.OcfCalculationService.SCALE;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.investment.fees.ocf.OcfCalculationService.TransactionCost;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

record TransactionCostWindow(
    LocalDate start,
    LocalDate end,
    BigDecimal commissions,
    BigDecimal averageAum,
    List<LocalDate> navDates) {

  private static final BigDecimal DAYS_IN_YEAR = BigDecimal.valueOf(365);

  static LocalDate trailingYearStart(LocalDate end) {
    return end.minusYears(1).plusDays(1);
  }

  TransactionCost at(BigDecimal rate, List<OcfGap> gaps) {
    return new TransactionCost(rate, start, end, commissions, averageAum, navDates, gaps);
  }

  TransactionCost annualised() {
    var rate =
        start.equals(trailingYearStart(end))
            ? commissions.divide(averageAum, SCALE, HALF_UP)
            : annualisedOverTheFundsLife();
    return at(rate, List.of());
  }

  private BigDecimal annualisedOverTheFundsLife() {
    var daysOfFundLife = ChronoUnit.DAYS.between(start, end) + 1;
    return commissions
        .multiply(DAYS_IN_YEAR)
        .divide(averageAum.multiply(BigDecimal.valueOf(daysOfFundLife)), SCALE, HALF_UP);
  }
}
