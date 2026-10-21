package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record OpenTrade(
    TulevaFund fund,
    TransactionType side,
    BigDecimal total,
    @Nullable String reference,
    @Nullable String isin,
    @Nullable LocalDate settlementDate,
    @Nullable BigDecimal quantity,
    @Nullable Instant tradeDate) {

  public boolean isSameTradeAs(OpenTrade other) {
    if (fund != other.fund) {
      return false;
    }
    if (reference != null && other.reference != null) {
      return reference.equals(other.reference);
    }
    return side == other.side
        && Objects.equals(isin, other.isin)
        && Objects.equals(tradeDate, other.tradeDate)
        && hasSameQuantityAs(other);
  }

  private boolean hasSameQuantityAs(OpenTrade other) {
    return quantity != null && other.quantity != null && quantity.compareTo(other.quantity) == 0;
  }
}
