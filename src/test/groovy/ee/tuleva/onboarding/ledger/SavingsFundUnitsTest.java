package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_UNITS_OUTSTANDING;
import static ee.tuleva.onboarding.ledger.UserAccount.FUND_UNITS;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavingsFundUnitsTest {

  @Mock private LedgerService ledgerService;
  @Mock private LedgerAccount unitsOutstandingAccount;
  @InjectMocks private SavingsFundUnits unitStats;

  @Test
  void readsUnitStatsFromTheSavingsFundLedgerAccounts() {
    var cutoff = Instant.parse("2026-08-30T21:00:00Z");
    given(ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING, TKF100))
        .willReturn(unitsOutstandingAccount);
    given(unitsOutstandingAccount.getBalance()).willReturn(new BigDecimal("1000.123"));
    given(unitsOutstandingAccount.getBalanceAt(cutoff)).willReturn(new BigDecimal("900.456"));
    given(ledgerService.countAccountsWithPositiveBalance(FUND_UNITS)).willReturn(42);

    assertThat(unitStats.unitsOutstanding()).isEqualByComparingTo(new BigDecimal("1000.123"));
    assertThat(unitStats.unitsOutstandingAt(cutoff))
        .isEqualByComparingTo(new BigDecimal("900.456"));
    assertThat(unitStats.unitHolderCount()).isEqualTo(42);
  }
}
