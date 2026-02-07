package ee.tuleva.onboarding.account.transaction;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.comparing;

import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.epis.cashflows.CashFlow;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import org.jetbrains.annotations.NotNull;

@Builder
public record Transaction(
    UUID id,
    BigDecimal amount,
    Currency currency,
    Instant time,
    String isin,
    CashFlow.Type type,
    BigDecimal units,
    BigDecimal nav)
    implements Comparable<Transaction> {

  public static Transaction from(CashFlow cashFlow) {
    String seed =
        cashFlow.getTime() + cashFlow.getIsin() + cashFlow.getAmount() + cashFlow.getType();
    return Transaction.builder()
        .id(UUID.nameUUIDFromBytes(seed.getBytes(UTF_8)))
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
