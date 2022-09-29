package ee.tuleva.onboarding.epis.cashflows;

import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CashFlow {
  private String isin;
  private LocalDate date;
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

  public boolean isAfter(LocalDate other) {
    return date.isAfter(other);
  }

  public enum Type {
    CONTRIBUTION_CASH,
    CONTRIBUTION,
    SUBTRACTION,
    CASH,
    OTHER
  }
}
