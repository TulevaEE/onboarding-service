package ee.tuleva.onboarding.savings.fund.nav.components;

import static ee.tuleva.onboarding.ledger.UserAccount.CASH_RESERVED;
import static ee.tuleva.onboarding.savings.fund.nav.components.NavComponent.NavComponentType.ASSET;

import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.savings.fund.nav.NavComponentContext;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SubscriptionsComponent implements NavComponent {

  private final NavLedgerRepository navLedgerRepository;

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
    BigDecimal balance = navLedgerRepository.sumBalanceByAccountName(CASH_RESERVED.name());
    if (balance.signum() > 0) {
      throw new IllegalStateException(
          "CASH_RESERVED should be negative (liability), but was: " + balance);
    }
    return balance.negate();
  }

  @Override
  public boolean requiresPreliminaryNav() {
    return false;
  }
}
