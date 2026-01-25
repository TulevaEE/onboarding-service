package ee.tuleva.onboarding.banking.seb.reconciliation;

import static ee.tuleva.onboarding.banking.statement.BankStatementBalance.StatementBalanceType.CLOSE;

import ee.tuleva.onboarding.banking.seb.SebAccountConfiguration;
import ee.tuleva.onboarding.banking.statement.BankStatement;
import ee.tuleva.onboarding.ledger.LedgerService;
import java.time.LocalTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class SebReconciliator {

  private final LedgerService ledgerService;
  private final SebAccountConfiguration sebAccountConfiguration;

  public void reconcile(BankStatement bankStatement) {
    var closingBankBalance =
        bankStatement.getBalances().stream()
            .filter(balance -> balance.type().equals(CLOSE))
            .findFirst()
            .orElseThrow();

    var bankBalanceTime =
        closingBankBalance
            .time()
            .atStartOfDay(ZoneId.of("Europe/Tallinn"))
            .with(LocalTime.MAX)
            .toInstant();

    var iban = bankStatement.getBankStatementAccount().iban();
    var bankStatementAccount = sebAccountConfiguration.getAccountType(iban);

    if (bankStatementAccount == null) {
      log.error("Unknown account type: iban={}", iban);
      return;
    }

    var ledgerSystemAccount = bankStatementAccount.getLedgerAccount();
    var ledgerAccountBalance =
        ledgerService.getSystemAccount(ledgerSystemAccount).getBalanceAt(bankBalanceTime);

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
