package ee.tuleva.onboarding.swedbank.reconcillation;

import static ee.tuleva.onboarding.swedbank.statement.BankStatementBalance.StatementBalanceType.CLOSE;

import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.swedbank.statement.BankStatement;
import java.time.LocalTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class Reconciliator {

  private LedgerService ledgerService;

  public void reconcile(BankStatement bankStatement) {
    checkBalanceMatch(bankStatement);
  }

  private void checkBalanceMatch(BankStatement bankStatement) {
    var closingBankBalance =
        bankStatement.getBalances().stream()
            .filter(balance -> balance.type().equals(CLOSE))
            .findFirst()
            .orElseThrow();

    var bankBalanceTime =
        closingBankBalance
            .time()
            .atStartOfDay(ZoneId.of("Estonia/Tallinn"))
            .with(LocalTime.MAX)
            .toInstant();

    var bankStatementAccount = bankStatement.getBankStatementAccount().getBankAccountType();

    var ledgerSystemAccount = bankStatementAccount.getLedgerAccount();
    var ledgerAccountBalance =
        ledgerService.getSystemAccount(ledgerSystemAccount).getBalanceAt(bankBalanceTime);

    if (ledgerAccountBalance.compareTo(closingBankBalance.balance()) != 0) {
      throw new IllegalStateException(
          "Bank account("
              + bankStatementAccount
              + ") closing balance="
              + closingBankBalance.balance()
              + "does not match ledger account("
              + ledgerSystemAccount
              + ") balance="
              + ledgerAccountBalance);
    }
  }
}
