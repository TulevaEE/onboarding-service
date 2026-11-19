package ee.tuleva.onboarding.investment.transaction.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;

@Getter
class CostBasisOversellException extends IllegalStateException {

  private final CostBasisOversell oversell;

  CostBasisOversellException(
      String fundIsin,
      String instrumentIsin,
      LocalDate asOfDate,
      BigDecimal heldQuantity,
      BigDecimal sellQuantity) {
    super(
        String.format(
            "Cost-basis oversell: fund=%s, isin=%s, asOfDate=%s, heldQty=%s, sellQty=%s",
            fundIsin, instrumentIsin, asOfDate, heldQuantity, sellQuantity));
    this.oversell =
        new CostBasisOversell(fundIsin, instrumentIsin, asOfDate, heldQuantity, sellQuantity);
  }
}
