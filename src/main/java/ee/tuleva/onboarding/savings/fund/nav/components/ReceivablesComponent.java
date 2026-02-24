package ee.tuleva.onboarding.savings.fund.nav.components;

import static ee.tuleva.onboarding.ledger.SystemAccount.TRADE_RECEIVABLES;
import static ee.tuleva.onboarding.savings.fund.nav.components.NavComponent.NavComponentType.ASSET;

import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.savings.fund.nav.NavComponentContext;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReceivablesComponent implements NavComponent {

  private final LedgerService ledgerService;

  @Override
  public String getName() {
    return "receivables";
  }

  @Override
  public NavComponentType getType() {
    return ASSET;
  }

  @Override
  public BigDecimal calculate(NavComponentContext context) {
    return ledgerService.getSystemAccount(TRADE_RECEIVABLES).getBalanceAt(context.getCutoff());
  }
}
