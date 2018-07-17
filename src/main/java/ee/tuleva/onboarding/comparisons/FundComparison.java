package ee.tuleva.onboarding.comparisons;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class FundComparison {
    private double actualReturnPercentage;
    private double estonianAverageReturnPercentage;
    private double marketAverageReturnPercentage;
}
