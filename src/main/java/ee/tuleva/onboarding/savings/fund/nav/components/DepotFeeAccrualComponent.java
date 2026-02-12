package ee.tuleva.onboarding.savings.fund.nav.components;

import static ee.tuleva.onboarding.ledger.SystemAccount.DEPOT_FEE_ACCRUAL;
import static ee.tuleva.onboarding.savings.fund.nav.components.NavComponent.NavComponentType.LIABILITY;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.savings.fund.nav.NavComponentContext;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DepotFeeAccrualComponent implements NavComponent {

  private final NavLedgerRepository navLedgerRepository;

  @Override
  public String getName() {
    return "depot_fee_accrual";
  }

  @Override
  public NavComponentType getType() {
    return LIABILITY;
  }

  @Override
  public BigDecimal calculate(NavComponentContext context) {
    BigDecimal balance = navLedgerRepository.getSystemAccountBalance(DEPOT_FEE_ACCRUAL.name());
    if (balance == null) {
      return ZERO;
    }
    return balance.abs();
  }

  @Override
  public boolean requiresPreliminaryNav() {
    return false;
  }
}
