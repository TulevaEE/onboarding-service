package ee.tuleva.onboarding.investment.fees.instrument;

import static java.math.RoundingMode.HALF_UP;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.IsoFields.DAY_OF_QUARTER;

import java.math.BigDecimal;
import java.time.LocalDate;

enum AccrualMethod {
  DAILY_HOLDING {
    @Override
    BigDecimal dailyAmount(BigDecimal holdingValue, BigDecimal annualRate, LocalDate day) {
      return holdingValue
          .multiply(annualRate)
          .divide(BigDecimal.valueOf(day.lengthOfYear()), AMOUNT_SCALE, HALF_UP);
    }
  },
  MONTH_END_HOLDING {
    @Override
    BigDecimal dailyAmount(BigDecimal holdingValue, BigDecimal annualRate, LocalDate day) {
      return holdingValue
          .multiply(annualRate)
          .multiply(THIRTY_360_MONTH_DAYS)
          .divide(
              THIRTY_360_YEAR_DAYS.multiply(BigDecimal.valueOf(day.lengthOfMonth())),
              AMOUNT_SCALE,
              HALF_UP);
    }
  },
  QUARTER_AVERAGE_MONTH_END {
    @Override
    BigDecimal dailyAmount(BigDecimal holdingValue, BigDecimal annualRate, LocalDate day) {
      var quarterStart = day.with(DAY_OF_QUARTER, 1);
      var daysInTheQuarter = DAYS.between(quarterStart, quarterStart.plusMonths(3));
      return holdingValue
          .multiply(annualRate)
          .divide(
              QUARTERS_IN_A_YEAR.multiply(BigDecimal.valueOf(daysInTheQuarter)),
              AMOUNT_SCALE,
              HALF_UP);
    }
  };

  static final int AMOUNT_SCALE = 10;
  private static final BigDecimal THIRTY_360_MONTH_DAYS = BigDecimal.valueOf(30);
  private static final BigDecimal THIRTY_360_YEAR_DAYS = BigDecimal.valueOf(360);
  private static final BigDecimal QUARTERS_IN_A_YEAR = BigDecimal.valueOf(4);

  abstract BigDecimal dailyAmount(BigDecimal holdingValue, BigDecimal annualRate, LocalDate day);
}
