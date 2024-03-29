package ee.tuleva.onboarding.capital;

import ee.tuleva.onboarding.currency.Currency;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CapitalStatement {

  private BigDecimal membershipBonus;
  private BigDecimal capitalPayment;
  private BigDecimal unvestedWorkCompensation;
  private BigDecimal workCompensation;
  private BigDecimal profit;
  private Currency currency;

  public BigDecimal getTotal() {
    return BigDecimal.ZERO
        .add(membershipBonus)
        .add(capitalPayment)
        .add(unvestedWorkCompensation)
        .add(workCompensation)
        .add(profit);
  }
}
