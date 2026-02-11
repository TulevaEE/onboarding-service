package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.SystemAccount.*;

import ee.tuleva.onboarding.ledger.LedgerTransactionService.LedgerEntryDto;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NavFeeAccrualLedger {

  private final LedgerAccountService ledgerAccountService;
  private final LedgerTransactionService ledgerTransactionService;
  private final Clock clock;

  @Transactional
  public void recordFeeAccrual(
      String fund, LocalDate accrualDate, SystemAccount feeAccount, BigDecimal amount) {
    if (amount == null || amount.signum() == 0) {
      return;
    }

    Map<String, Object> metadata =
        Map.of(
            "operationType",
            "FEE_ACCRUAL",
            "fund",
            fund,
            "feeType",
            feeAccount.name(),
            "accrualDate",
            accrualDate.toString());

    ledgerTransactionService.createTransaction(
        Instant.now(clock),
        metadata,
        entry(getNavEquityAccount(), amount),
        entry(getSystemAccount(feeAccount), amount.negate()));
  }

  private LedgerAccount getNavEquityAccount() {
    return getSystemAccount(NAV_EQUITY);
  }

  private LedgerAccount getSystemAccount(SystemAccount systemAccount) {
    return ledgerAccountService
        .findSystemAccount(systemAccount)
        .orElseGet(() -> ledgerAccountService.createSystemAccount(systemAccount));
  }

  private LedgerEntryDto entry(LedgerAccount account, BigDecimal amount) {
    return new LedgerEntryDto(account, amount);
  }
}
