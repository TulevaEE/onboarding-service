package ee.tuleva.onboarding.savings.fund.nav.components;

import static ee.tuleva.onboarding.ledger.SystemAccount.CASH_POSITION;
import static ee.tuleva.onboarding.savings.fund.nav.components.NavComponent.NavComponentType.ASSET;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.savings.fund.nav.NavComponentContext;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CashPositionComponent implements NavComponent {

  private final NavLedgerRepository navLedgerRepository;

  @Override
  public String getName() {
    return "cash";
  }

  @Override
  public NavComponentType getType() {
    return ASSET;
  }

  @Override
  public BigDecimal calculate(NavComponentContext context) {
    BigDecimal balance =
        navLedgerRepository.getSystemAccountBalance(CASH_POSITION.getAccountName());
    return balance != null ? balance : ZERO;
  }

  @Override
  public boolean requiresPreliminaryNav() {
    return false;
  }
}
