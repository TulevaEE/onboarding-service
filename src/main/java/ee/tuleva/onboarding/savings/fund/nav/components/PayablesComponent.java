package ee.tuleva.onboarding.savings.fund.nav.components;

import static ee.tuleva.onboarding.ledger.SystemAccount.TRADE_PAYABLES;

import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.savings.fund.nav.NavComponentContext;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PayablesComponent implements NavComponent {

  private final LedgerService ledgerService;

  @Override
  public String getName() {
    return "payables";
  }

  @Override
  public NavComponentType getType() {
    return NavComponentType.LIABILITY;
  }

  @Override
  public BigDecimal calculate(NavComponentContext context) {
    BigDecimal balance =
        ledgerService.getSystemAccount(TRADE_PAYABLES).getBalanceAt(context.getCutoff());
    if (balance.signum() > 0) {
      throw new IllegalStateException(
          "TRADE_PAYABLES should be negative (liability), but was: " + balance);
    }
    return balance.negate();
  }
}
