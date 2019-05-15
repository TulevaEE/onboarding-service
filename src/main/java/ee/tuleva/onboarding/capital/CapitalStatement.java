package ee.tuleva.onboarding.capital;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CapitalStatement {
    private BigDecimal membershipBonus;
    private BigDecimal capitalPayment;
    private BigDecimal profit;
}
