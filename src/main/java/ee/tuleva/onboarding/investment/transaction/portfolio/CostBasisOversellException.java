package ee.tuleva.onboarding.investment.transaction.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;

@Getter
class CostBasisOversellException extends IllegalStateException {

  private final String fundIsin;
  private final String instrumentIsin;
  private final LocalDate asOfDate;
  private final BigDecimal heldQuantity;
  private final BigDecimal sellQuantity;

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
    this.fundIsin = fundIsin;
    this.instrumentIsin = instrumentIsin;
    this.asOfDate = asOfDate;
    this.heldQuantity = heldQuantity;
    this.sellQuantity = sellQuantity;
  }
}
