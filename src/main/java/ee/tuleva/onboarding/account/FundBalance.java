package ee.tuleva.onboarding.account;


import ee.tuleva.onboarding.fund.Fund;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
public class FundBalance {
    private Fund fund;
    private BigDecimal value;
    private BigDecimal units;
    private String currency;
    private Integer pillar;
    private boolean activeContributions;
    private BigDecimal contributionSum;

    public BigDecimal getProfit() {
        return value != null && contributionSum != null ? value.subtract(contributionSum) : null;
    }
}
