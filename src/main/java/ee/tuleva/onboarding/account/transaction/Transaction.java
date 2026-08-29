package ee.tuleva.onboarding.account.transaction;

import static ee.tuleva.onboarding.epis.CashFlow.Type.CONTRIBUTION;
import static ee.tuleva.onboarding.epis.CashFlow.Type.CONTRIBUTION_CASH;
import static ee.tuleva.onboarding.epis.CashFlow.Type.CONTRIBUTION_CASH_WORKPLACE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.comparing;

import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.epis.CashFlow;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

@Builder
public record Transaction(
    UUID id,
    BigDecimal amount,
    Currency currency,
    Instant time,
    Instant priceTime,
    Instant settledTime,
    @Nullable String isin,
    CashFlow.Type type,
    BigDecimal units,
    BigDecimal nav)
    implements Comparable<Transaction> {

  public Transaction {
    priceTime = priceTime == null ? time : priceTime;
    settledTime = settledTime == null ? time : settledTime;
  }

  public static Transaction from(CashFlow cashFlow) {
    String seed =
        cashFlow.getTime() + cashFlow.getIsin() + cashFlow.getAmount() + cashFlow.getType();
    return Transaction.builder()
        .id(UUID.nameUUIDFromBytes(seed.getBytes(UTF_8)))
        .amount(cashFlow.getAmount())
        .currency(cashFlow.getCurrency())
        .time(cashFlow.getTime())
        .priceTime(cashFlow.getPriceTime())
        .isin(cashFlow.getIsin())
        .type(cashFlow.getType())
        .units(cashFlow.getUnits())
        .nav(cashFlow.getNav())
        .build();
  }

  public boolean isAcquisition() {
    return type == CONTRIBUTION_CASH || type == CONTRIBUTION_CASH_WORKPLACE || type == CONTRIBUTION;
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
