package ee.tuleva.onboarding.account.transaction;

import static java.util.Comparator.comparing;

import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.epis.cashflows.CashFlow;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;
import org.jetbrains.annotations.NotNull;

@Builder
public record Transaction(
    BigDecimal amount,
    Currency currency,
    Instant time,
    String isin,
    CashFlow.Type type,
    BigDecimal units,
    BigDecimal nav)
    implements Comparable<Transaction> {

  public static Transaction from(CashFlow cashFlow) {
    return Transaction.builder()
        .amount(cashFlow.getAmount())
        .currency(cashFlow.getCurrency())
        .time(cashFlow.getTime())
        .isin(cashFlow.getIsin())
        .type(cashFlow.getType())
        .units(cashFlow.getUnits())
        .nav(cashFlow.getNav())
        .build();
  }

  @Override
  public int compareTo(@NotNull Transaction other) {
    return comparing(Transaction::time)
        .thenComparing(Transaction::amount)
        .thenComparing(Transaction::currency)
        .thenComparing(Transaction::type)
        .compare(this, other);
  }
}
