package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record PendingTrade(
    TulevaFund fund,
    @Nullable String reference,
    String isin,
    TransactionType side,
    BigDecimal quantity,
    BigDecimal total,
    Instant tradeDate,
    @Nullable LocalDate settlementDate) {

  public boolean isSameTradeAs(PendingTrade other) {
    if (reference != null && other.reference != null) {
      return fund == other.fund && reference.equals(other.reference);
    }
    return fund == other.fund
        && side == other.side
        && isin.equals(other.isin)
        && quantity.compareTo(other.quantity) == 0
        && tradeDate.equals(other.tradeDate);
  }
}
