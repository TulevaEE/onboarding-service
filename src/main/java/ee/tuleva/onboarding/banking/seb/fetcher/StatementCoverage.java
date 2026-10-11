package ee.tuleva.onboarding.banking.seb.fetcher;

import static ee.tuleva.onboarding.banking.BankType.SEB;
import static ee.tuleva.onboarding.banking.message.BankMessageType.HISTORIC_STATEMENT;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.message.BankingMessageRepository;
import ee.tuleva.onboarding.banking.message.StoredStatement;
import ee.tuleva.onboarding.banking.statement.StatementPeriod;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class StatementCoverage {

  private final BankingMessageRepository bankingMessageRepository;

  public boolean isReceived(BankAccount account, LocalDate date) {
    return storedStatements(account, date, date).stream()
        .anyMatch(statement -> statement.period().covers(date));
  }

  public List<StatementPeriod> missingPeriods(BankAccount account, LocalDate from, LocalDate to) {
    return trackedFrom(account, from)
        .map(start -> uncoveredPeriods(account, start, to, statement -> true))
        .orElse(List.of());
  }

  public List<StatementPeriod> unprocessedPeriods(
      BankAccount account, LocalDate from, LocalDate to) {
    var start = trackedFrom(account, from).orElse(to);
    return uncoveredPeriods(account, start, to, StoredStatement::isProcessed);
  }

  private Optional<LocalDate> trackedFrom(BankAccount account, LocalDate from) {
    return bankingMessageRepository
        .findEarliestStatementDate(SEB, HISTORIC_STATEMENT, account.iban())
        .map(earliest -> earliest.isAfter(from) ? earliest : from);
  }

  private List<StatementPeriod> uncoveredPeriods(
      BankAccount account, LocalDate from, LocalDate to, Predicate<StoredStatement> counts) {
    if (from.isAfter(to)) {
      return List.of();
    }
    var stored = storedStatements(account, from, to).stream().filter(counts).toList();
    var uncoveredDates =
        from.datesUntil(to.plusDays(1))
            .filter(date -> stored.stream().noneMatch(statement -> statement.period().covers(date)))
            .toList();
    return contiguousPeriods(uncoveredDates);
  }

  private List<StoredStatement> storedStatements(
      BankAccount account, LocalDate from, LocalDate to) {
    return bankingMessageRepository.findStatements(
        SEB, HISTORIC_STATEMENT, account.iban(), from, to);
  }

  private static List<StatementPeriod> contiguousPeriods(List<LocalDate> dates) {
    var periods = new ArrayList<StatementPeriod>();
    for (LocalDate date : dates) {
      if (!periods.isEmpty() && periods.getLast().to().plusDays(1).equals(date)) {
        periods.set(periods.size() - 1, new StatementPeriod(periods.getLast().from(), date));
      } else {
        periods.add(new StatementPeriod(date, date));
      }
    }
    return List.copyOf(periods);
  }
}
