package ee.tuleva.onboarding.epis.cashflows;

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

  public enum Type {
    CONTRIBUTION,
    OTHER
  }
}
