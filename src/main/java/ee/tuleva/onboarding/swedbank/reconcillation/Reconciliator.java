package ee.tuleva.onboarding.swedbank.reconcillation;

import static ee.tuleva.onboarding.banking.statement.BankStatementBalance.StatementBalanceType.CLOSE;

import ee.tuleva.onboarding.banking.statement.BankStatement;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.swedbank.fetcher.SwedbankAccountConfiguration;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class Reconciliator {

  private final LedgerService ledgerService;
  private final SwedbankAccountConfiguration swedbankAccountConfiguration;
  private final Clock clock;

  public void reconcile(BankStatement bankStatement) {
    var closingBankBalance =
        bankStatement.getBalances().stream()
            .filter(balance -> balance.type().equals(CLOSE))
            .findFirst()
            .orElseThrow();

    var reconciliationTime = clock.instant();

    var iban = bankStatement.getBankStatementAccount().iban();
    var bankStatementAccount = swedbankAccountConfiguration.getAccountType(iban);

    if (bankStatementAccount == null) {
      log.error("Unknown account type: iban={}", iban);
      return;
    }

    var ledgerSystemAccount = bankStatementAccount.getLedgerAccount();
    var ledgerAccountBalance =
        ledgerService.getSystemAccount(ledgerSystemAccount).getBalanceAt(reconciliationTime);

    log.info(
        "Reconciling: bankAccount={}, closingBalance={}, ledgerAccount={}, ledgerBalance={}",
        bankStatementAccount,
        closingBankBalance.balance(),
        ledgerSystemAccount,
        ledgerAccountBalance);

    if (ledgerAccountBalance.compareTo(closingBankBalance.balance()) != 0) {
      throw new IllegalStateException(
          "Bank statement reconciliation failed: bankAccount=%s, closingBalance=%s, ledgerAccount=%s, ledgerBalance=%s"
              .formatted(
                  bankStatementAccount,
                  closingBankBalance.balance(),
                  ledgerSystemAccount,
                  ledgerAccountBalance));
    }
  }
}
