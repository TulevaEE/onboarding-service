package ee.tuleva.onboarding.comparisons;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class ComparisonCommand {

    @Min(0)
    @Max(65)
    Integer age;

    @Min(0)
    BigDecimal monthlyWage;

    @Size(min = 12, max = 12)
    List<String> isinsFrom;

    Map<String, BigDecimal> currentCapitals;

    // must also exist in isinsFrom
    @Size(min = 12, max = 12)
    String activeIsin;

    Map<String, BigDecimal> managementFeeRates;

    // defaults that can be overwritten in API
    @Size(min = 12, max = 12)
    String isinTo = "AE123232334";

    Integer ageOfRetirement = 65;

    BigDecimal returnRate = new BigDecimal("0.05");

    BigDecimal annualSalaryGainRate = new BigDecimal("0.03");

    //percent from monthly wage
    BigDecimal secondPillarContributionRate = new BigDecimal("0.06");

}
