package ee.tuleva.onboarding.comparisons;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;

@Getter
@Setter
@Builder
public class ComparisonCalculation implements Serializable {
    private BigDecimal averageSalary;
    private BigDecimal returnRate;
    private BigDecimal targetPensionBalance;
    private BigDecimal currentPlanPensionBalance;
    private BigDecimal targetPensionFees;
    private BigDecimal currentPlanPensionFees;
}
