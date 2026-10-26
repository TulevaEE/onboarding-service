package ee.tuleva.onboarding.banking.message;

import static ee.tuleva.onboarding.banking.BankType.SEB;
import static ee.tuleva.onboarding.banking.message.BankMessageType.HISTORIC_STATEMENT;
import static ee.tuleva.onboarding.banking.statement.TransactionType.CREDIT;
import static java.util.stream.Collectors.toUnmodifiableSet;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.BankStatementBalances;
import ee.tuleva.onboarding.banking.statement.BankStatement;
import ee.tuleva.onboarding.banking.statement.BankStatementEntry;
import ee.tuleva.onboarding.banking.statement.BankStatementExtractor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BookedBalanceReader implements BankStatementBalances {

  private final BankingMessageRepository bankingMessageRepository;
  private final BankStatementExtractor bankStatementExtractor;

  public Optional<BookedBalance> latest(String iban) {
    return bankingMessageRepository
        .findLatestProcessedStatement(iban)
        .flatMap(
            message -> {
              var statement = statementOf(message);
              return statement
                  .bookedBalance()
                  .map(
                      amount ->
                          new BookedBalance(
                              amount, message.getReceivedAt(), creditedEndToEndIds(statement)));
            });
  }

  @Override
  public Optional<BigDecimal> closingBalance(BankAccount account, LocalDate day) {
    return bankingMessageRepository
        .findLatestProcessedStatementEndingOn(SEB, account.iban(), day)
        .flatMap(message -> statementOf(message).bookedBalance());
  }

  private static Set<String> creditedEndToEndIds(BankStatement statement) {
    return statement.getEntries().stream()
        .filter(entry -> entry.transactionType() == CREDIT)
        .map(BankStatementEntry::endToEndId)
        .filter(Objects::nonNull)
        .collect(toUnmodifiableSet());
  }

  private BankStatement statementOf(BankingMessage message) {
    return message.getMessageType() == HISTORIC_STATEMENT
        ? bankStatementExtractor.extractFromHistoricStatement(
            message.getRawResponse(), message.getTimezoneId())
        : bankStatementExtractor.extractFromIntraDayReport(
            message.getRawResponse(), message.getTimezoneId());
  }
}
