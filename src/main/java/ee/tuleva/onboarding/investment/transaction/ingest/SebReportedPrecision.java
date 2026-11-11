package ee.tuleva.onboarding.investment.transaction.ingest;

import static java.math.RoundingMode.HALF_UP;

import java.math.BigDecimal;
import java.math.MathContext;

final class SebReportedPrecision {

  static final MathContext SIGNIFICANT_DIGITS = new MathContext(10, HALF_UP);

  private SebReportedPrecision() {}

  static BigDecimal asSebReportsIt(BigDecimal value) {
    return value.round(SIGNIFICANT_DIGITS);
  }
}
