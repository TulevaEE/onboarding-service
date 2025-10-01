package ee.tuleva.onboarding.account;

import ee.tuleva.onboarding.fund.ApiFundResponse;
import java.math.BigDecimal;
import java.util.Locale;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApiFundBalanceResponse {
  private ApiFundResponse fund;
  private BigDecimal value;
  private BigDecimal unavailableValue;
  private String currency;
  private boolean activeContributions;
  private BigDecimal contributions;
  private BigDecimal subtractions;
  private BigDecimal profit;
  private BigDecimal units;

  static ApiFundBalanceResponse from(FundBalance fundBalance, Locale locale) {
    return ApiFundBalanceResponse.builder()
        .fund(new ApiFundResponse(fundBalance.getFund(), locale))
        .value(fundBalance.getValue())
        .unavailableValue(fundBalance.getUnavailableValue())
        .currency(fundBalance.getCurrency())
        .activeContributions(fundBalance.isActiveContributions())
        .contributions(fundBalance.getContributions())
        .subtractions(fundBalance.getSubtractions())
        .profit(fundBalance.getProfit())
        .units(fundBalance.getUnits())
        .build();
  }
}
