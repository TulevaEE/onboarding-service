package ee.tuleva.onboarding.comparisons;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
public class ComparisonResponse {

    BigDecimal currentFundFee;
    BigDecimal newFundFee;
    BigDecimal currentFundFutureValue;
    BigDecimal newFundFutureValue;

}
