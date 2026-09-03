package ee.tuleva.onboarding.banking.seb.reconciliation;

import static ee.tuleva.onboarding.banking.statement.BankStatementBalance.StatementBalanceType.CLOSE;

import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.statement.BankStatement;
import ee.tuleva.onboarding.ledger.FundBankLedger;
import ee.tuleva.onboarding.ledger.LedgerService;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
public class SebReconciliator {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private final LedgerService ledgerService;
  private final BankAccounts bankAccounts;
  private final FundBankLedger fundBankLedger;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public void reconcile(BankStatement bankStatement) {
    var closingBankBalance =
        bankStatement.getBalances().stream()
            .filter(balance -> CLOSE.equals(balance.type()))
            .findFirst()
            .orElseThrow();

    var reconciliationTime =
        closingBankBalance.time().plusDays(1).atStartOfDay(ESTONIAN_ZONE).toInstant();

    var iban = bankStatement.getBankStatementAccount().iban();
    var account =
        bankAccounts
            .find(iban)
            .orElseThrow(
                () -> new IllegalStateException("Unknown bank account: iban=%s".formatted(iban)));

    var ledgerAccountBalance =
        ledgerService
            .getSystemAccount(account.ledgerAccount(), account.fund())
            .getBalanceAt(reconciliationTime);

    if (ledgerAccountBalance.compareTo(closingBankBalance.balance()) != 0) {
      eventPublisher.publishEvent(
          new ReconciliationCompletedEvent(
              account, closingBankBalance.balance(), ledgerAccountBalance, false));

      var diff = ledgerAccountBalance.subtract(closingBankBalance.balance());
      throw new IllegalStateException(
          "Bank statement reconciliation failed: bankAccount=%s, closingBalance=%s, ledgerAccount=%s, ledgerBalance=%s, diff=%s"
              .formatted(
                  account,
                  closingBankBalance.balance(),
                  account.ledgerAccount(),
                  ledgerAccountBalance,
                  diff));
    }

    var unresolvedUnclassifiedEntries =
        fundBankLedger.countUnresolvedUnclassifiedEntries(account.fund());
    if (unresolvedUnclassifiedEntries > 0) {
      throw new IllegalStateException(
          "Unresolved unclassified bank entries: fund=%s, count=%d"
              .formatted(account.fund(), unresolvedUnclassifiedEntries));
    }

    eventPublisher.publishEvent(
        new ReconciliationCompletedEvent(
            account, closingBankBalance.balance(), ledgerAccountBalance, true));

    log.info(
        "Reconciliation successful: bankAccount={}, balance={}, ledgerAccount={}",
        account,
        closingBankBalance.balance(),
        account.ledgerAccount());
  }
}
