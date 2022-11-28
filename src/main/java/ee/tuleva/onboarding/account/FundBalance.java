package ee.tuleva.onboarding.account;

import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.fund.Fund;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class FundBalance {

  private Fund fund;
  private BigDecimal value;
  private BigDecimal unavailableValue;
  private BigDecimal units;
  private String currency;
  private boolean activeContributions;
  private BigDecimal contributions;
  private BigDecimal subtractions;

  public BigDecimal getProfit() {
    BigDecimal unavailableValue = this.unavailableValue != null ? this.unavailableValue : ZERO;
    return value != null && getContributionSum() != null
        ? value.add(unavailableValue).subtract(getContributionSum())
        : null;
  }

  public String getIsin() {
    return fund.getIsin();
  }

  public Integer getPillar() {
    return fund.getPillar();
  }

  public BigDecimal getTotalValue() {
    return ZERO.add(value == null ? ZERO : value)
        .add(unavailableValue == null ? ZERO : unavailableValue);
  }

  public boolean isOwnFund() {
    return fund.isOwnFund();
  }

  public boolean isExitRestricted() {
    return fund.isExitRestricted();
  }

  BigDecimal getContributionSum() {
    BigDecimal sum =
        ZERO.add(contributions == null ? ZERO : contributions)
            .add(subtractions == null ? ZERO : subtractions);
    return contributions != null || subtractions != null ? sum : null;
  }
}
