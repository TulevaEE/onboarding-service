package ee.tuleva.onboarding.account.transaction;

import static java.util.Comparator.comparing;
import static java.util.Comparator.nullsLast;

import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.epis.cashflows.CashFlow;
import java.math.BigDecimal;
import java.time.Instant;
import org.jetbrains.annotations.NotNull;

public record Transaction(
    BigDecimal amount,
    Currency currency,
    Instant time,
    String isin,
    CashFlow.Type type,
    String comment)
    implements Comparable<Transaction> {

  public static Transaction from(CashFlow cashFlow) {
    return new Transaction(
        cashFlow.getAmount(),
        cashFlow.getCurrency(),
        cashFlow.getTime(),
        cashFlow.getIsin(),
        cashFlow.getType(),
        cashFlow.getComment());
  }

  @Override
  public int compareTo(@NotNull Transaction other) {
    return comparing(Transaction::time)
        .thenComparing(Transaction::amount)
        .thenComparing(Transaction::currency)
        .thenComparing(Transaction::type)
        .thenComparing(Transaction::comment, nullsLast(String::compareToIgnoreCase))
        .compare(this, other);
  }
}
