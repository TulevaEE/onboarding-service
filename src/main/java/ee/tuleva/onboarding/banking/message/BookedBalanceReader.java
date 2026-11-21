package ee.tuleva.onboarding.banking.message;

import static ee.tuleva.onboarding.banking.BankType.SEB;
import static ee.tuleva.onboarding.banking.message.BankMessageType.HISTORIC_STATEMENT;
import static ee.tuleva.onboarding.banking.statement.TransactionType.CREDIT;
import static java.util.stream.Collectors.toUnmodifiableSet;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.BankEntryReference;
import ee.tuleva.onboarding.banking.BankStatementBalances;
import ee.tuleva.onboarding.banking.CurrentDayReport;
import ee.tuleva.onboarding.banking.CurrentDayReport.Booked;
import ee.tuleva.onboarding.banking.CurrentDayReport.Missing;
import ee.tuleva.onboarding.banking.CurrentDayReport.NoBookedBalance;
import ee.tuleva.onboarding.banking.CurrentDayReport.Unreadable;
import ee.tuleva.onboarding.banking.statement.BankStatement;
import ee.tuleva.onboarding.banking.statement.BankStatementEntry;
import ee.tuleva.onboarding.banking.statement.BankStatementExtractor;
import ee.tuleva.onboarding.banking.statement.BankStatementParseException;
import ee.tuleva.onboarding.banking.statement.DayClosingBalance;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
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
        .flatMap(message -> statementOf(message).bookedBalance())
        .or(
            () ->
                catchUpStatementCovering(account, day)
                    .flatMap(
                        message ->
                            DayClosingBalance.on(
                                statementOf(message), day, message.getTimezoneId())));
  }

  @Override
  public CurrentDayReport currentDayReport(BankAccount account, LocalDate day) {
    List<BankingMessage> reports =
        bankingMessageRepository.findProcessedCurrentDayReportsLatestFirst(
            SEB, account.iban(), day);
    if (reports.isEmpty()) {
      return new Missing();
    }
    return bookedBalanceOf(reports.getFirst(), reports, account);
  }

  private CurrentDayReport bookedBalanceOf(
      BankingMessage message, List<BankingMessage> reportsUpToIt, BankAccount account) {
    try {
      var statement = statementOf(message);
      return statement
          .bookedBalance()
          .<CurrentDayReport>map(
              balance ->
                  new Booked(
                      balance,
                      message.getReceivedAt(),
                      pendingEntryReferences(statement, account.iban()),
                      bookedEntryReferences(reportsUpToIt, account.iban())))
          .orElseGet(NoBookedBalance::new);
    } catch (BankStatementParseException e) {
      log.error("Stored current-day report cannot be read: messageId={}", message.getId(), e);
      return new Unreadable();
    }
  }

  @Override
  public boolean isInsideAStatementClosingLater(BankAccount account, LocalDate day) {
    return catchUpStatementCovering(account, day).isPresent();
  }

  private Optional<BankingMessage> catchUpStatementCovering(BankAccount account, LocalDate day) {
    return bankingMessageRepository.findLatestProcessedStatementCoveringAndClosingAfter(
        SEB, account.iban(), day);
  }

  private Set<UUID> bookedEntryReferences(List<BankingMessage> reports, String iban) {
    return reports.stream()
        .flatMap(report -> readableEntries(report).stream())
        .filter(BankStatementEntry::booked)
        .map(entry -> BankEntryReference.of(iban, entry.externalId()))
        .collect(toUnmodifiableSet());
  }

  private List<BankStatementEntry> readableEntries(BankingMessage report) {
    try {
      return statementOf(report).getEntries();
    } catch (BankStatementParseException e) {
      log.error("Stored current-day report cannot be read: messageId={}", report.getId(), e);
      return List.of();
    }
  }

  private static Set<UUID> pendingEntryReferences(BankStatement statement, String iban) {
    return statement.getEntries().stream()
        .filter(entry -> !entry.booked())
        .map(entry -> BankEntryReference.of(iban, entry.externalId()))
        .collect(toUnmodifiableSet());
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
