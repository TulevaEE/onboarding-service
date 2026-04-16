package ee.tuleva.onboarding.savings.fund.nav.components;

import static ee.tuleva.onboarding.investment.position.AccountType.LIABILITY;
import static ee.tuleva.onboarding.savings.fund.nav.components.NavComponent.NavComponentType;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.savings.fund.nav.NavComponentContext;
import java.math.BigDecimal;
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
            .findByNavDateAndFundAndAccountTypeAndAccountId(
                context.getPositionReportDate(), fund, LIABILITY, fund.getIsin())
            .map(FundPosition::getMarketValue)
            .orElse(ZERO);
    if (value.signum() > 0) {
      log.error(
          "Unexpected positive redemption payables: fund={}, date={}, value={}",
          fund,
          context.getPositionReportDate(),
          value);
    }
    return value.abs();
  }
}
