package ee.tuleva.onboarding.comparisons;

import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FundComparison {
    private double actualReturnPercentage;
    private double estonianAverageReturnPercentage;
    private double marketAverageReturnPercentage;
}
