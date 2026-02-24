package ee.tuleva.onboarding.savings.fund.nav.components;

import static ee.tuleva.onboarding.ledger.SystemAccount.CASH_POSITION;
import static ee.tuleva.onboarding.savings.fund.nav.components.NavComponent.NavComponentType.ASSET;

import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.savings.fund.nav.NavComponentContext;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CashPositionComponent implements NavComponent {

  private final LedgerService ledgerService;

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
    return ledgerService.getSystemAccount(CASH_POSITION).getBalanceAt(context.getCutoff());
  }
}
