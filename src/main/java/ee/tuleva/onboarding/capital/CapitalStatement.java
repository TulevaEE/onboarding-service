package ee.tuleva.onboarding.capital;

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
}
