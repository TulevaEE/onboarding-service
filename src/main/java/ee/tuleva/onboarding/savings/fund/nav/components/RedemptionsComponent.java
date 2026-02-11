package ee.tuleva.onboarding.savings.fund.nav.components;

import static ee.tuleva.onboarding.ledger.UserAccount.FUND_UNITS_RESERVED;
import static ee.tuleva.onboarding.savings.fund.nav.components.NavComponent.NavComponentType.LIABILITY;

import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.savings.fund.nav.NavComponentContext;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedemptionsComponent implements NavComponent {

  private final NavLedgerRepository navLedgerRepository;

  @Override
  public String getName() {
    return "pending_redemptions";
  }

  @Override
  public NavComponentType getType() {
    return LIABILITY;
  }

  @Override
  public BigDecimal calculate(NavComponentContext context) {
    BigDecimal balance = navLedgerRepository.sumBalanceByAccountName(FUND_UNITS_RESERVED.name());
    if (balance.signum() > 0) {
      throw new IllegalStateException(
          "FUND_UNITS_RESERVED should be negative (liability), but was: " + balance);
    }
    BigDecimal reservedUnits = balance.negate();

    if (reservedUnits.signum() == 0) {
      return BigDecimal.ZERO;
    }

    BigDecimal preliminaryNavPerUnit = context.getPreliminaryNavPerUnit();
    if (preliminaryNavPerUnit == null || preliminaryNavPerUnit.signum() == 0) {
      throw new IllegalStateException(
          "Preliminary NAV per unit is required for redemption calculation");
    }

    return reservedUnits.multiply(preliminaryNavPerUnit);
  }

  @Override
  public boolean requiresPreliminaryNav() {
    return true;
  }
}
