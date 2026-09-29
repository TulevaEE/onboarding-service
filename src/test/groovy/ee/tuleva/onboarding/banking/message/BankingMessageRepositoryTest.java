package ee.tuleva.onboarding.banking.message;

import static ee.tuleva.onboarding.banking.BankType.SEB;
import static ee.tuleva.onboarding.banking.BankType.SWEDBANK;
import static ee.tuleva.onboarding.banking.message.BankMessageType.HISTORIC_STATEMENT;
import static ee.tuleva.onboarding.banking.message.BankMessageType.INTRA_DAY_REPORT;
import static ee.tuleva.onboarding.banking.message.BankingMessageRepositoryTest.Outcome.FAILED;
import static ee.tuleva.onboarding.banking.message.BankingMessageRepositoryTest.Outcome.PROCESSED;
import static ee.tuleva.onboarding.banking.message.BankingMessageRepositoryTest.Outcome.UNPROCESSED;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.banking.BankType;
import ee.tuleva.onboarding.banking.statement.StatementPeriod;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
class BankingMessageRepositoryTest {

  private static final String IBAN = "EE001234567890123456";
  private static final String OTHER_IBAN = "EE001234567890123457";
  private static final Instant PROCESSED_AT = Instant.parse("2026-09-14T01:00:33Z");

  @Autowired BankingMessageRepository repository;
  @Autowired JdbcClient jdbcClient;

  @Test
  void
      findStatements_returnsHistoricStatementsOfTheAccountOverlappingTheRangeWithProcessingState() {
    save(SEB, HISTORIC_STATEMENT, IBAN, period("2026-09-07", "2026-09-09"), PROCESSED_AT);
    save(SEB, HISTORIC_STATEMENT, IBAN, period("2026-09-08", "2026-09-10"), PROCESSED_AT);
    save(SEB, HISTORIC_STATEMENT, IBAN, period("2026-09-11", "2026-09-11"), PROCESSED_AT);
    save(SEB, HISTORIC_STATEMENT, IBAN, period("2026-09-13", "2026-09-13"), null);
    save(SEB, HISTORIC_STATEMENT, IBAN, period("2026-09-14", "2026-09-16"), PROCESSED_AT);
    save(SEB, HISTORIC_STATEMENT, IBAN, period("2026-09-15", "2026-09-17"), PROCESSED_AT);
    save(SEB, HISTORIC_STATEMENT, OTHER_IBAN, period("2026-09-12", "2026-09-12"), PROCESSED_AT);
    save(SEB, INTRA_DAY_REPORT, IBAN, period("2026-09-12", "2026-09-12"), PROCESSED_AT);
    save(SWEDBANK, HISTORIC_STATEMENT, IBAN, period("2026-09-12", "2026-09-12"), PROCESSED_AT);
    save(SEB, HISTORIC_STATEMENT, IBAN, null, null);

    var statements =
        repository.findStatements(
            SEB, HISTORIC_STATEMENT, IBAN, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 14));

