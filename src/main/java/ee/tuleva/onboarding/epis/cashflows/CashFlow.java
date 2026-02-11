package ee.tuleva.onboarding.epis.cashflows;

import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.CASH;
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.CONTRIBUTION;
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.CONTRIBUTION_CASH;
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.CONTRIBUTION_CASH_WORKPLACE;
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.REFUND;
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.SUBTRACTION;
import static java.util.Comparator.comparing;
import static java.util.Comparator.nullsLast;

import ee.tuleva.onboarding.currency.Currency;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CashFlow implements Comparable<CashFlow> {

  private String isin;
  private Instant time;
  private Instant priceTime;
  private BigDecimal amount;
  private Currency currency;
  private Type type;
  private BigDecimal units;
  private BigDecimal nav;

  public Instant getPriceTime() {
    if (priceTime != null) {
      return priceTime;
    }
    return time;
  }

  public boolean isContribution() {
    return type == CONTRIBUTION_CASH || type == CONTRIBUTION_CASH_WORKPLACE || type == CONTRIBUTION;
  }

  public boolean isCashContribution() {
    return type == CONTRIBUTION_CASH || type == CONTRIBUTION_CASH_WORKPLACE;
  }

  public boolean isSubtraction() {
    return type == SUBTRACTION;
  }

  public boolean isPriceTimeAfter(Instant other) {
    return getPriceTime().isAfter(other);
  }

  public boolean isAfter(Instant other) {
    return time.isAfter(other);
  }

  public boolean isCash() {
    return type == CASH;
  }

  public boolean isRefund() {
    return type == REFUND;
  }

  @Override
  public int compareTo(@NotNull CashFlow other) {
    return comparing(CashFlow::getTime)
        .thenComparing(nullsLast(comparing(CashFlow::getPriceTime)))
        .thenComparing(CashFlow::getAmount)
        .thenComparing(CashFlow::getCurrency)
        .thenComparing(CashFlow::getType)
        .compare(this, other);
  }

  @Override
  public String toString() {
    return "{%s, %s, %s, %s, %s, units=%s, nav=%s}"
        .formatted(isin, time, amount, type, priceTime, units, nav);
  }

  public enum Type {
    CONTRIBUTION_CASH,
    CONTRIBUTION_CASH_WORKPLACE,
    CONTRIBUTION,
    SUBTRACTION,
    CASH,
    REFUND,
    OTHER
  }
}
