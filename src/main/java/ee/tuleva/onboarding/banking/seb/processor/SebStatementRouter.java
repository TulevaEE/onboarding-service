package ee.tuleva.onboarding.banking.seb.processor;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;

import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.SavingsFundStatementReceived;
import ee.tuleva.onboarding.banking.processor.BankOperationProcessor;
import ee.tuleva.onboarding.banking.statement.BankStatement;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;

@RequiredArgsConstructor
public class SebStatementRouter {

  private final BankAccounts bankAccounts;
  private final ApplicationEventPublisher eventPublisher;
  private final BankOperationProcessor bankOperationProcessor;
  private final PensionFundStatementProcessor pensionFundStatementProcessor;

  public void route(BankStatement statement) {
    var iban = statement.getBankStatementAccount().iban();
    var account =
        bankAccounts
            .find(iban)
            .orElseThrow(
                () -> new IllegalStateException("Unknown bank account: iban=%s".formatted(iban)));

    if (account.belongsTo(TKF100)) {
      eventPublisher.publishEvent(new SavingsFundStatementReceived(statement, account));
      statement.getEntries().stream()
          .filter(entry -> entry.details() == null)
          .forEach(entry -> bankOperationProcessor.processBankOperation(entry, account));
    } else {
      pensionFundStatementProcessor.process(statement, account);
    }
  }
}