    assertThat(statements)
        .containsExactlyInAnyOrder(
            new StoredStatement(LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 10), PROCESSED_AT),
            new StoredStatement(LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 11), PROCESSED_AT),
            new StoredStatement(LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 13), null),
            new StoredStatement(
                LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 16), PROCESSED_AT));
  }

  @Test
  void findEarliestStatementDate_returnsTheStartOfTheOldestHistoricStatementOfTheAccount() {
    save(SEB, HISTORIC_STATEMENT, IBAN, period("2026-09-11", "2026-09-13"), PROCESSED_AT);
    save(SEB, HISTORIC_STATEMENT, IBAN, period("2026-09-09", "2026-09-09"), null);
    save(SEB, INTRA_DAY_REPORT, IBAN, period("2026-09-01", "2026-09-01"), PROCESSED_AT);
    save(SEB, HISTORIC_STATEMENT, OTHER_IBAN, period("2026-09-02", "2026-09-02"), PROCESSED_AT);
    save(SEB, HISTORIC_STATEMENT, IBAN, null, null);

    assertThat(repository.findEarliestStatementDate(SEB, HISTORIC_STATEMENT, IBAN))
        .contains(LocalDate.of(2026, 9, 9));
  }

  @Test
  void findEarliestStatementDate_isEmptyWhenTheAccountHasNoStatementWithAPeriod() {
    save(SEB, HISTORIC_STATEMENT, IBAN, null, null);

    assertThat(repository.findEarliestStatementDate(SEB, HISTORIC_STATEMENT, IBAN)).isEmpty();
  }

  @Test
  void latestProcessedStatement_isTheLastOneTheMatcherHasSeenForTheAccount() {
    var fourPm = store(IBAN, INTRA_DAY_REPORT, "2026-09-23", "2026-09-23T13:00:05Z", PROCESSED);
    store(IBAN, INTRA_DAY_REPORT, "2026-09-23", "2026-09-23T12:30:05Z", PROCESSED);
    store(IBAN, INTRA_DAY_REPORT, null, "2026-09-23T13:30:05Z", UNPROCESSED);
    store(IBAN, INTRA_DAY_REPORT, "2026-09-23", "2026-09-23T13:35:05Z", FAILED);
    store(OTHER_IBAN, INTRA_DAY_REPORT, "2026-09-23", "2026-09-23T13:40:05Z", PROCESSED);

    assertThat(repository.findLatestProcessedStatement(IBAN))
        .hasValueSatisfying(message -> assertThat(message.getId()).isEqualTo(fourPm));
  }

  @Test
  void latestProcessedStatement_isTodaysReportRatherThanACatchUpOfEarlierDaysFetchedAfterIt() {
    var today = store(IBAN, INTRA_DAY_REPORT, "2026-09-23", "2026-09-23T13:00:05Z", PROCESSED);
    store(IBAN, HISTORIC_STATEMENT, "2026-09-22", "2026-09-23T13:05:05Z", PROCESSED);

    assertThat(repository.findLatestProcessedStatement(IBAN))
        .hasValueSatisfying(message -> assertThat(message.getId()).isEqualTo(today));
  }

  @Test
  void latestProcessedStatement_isAbsentUntilOneHasBeenProcessed() {
    store(IBAN, INTRA_DAY_REPORT, null, "2026-09-23T13:00:05Z", UNPROCESSED);

    assertThat(repository.findLatestProcessedStatement(IBAN)).isEmpty();
  }

  enum Outcome {
    PROCESSED,
    UNPROCESSED,
    FAILED
  }

  private UUID store(
      String iban,
      BankMessageType messageType,
      @Nullable String statementDate,
      String receivedAt,
      Outcome outcome) {
    var id = UUID.randomUUID();
    var received = Timestamp.from(Instant.parse(receivedAt));
    jdbcClient
        .sql(
            """
            insert into banking_message (id, bank_type, request_id, tracking_id, raw_response,
              timezone, message_type, account_iban, statement_from, statement_to, processed_at,
              failed_at, received_at)
            values (:id, 'SEB', 'request', 'tracking', '<Document/>', 'Europe/Tallinn',
              :messageType, :iban, :statementDate, :statementDate, :processedAt, :failedAt,
              :receivedAt)
            """)
        .param("id", id)
        .param("messageType", messageType.name())
        .param("iban", iban)
        .param("statementDate", statementDate == null ? null : LocalDate.parse(statementDate))
        .param("processedAt", outcome == PROCESSED ? received : null)
        .param("failedAt", outcome == FAILED ? received : null)
        .param("receivedAt", received)
        .update();
    return id;
  }

  private void save(
      BankType bankType,
      BankMessageType messageType,
      String iban,
      @Nullable StatementPeriod period,
      @Nullable Instant processedAt) {
    var id = UUID.randomUUID().toString();
    repository.save(
        BankingMessage.builder()
            .bankType(bankType)
            .messageType(messageType)
            .accountIban(iban)
            .statementFrom(period == null ? null : period.from())
            .statementTo(period == null ? null : period.to())
            .processedAt(processedAt)
            .requestId(id)
            .trackingId(id)
            .rawResponse("<Document/>")
            .timezone("Europe/Tallinn")
            .build());
  }

  private static StatementPeriod period(String from, String to) {
    return new StatementPeriod(LocalDate.parse(from), LocalDate.parse(to));
  }
}
