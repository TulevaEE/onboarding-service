package ee.tuleva.onboarding.account;


import ee.tuleva.onboarding.fund.Fund;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

import static java.math.BigDecimal.ZERO;

@Getter
@Setter
@Builder
public class FundBalance {
    private Fund fund;
    private BigDecimal value;
    private BigDecimal unavailableValue;
    private BigDecimal units;
    private String currency;
    private Integer pillar;
    private boolean activeContributions;
    private BigDecimal contributionSum;

    public BigDecimal getProfit() {
        BigDecimal unavailableValue = this.unavailableValue != null ? this.unavailableValue : ZERO;
        return value != null && contributionSum != null ? value.add(unavailableValue).subtract(contributionSum) : null;
    }
}
