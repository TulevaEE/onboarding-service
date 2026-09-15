package ee.tuleva.onboarding.banking.seb.fetcher;

import static ee.tuleva.onboarding.banking.BankAccountType.DEPOSIT_EUR;
import static ee.tuleva.onboarding.banking.BankType.SEB;
import static ee.tuleva.onboarding.banking.message.BankMessageType.HISTORIC_STATEMENT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.message.BankingMessageRepository;
import ee.tuleva.onboarding.banking.message.StoredStatement;
import ee.tuleva.onboarding.banking.statement.StatementPeriod;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatementCoverageTest {

  private static final BankAccount ACCOUNT =
      new BankAccount("EE001234567890123456", DEPOSIT_EUR, TKF100, "gw-test");
  private static final Instant PROCESSED_AT = Instant.parse("2026-09-14T01:00:33Z");

  @Mock private BankingMessageRepository bankingMessageRepository;

  @InjectMocks private StatementCoverage statementCoverage;

  @Test
  void missingPeriods_groupsTheUnreceivedDatesIntoContiguousPeriods() {
    givenEarliestStatementDate(date(1));
    givenStatements(date(7), date(14), processed(8, 8), processed(10, 10), processed(13, 13));

    var missing = statementCoverage.missingPeriods(ACCOUNT, date(7), date(14));

    assertThat(missing).containsExactly(period(7, 7), period(9, 9), period(11, 12), period(14, 14));
  }

  @Test
  void missingPeriods_treatsAStatementThatFailedProcessingAsReceived() {
    givenEarliestStatementDate(date(1));
    givenStatements(date(11), date(12), failed(11, 12));

    var missing = statementCoverage.missingPeriods(ACCOUNT, date(11), date(12));

    assertThat(missing).isEmpty();
  }

  @Test
  void unprocessedPeriods_reportsTheDatesOfStatementsThatFailedProcessing() {
    givenEarliestStatementDate(date(1));
    givenStatements(date(10), date(13), processed(10, 10), failed(11, 12), processed(13, 13));

    var unprocessed = statementCoverage.unprocessedPeriods(ACCOUNT, date(10), date(13));

    assertThat(unprocessed).containsExactly(period(11, 12));
  }

  @Test
  void missingPeriods_ignoresDatesBeforeTheFirstStatementEverReceived() {
    givenEarliestStatementDate(date(10));
    givenStatements(date(10), date(14), processed(10, 10), processed(12, 14));

    var missing = statementCoverage.missingPeriods(ACCOUNT, date(7), date(14));

    assertThat(missing).containsExactly(period(11, 11));
  }

  @Test
  void unprocessedPeriods_ignoresDatesBeforeTheFirstStatementEverReceived() {
    givenEarliestStatementDate(date(10));
    givenStatements(date(10), date(14), processed(10, 14));

    var unprocessed = statementCoverage.unprocessedPeriods(ACCOUNT, date(7), date(14));

    assertThat(unprocessed).isEmpty();
  }

  @Test
  void missingPeriods_isEmptyWhenTheFirstStatementEverReceivedIsAfterTheWindow() {
    givenEarliestStatementDate(date(20));

    var missing = statementCoverage.missingPeriods(ACCOUNT, date(7), date(14));

    assertThat(missing).isEmpty();
  }

  @Test
  void missingPeriods_isEmptyWhenNoStatementHasBeenReceivedYet() {
    given(
            bankingMessageRepository.findEarliestStatementDate(
                SEB, HISTORIC_STATEMENT, ACCOUNT.iban()))
        .willReturn(Optional.empty());

    var missing = statementCoverage.missingPeriods(ACCOUNT, date(7), date(14));

    assertThat(missing).isEmpty();
  }

  @Test
  void unprocessedPeriods_reportsTheLastDayWhenNoStatementHasEverBeenReceived() {
    given(
            bankingMessageRepository.findEarliestStatementDate(
                SEB, HISTORIC_STATEMENT, ACCOUNT.iban()))
        .willReturn(Optional.empty());
    givenStatements(date(14), date(14));

    var unprocessed = statementCoverage.unprocessedPeriods(ACCOUNT, date(7), date(14));

    assertThat(unprocessed).containsExactly(period(14, 14));
  }

  @Test
  void isReceived_isTrueWhenAStoredStatementCoversTheDateEvenIfProcessingFailed() {
    givenStatements(date(13), date(13), failed(11, 13));

    assertThat(statementCoverage.isReceived(ACCOUNT, date(13))).isTrue();
  }

  @Test
  void isReceived_isFalseWhenNoStoredStatementCoversTheDate() {
    givenStatements(date(12), date(12));

    assertThat(statementCoverage.isReceived(ACCOUNT, date(12))).isFalse();
  }

  private void givenEarliestStatementDate(LocalDate earliest) {
    given(
            bankingMessageRepository.findEarliestStatementDate(
                SEB, HISTORIC_STATEMENT, ACCOUNT.iban()))
        .willReturn(Optional.of(earliest));
  }

  private void givenStatements(LocalDate from, LocalDate to, StoredStatement... statements) {
    given(
            bankingMessageRepository.findStatements(
                SEB, HISTORIC_STATEMENT, ACCOUNT.iban(), from, to))
        .willReturn(List.of(statements));
  }

  private static StoredStatement processed(int fromDay, int toDay) {
    return new StoredStatement(date(fromDay), date(toDay), PROCESSED_AT);
  }

  private static StoredStatement failed(int fromDay, int toDay) {
    return new StoredStatement(date(fromDay), date(toDay), null);
  }

  private static LocalDate date(int dayOfSeptember2026) {
    return LocalDate.of(2026, 9, dayOfSeptember2026);
  }

  private static StatementPeriod period(int fromDay, int toDay) {
    return new StatementPeriod(date(fromDay), date(toDay));
  }
}
