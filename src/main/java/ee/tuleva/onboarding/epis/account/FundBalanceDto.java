package ee.tuleva.onboarding.epis.account;

import java.math.BigDecimal;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class FundBalanceDto {
  private String isin;
  private BigDecimal value;
  private BigDecimal unavailableValue;
  private String currency;
  private BigDecimal units;
  private BigDecimal unavailableUnits;
  private BigDecimal nav;
  private boolean activeContributions;
}
