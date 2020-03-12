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
    private BigDecimal contributions;
    private BigDecimal subtractions;

    @Deprecated
    public BigDecimal getContributionSum() {
        return contributions != null || subtractions != null ?
            ZERO.add(contributions == null ? ZERO : contributions)
                .add(subtractions == null ? ZERO : subtractions) :
            null;
    }

    public BigDecimal getProfit() {
        BigDecimal unavailableValue = this.unavailableValue != null ? this.unavailableValue : ZERO;
        return value != null && getContributionSum() != null ?
            value.add(unavailableValue).subtract(getContributionSum()) :
            null;
    }

    public String getIsin() {
        return fund.getIsin();
    }

    public BigDecimal getTotalValue() {
        return ZERO
            .add(value == null ? ZERO : value)
            .add(unavailableValue == null ? ZERO : unavailableValue);
    }
}
