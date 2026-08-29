package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_UNITS_OUTSTANDING;
import static ee.tuleva.onboarding.ledger.UserAccount.FUND_UNITS;

import ee.tuleva.onboarding.fund.SavingsFundUnitStats;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SavingsFundUnitLedgerStats implements SavingsFundUnitStats {

  private final LedgerService ledgerService;

  @Override
  public BigDecimal unitsOutstanding() {
    return unitsOutstandingAccount().getBalance();
  }

  @Override
  public BigDecimal unitsOutstandingAt(Instant cutoff) {
    return unitsOutstandingAccount().getBalanceAt(cutoff);
  }

  @Override
  public int unitHolderCount() {
    return ledgerService.countAccountsWithPositiveBalance(FUND_UNITS);
  }

  private LedgerAccount unitsOutstandingAccount() {
    return ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING, TKF100);
  }
}
