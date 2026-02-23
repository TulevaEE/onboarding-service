package ee.tuleva.onboarding.savings.fund.nav.components;

import static ee.tuleva.onboarding.investment.position.AccountType.LIABILITY;
import static ee.tuleva.onboarding.savings.fund.nav.components.NavComponent.NavComponentType;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.savings.fund.nav.NavComponentContext;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedemptionsComponent implements NavComponent {

  private final FundPositionRepository fundPositionRepository;

  @Override
  public String getName() {
    return "pending_redemptions";
  }

  @Override
  public NavComponentType getType() {
    return NavComponentType.LIABILITY;
  }

  @Override
  public BigDecimal calculate(NavComponentContext context) {
    var fund = context.getFund();
    BigDecimal value =
        fundPositionRepository
            .findByNavDateAndFundAndAccountTypeAndAccountId(
                context.getPositionReportDate(), fund, LIABILITY, fund.getIsin())
            .map(position -> position.getMarketValue())
            .orElse(ZERO);
    if (value.signum() < 0) {
      throw new IllegalStateException(
          "Payables of redeemed units should not be negative: value=" + value);
    }
    return value;
  }
}
