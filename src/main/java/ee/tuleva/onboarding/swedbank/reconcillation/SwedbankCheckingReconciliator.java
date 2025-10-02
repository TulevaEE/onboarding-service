package ee.tuleva.onboarding.swedbank.reconcillation;

import static ee.tuleva.onboarding.swedbank.statement.BankStatementBalance.StatementBalanceType.CLOSE;

import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.swedbank.statement.SwedbankBankStatementExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SwedbankCheckingReconciliator {
  private final SwedbankBankStatementExtractor swedbankBankStatementExtractor;
  private final SavingsFundLedger savingsFundLedger;

  public void processMessage(String rawResponse) {
    var response = this.swedbankBankStatementExtractor.extractFromHistoricStatement(rawResponse);

    var closingBalance =
        response.getBalances().stream()
            .filter(balance -> balance.type().equals(CLOSE))
            .findFirst()
            .orElseThrow();

    // var ledger = this.savingsFundLedger.getCashReconciliation(closingBalance.time())
    // TODO reconciliation and throw if no match
  }
}
