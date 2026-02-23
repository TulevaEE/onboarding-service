package ee.tuleva.onboarding.savings.fund.nav.components;

import static ee.tuleva.onboarding.investment.position.AccountType.RECEIVABLES;
import static ee.tuleva.onboarding.savings.fund.nav.components.NavComponent.NavComponentType.ASSET;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.savings.fund.nav.NavComponentContext;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SubscriptionsComponent implements NavComponent {

  private final FundPositionRepository fundPositionRepository;

  @Override
  public String getName() {
    return "pending_subscriptions";
  }

  @Override
  public NavComponentType getType() {
    return ASSET;
  }

  @Override
  public BigDecimal calculate(NavComponentContext context) {
    var fund = context.getFund();
    BigDecimal value =
        fundPositionRepository
            .findByNavDateAndFundAndAccountTypeAndAccountId(
                context.getPositionReportDate(), fund, RECEIVABLES, fund.getIsin())
            .map(position -> position.getMarketValue())
            .orElse(ZERO);
    if (value.signum() < 0) {
      throw new IllegalStateException(
          "Receivables of outstanding units should not be negative: value=" + value);
    }
    return value;
  }
}
