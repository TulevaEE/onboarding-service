package ee.tuleva.onboarding.comparisons.overview;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AccountOverview {
  private List<Transaction> transactions;
  private BigDecimal beginningBalance;
  private BigDecimal endingBalance;
  private Instant startTime;
  private Instant endTime;
  private Integer pillar;
}
