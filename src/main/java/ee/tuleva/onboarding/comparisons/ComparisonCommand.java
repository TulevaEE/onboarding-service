package ee.tuleva.onboarding.comparisons;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@ToString
public class ComparisonCommand implements Serializable {

    @Min(0)
    @Max(65)
    Integer age;

    @Min(0)
    BigDecimal monthlyWage;

    Map<String, BigDecimal> currentCapitals;

    // must also exist in isinsFrom
    @Size(min = 12, max = 12)
    String activeFundIsin;

    Map<String, BigDecimal> managementFeeRates;

    // defaults that can be overwritten in API
    // todo change when known
    @Size(min = 12, max = 12)
    String isinTo = "EE3600109435";

    Integer ageOfRetirement = 65;

    BigDecimal returnRate = new BigDecimal("0.05");

    BigDecimal annualSalaryGainRate = new BigDecimal("0.03");

    //percent from monthly wage
    BigDecimal secondPillarContributionRate = new BigDecimal("0.06");

    boolean isTulevaMember = true;

    BigDecimal tulevaMemberBonus = new BigDecimal("0.0005");

}
