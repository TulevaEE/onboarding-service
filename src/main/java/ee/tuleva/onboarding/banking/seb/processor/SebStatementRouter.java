package ee.tuleva.onboarding.banking.seb.processor;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;

import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.statement.BankStatement;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SebStatementRouter {

  private final BankAccounts bankAccounts;
  private final SavingsFundStatementProcessor savingsFundStatementProcessor;
  private final PensionFundStatementProcessor pensionFundStatementProcessor;

  public void route(BankStatement statement) {
    var iban = statement.getBankStatementAccount().iban();
    var account =
        bankAccounts
            .find(iban)
            .orElseThrow(
                () -> new IllegalStateException("Unknown bank account: iban=%s".formatted(iban)));

    if (account.belongsTo(TKF100)) {
      savingsFundStatementProcessor.process(statement, account);
    } else {
      pensionFundStatementProcessor.process(statement, account);
    }
  }
}
