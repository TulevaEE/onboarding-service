package ee.tuleva.onboarding.investment.transaction;

import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import org.jspecify.annotations.NullMarked;

@NullMarked
record PriceDeviation(
    String isin, BigDecimal manualPrice, BigDecimal lastIndexPrice, LocalDate lastIndexPriceDate) {

  static PriceDeviation between(PriceOverride override, ResolvedPrice lastIndexPrice) {
    return new PriceDeviation(
        override.isin(), override.price(), lastIndexPrice.usedPrice(), lastIndexPrice.priceDate());
  }

  BigDecimal gap() {
    return manualPrice.divide(lastIndexPrice, MathContext.DECIMAL64).subtract(BigDecimal.ONE);
  }

  boolean exceeds(BigDecimal threshold) {
    return gap().abs().compareTo(threshold) > 0;
  }

  String gapPercent() {
    return gap().movePointRight(2).setScale(1, HALF_UP).toPlainString();
  }
}
