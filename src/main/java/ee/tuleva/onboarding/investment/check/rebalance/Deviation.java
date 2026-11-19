package ee.tuleva.onboarding.investment.check.rebalance;

import java.math.BigDecimal;
import java.util.Comparator;

public record Deviation(String isin, BigDecimal modelWeight, BigDecimal actualWeight) {

  static final Comparator<Deviation> LARGEST_FIRST =
      Comparator.comparing(Deviation::size).reversed().thenComparing(Deviation::isin);

  public BigDecimal percentagePoints() {
    return actualWeight.subtract(modelWeight).movePointRight(2);
  }

  boolean exceeds(BigDecimal tolerance) {
    return size().compareTo(tolerance) > 0;
  }

  private BigDecimal size() {
    return actualWeight.subtract(modelWeight).abs();
  }
}
