package ee.tuleva.onboarding.investment.fees.ocf;

import static ee.tuleva.onboarding.investment.fees.ocf.OcfCalculationService.SCALE;
import static java.math.RoundingMode.HALF_UP;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

final class AnnualisedRate {

  private static final BigDecimal DAYS_IN_YEAR = BigDecimal.valueOf(365);

  private AnnualisedRate() {}

  static LocalDate trailingYearStart(LocalDate end) {
    return end.minusYears(1).plusDays(1);
  }

  static BigDecimal of(LocalDate start, LocalDate end, BigDecimal costs, BigDecimal averageAum) {
    return start.equals(trailingYearStart(end))
        ? costs.divide(averageAum, SCALE, HALF_UP)
        : overTheDaysCovered(start, end, costs, averageAum);
  }

  private static BigDecimal overTheDaysCovered(
      LocalDate start, LocalDate end, BigDecimal costs, BigDecimal averageAum) {
    var daysCovered = ChronoUnit.DAYS.between(start, end) + 1;
    return costs
        .multiply(DAYS_IN_YEAR)
        .divide(averageAum.multiply(BigDecimal.valueOf(daysCovered)), SCALE, HALF_UP);
  }
}
