package ee.tuleva.onboarding.savings.fund.nav.components;

import static ee.tuleva.onboarding.investment.position.AccountType.RECEIVABLES;
import static ee.tuleva.onboarding.savings.fund.nav.components.NavComponent.NavComponentType.ASSET;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.savings.fund.nav.NavComponentContext;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
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
            .findByNavDateAndFundAndAccountType(context.getPositionReportDate(), fund, RECEIVABLES)
            .stream()
            .filter(p -> isSubscriptionReceivable(p.getAccountName()))
            .map(FundPosition::getMarketValue)
            .filter(Objects::nonNull)
            .reduce(ZERO, BigDecimal::add);
    if (value.signum() < 0) {
      throw new IllegalStateException(
          "Subscription receivables should be positive (asset): fund="
              + fund
              + ", date="
              + context.getPositionReportDate()
              + ", value="
              + value);
    }
    return value;
  }

  static boolean isSubscriptionReceivable(String accountName) {
    return accountName != null && accountName.contains("Receivables of outstanding units");
  }
}
