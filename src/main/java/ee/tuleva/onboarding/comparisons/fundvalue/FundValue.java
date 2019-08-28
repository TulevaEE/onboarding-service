package ee.tuleva.onboarding.comparisons.fundvalue;

import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;

@Value
public class FundValue {
    private String comparisonFund;
    private LocalDate date;
    private BigDecimal value;
}
