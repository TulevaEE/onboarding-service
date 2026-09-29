package ee.tuleva.onboarding.banking.message;

import static ee.tuleva.onboarding.banking.message.BankMessageType.HISTORIC_STATEMENT;

import ee.tuleva.onboarding.banking.statement.BankStatement;
import ee.tuleva.onboarding.banking.statement.BankStatementExtractor;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BookedBalanceReader {

  private final BankingMessageRepository bankingMessageRepository;
  private final BankStatementExtractor bankStatementExtractor;

  public Optional<BookedBalance> latest(String iban) {
    return bankingMessageRepository
        .findLatestProcessedStatement(iban)
        .flatMap(
            message ->
                statementOf(message)
                    .bookedBalance()
                    .map(amount -> new BookedBalance(amount, message.getReceivedAt())));
  }

  private BankStatement statementOf(BankingMessage message) {
    return message.getMessageType() == HISTORIC_STATEMENT
        ? bankStatementExtractor.extractFromHistoricStatement(
            message.getRawResponse(), message.getTimezoneId())
        : bankStatementExtractor.extractFromIntraDayReport(
            message.getRawResponse(), message.getTimezoneId());
  }
}
