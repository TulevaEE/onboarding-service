package ee.tuleva.onboarding.account.transaction;

import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.epis.cashflows.CashFlow;
import java.math.BigDecimal;
import java.time.Instant;

public record Transaction(
    BigDecimal amount,
    Currency currency,
    Instant time,
    String isin,
    CashFlow.Type type,
    String comment) {

  public static Transaction from(CashFlow cashFlow) {
    return new Transaction(
        cashFlow.getAmount(),
        cashFlow.getCurrency(),
        cashFlow.getTime(),
        cashFlow.getIsin(),
        cashFlow.getType(),
        cashFlow.getComment());
  }
}
