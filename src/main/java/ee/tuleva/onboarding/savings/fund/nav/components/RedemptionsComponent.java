package ee.tuleva.onboarding.savings.fund.nav.components;

import static ee.tuleva.onboarding.investment.position.AccountType.LIABILITY;
import static ee.tuleva.onboarding.savings.fund.nav.components.NavComponent.NavComponentType;
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
            .findByNavDateAndFundAndAccountType(context.getPositionReportDate(), fund, LIABILITY)
            .stream()
            .filter(p -> isRedemptionPayable(p.getAccountName()))
            .map(FundPosition::getMarketValue)
            .filter(Objects::nonNull)
            .reduce(ZERO, BigDecimal::add);
    if (value.signum() > 0) {
      throw new IllegalStateException(
          "Redemption payables should be negative (liability): fund="
              + fund
              + ", date="
              + context.getPositionReportDate()
              + ", value="
              + value);
    }
    return value.negate();
  }

  static boolean isRedemptionPayable(String accountName) {
    return accountName != null && accountName.contains("Payables of redeemed units");
  }
}
