package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_UNITS_OUTSTANDING;
import static ee.tuleva.onboarding.ledger.UserAccount.FUND_UNITS;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SavingsFundUnits {

  private final LedgerService ledgerService;

  public BigDecimal unitsOutstanding() {
    return unitsOutstandingAccount().getBalance();
  }

  public BigDecimal unitsOutstandingAt(Instant cutoff) {
    return unitsOutstandingAccount().getBalanceAt(cutoff);
  }

  public int unitHolderCount() {
    return ledgerService.countAccountsWithPositiveBalance(FUND_UNITS);
  }

  private LedgerAccount unitsOutstandingAccount() {
    return ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING, TKF100);
  }
}
