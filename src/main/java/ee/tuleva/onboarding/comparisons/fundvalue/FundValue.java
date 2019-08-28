package ee.tuleva.onboarding.comparisons.fundvalue;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;

@Value
@Builder
public class FundValue {
    private Instant time;
    private BigDecimal value;
    private String comparisonFund;
}
