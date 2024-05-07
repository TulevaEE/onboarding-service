package ee.tuleva.onboarding.capital;

import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType;
import ee.tuleva.onboarding.currency.Currency;
import java.math.BigDecimal;

public record CapitalRow(
    MemberCapitalEventType type, BigDecimal contributions, BigDecimal profit, Currency currency) {
  public BigDecimal value() {
    return ZERO.add(contributions != null ? contributions : ZERO)
        .add(profit != null ? profit : ZERO);
  }
}
