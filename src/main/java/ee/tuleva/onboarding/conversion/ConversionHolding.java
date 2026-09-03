package ee.tuleva.onboarding.conversion;

import java.math.BigDecimal;

public record ConversionHolding(
    int pillar,
    String isin,
    boolean ownFund,
    boolean exitRestricted,
    boolean activeContributions,
    BigDecimal value,
    BigDecimal units,
    BigDecimal ongoingChargesFigure) {

  public boolean hasAnyValue() {
    return value.compareTo(BigDecimal.ZERO) > 0;
  }
}
