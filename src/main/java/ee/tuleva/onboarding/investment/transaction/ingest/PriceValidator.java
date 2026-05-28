package ee.tuleva.onboarding.investment.transaction.ingest;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
class PriceValidator {

  private static final MathContext MATH_CONTEXT = new MathContext(16, RoundingMode.HALF_UP);
  private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

  boolean isWithinTolerance(BigDecimal execPrice, BigDecimal navPrice, BigDecimal tolerance) {
    BigDecimal ratio = absoluteRatio(execPrice, navPrice);
    return ratio.compareTo(tolerance) <= 0;
  }

  BigDecimal computeDeltaPercent(BigDecimal execPrice, BigDecimal navPrice) {
    return absoluteRatio(execPrice, navPrice).multiply(ONE_HUNDRED, MATH_CONTEXT);
  }

  private static BigDecimal absoluteRatio(BigDecimal execPrice, BigDecimal navPrice) {
    BigDecimal diff = execPrice.subtract(navPrice).abs();
    return diff.divide(navPrice, MATH_CONTEXT);
  }
}
