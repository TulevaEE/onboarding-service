package ee.tuleva.onboarding.comparisons.fundvalue;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Value;

@Value
public class FundValue {
  private String comparisonFund;
  private LocalDate date;
  private BigDecimal value;
}
