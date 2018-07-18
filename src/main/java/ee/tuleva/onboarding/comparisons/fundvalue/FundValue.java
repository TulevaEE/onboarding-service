package ee.tuleva.onboarding.comparisons.fundvalue;

import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;

@Value
public class FundValue {
    private Instant time;
    private BigDecimal value;
    private ComparisonFund comparisonFund;
}
