package ee.tuleva.onboarding.epis.cashflows;

import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
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
  private BigDecimal amount;
  private String currency;
  private Type type;

  public boolean isContribution() {
    return type == CONTRIBUTION_CASH || type == CONTRIBUTION;
  }

  public boolean isCashContribution() {
    return type == CONTRIBUTION_CASH;
  }

  public boolean isSubtraction() {
    return type == SUBTRACTION;
  }

  public boolean isAfter(Instant other) {
    return time.isAfter(other);
  }

  public boolean isCash() {
    return type == CASH;
  }

  @Override
  public int compareTo(@NotNull CashFlow other) {
    return Comparator.comparing(CashFlow::getTime)
        .thenComparing(CashFlow::getAmount)
        .thenComparing(CashFlow::getCurrency)
        .thenComparing(CashFlow::getType)
        .compare(this, other);
  }

  public enum Type {
    CONTRIBUTION_CASH,
    CONTRIBUTION,
    SUBTRACTION,
    CASH,
    OTHER
  }
}
