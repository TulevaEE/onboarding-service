package ee.tuleva.onboarding.analytics.transaction.exchange;

import static ee.tuleva.onboarding.analytics.transaction.exchange.ExchangeTransactionFixture.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class ExchangeTransactionRepositoryTest {

  @Autowired ExchangeTransactionRepository repository;

  @Autowired DataSource dataSource;

  private static final LocalDateTime FIXED_TEST_TIME =
      LocalDateTime.of(2025, Month.FEBRUARY, 15, 10, 00);
  private static final LocalDate DATE_2025_01_15 = LocalDate.of(2025, 1, 15);
  private static final LocalDate DATE_2025_01_16 = LocalDate.of(2025, 1, 16);
  private static final LocalDate DATE_2025_01_17 = LocalDate.of(2025, 1, 17);

  private static final String CREATE_PUBLIC_SCHEMA = "CREATE SCHEMA IF NOT EXISTS public";
  private static final String CREATE_EXCHANGE_TRANSACTIONS_TABLE =
      "CREATE TABLE IF NOT EXISTS public.exchange_transaction ("
          + "id BIGSERIAL PRIMARY KEY,"
          + "reporting_date DATE NOT NULL,"
          + "security_from TEXT NOT NULL,"
          + "security_to TEXT NOT NULL,"
          + "fund_manager_from VARCHAR(255),"
          + "fund_manager_to VARCHAR(255),"
          + "code TEXT NOT NULL,"
          + "first_name TEXT NOT NULL,"
          + "name TEXT NOT NULL,"
          + "percentage NUMERIC NOT NULL,"
          + "unit_amount NUMERIC NOT NULL,"
          + "date_created TIMESTAMP NOT NULL"
          + ");";

  private static final String TRUNCATE_EXCHANGE_TRANSACTIONS_TABLE =
      "TRUNCATE TABLE public.exchange_transaction";

  @BeforeAll
  static void createSchema(@Autowired DataSource dataSource) throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(CREATE_PUBLIC_SCHEMA);
      stmt.execute(CREATE_EXCHANGE_TRANSACTIONS_TABLE);
    }
  }

  @BeforeEach
  void cleanUp() throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(TRUNCATE_EXCHANGE_TRANSACTIONS_TABLE);
    }
  }

  private ExchangeTransaction.ExchangeTransactionBuilder exampleTransactionBuilderWithFixedTime() {
    return exampleTransactionBuilder().dateCreated(FIXED_TEST_TIME);
  }

  @Test
  @DisplayName("findAll returns all inserted records")
  void testFindAll() {
    ExchangeTransaction tx1 = exampleTransactionBuilder().dateCreated(FIXED_TEST_TIME).build();
    ExchangeTransaction tx2 =
        anotherExampleTransactionBuilder().dateCreated(FIXED_TEST_TIME.plusHours(1)).build();

    repository.saveAll(List.of(tx1, tx2));

    var all = repository.findAll();
    assertThat(all).hasSize(2);
    assertThat(all)
        .extracting(ExchangeTransaction::getCode)
        .containsExactlyInAnyOrder(tx1.getCode(), tx2.getCode());
  }

  @Test
  @DisplayName("deleteByReportingDate deletes only records with the specified date")
  void deleteByReportingDate_deletesMatchingRecords() {
    ExchangeTransaction tx1 =
        exampleTransactionBuilderWithFixedTime()
            .reportingDate(DATE_2025_01_15)
            .code("DEL1")
            .build();
    ExchangeTransaction tx2 =
        exampleTransactionBuilderWithFixedTime()
            .reportingDate(DATE_2025_01_15)
            .code("DEL2")
            .dateCreated(FIXED_TEST_TIME.plusSeconds(1))
            .build();
    ExchangeTransaction tx3 =
        exampleTransactionBuilderWithFixedTime()
            .reportingDate(DATE_2025_01_16)
            .code("KEEP1")
            .dateCreated(FIXED_TEST_TIME.plusSeconds(2))
            .build();

    repository.saveAll(List.of(tx1, tx2, tx3));
    assertThat(repository.count()).isEqualTo(3);

    int deletedCount = repository.deleteByReportingDate(DATE_2025_01_15);

    assertThat(deletedCount).isEqualTo(2);
    assertThat(repository.count()).isEqualTo(1);
    List<ExchangeTransaction> remaining = repository.findAll();
    assertThat(remaining).hasSize(1);
    assertThat(remaining.get(0).getCode()).isEqualTo("KEEP1");
    assertThat(remaining.get(0).getReportingDate()).isEqualTo(DATE_2025_01_16);
  }

  @Test
  @DisplayName("deleteByReportingDate returns 0 and deletes nothing when no records match the date")
  void deleteByReportingDate_deletesNothing_whenNoMatchingDate() {
    ExchangeTransaction tx1 =
        exampleTransactionBuilderWithFixedTime()
            .reportingDate(DATE_2025_01_15)
            .code("KEEP1")
            .build();
    repository.save(tx1);
    assertThat(repository.count()).isEqualTo(1);

    int deletedCount = repository.deleteByReportingDate(DATE_2025_01_16);

    assertThat(deletedCount).isEqualTo(0);
    assertThat(repository.count()).isEqualTo(1);
    assertThat(repository.findAll().get(0).getCode()).isEqualTo("KEEP1");
  }

  @Test
  @DisplayName("deleteByReportingDate returns 0 when the table is empty")
  void deleteByReportingDate_deletesNothing_whenTableIsEmpty() {
    assertThat(repository.count()).isEqualTo(0);

    int deletedCount = repository.deleteByReportingDate(DATE_2025_01_15);

    assertThat(deletedCount).isEqualTo(0);
    assertThat(repository.count()).isEqualTo(0);
  }

  @Test
  @DisplayName(
      "findTopByOrderByReportingDateDesc returns the transaction with the latest reporting date")
  void findTopByOrderByReportingDateDesc_returnsLatest() {
    ExchangeTransaction txOldest =
        exampleTransactionBuilderWithFixedTime().reportingDate(DATE_2025_01_15).code("OLD").build();
    ExchangeTransaction txMiddle =
        exampleTransactionBuilderWithFixedTime()
            .reportingDate(DATE_2025_01_16)
            .code("MID")
            .dateCreated(FIXED_TEST_TIME.plusHours(1))
            .build();
    ExchangeTransaction txLatest =
        exampleTransactionBuilderWithFixedTime()
            .reportingDate(DATE_2025_01_17)
            .code("LATEST")
            .dateCreated(FIXED_TEST_TIME.plusHours(2))
            .build();

    ExchangeTransaction txLatest2 =
        exampleTransactionBuilderWithFixedTime()
            .reportingDate(DATE_2025_01_17)
            .code("LATEST_ANOTHER")
            .dateCreated(FIXED_TEST_TIME.plusHours(3))
            .build();

    repository.saveAll(List.of(txOldest, txLatest, txMiddle, txLatest2));

    Optional<ExchangeTransaction> found = repository.findTopByOrderByReportingDateDesc();

    assertThat(found).isPresent();
    assertThat(found.get().getReportingDate()).isEqualTo(DATE_2025_01_17);

    assertThat(found.get().getCode()).isIn("LATEST", "LATEST_ANOTHER");
  }

  @Test
  @DisplayName("findTopByOrderByReportingDateDesc returns empty optional when repository is empty")
  void findTopByOrderByReportingDateDesc_returnsEmpty_whenEmpty() {
    Optional<ExchangeTransaction> found = repository.findTopByOrderByReportingDateDesc();
    assertThat(found).isNotPresent();
  }

  @Test
  @DisplayName("findByReportingDate returns all transactions for the specified date")
  void findByReportingDate_returnsMatchingTransactions() {
    ExchangeTransaction txMatch1 =
        exampleTransactionBuilderWithFixedTime()
            .reportingDate(DATE_2025_01_16)
            .code("MATCH1")
            .build();
    ExchangeTransaction txMatch2 =
        exampleTransactionBuilderWithFixedTime()
            .reportingDate(DATE_2025_01_16)
            .code("MATCH2")
            .dateCreated(FIXED_TEST_TIME.plusHours(1))
            .build();
    ExchangeTransaction txNoMatch =
        exampleTransactionBuilderWithFixedTime()
            .reportingDate(DATE_2025_01_17)
            .code("NOMATCH")
            .dateCreated(FIXED_TEST_TIME.plusHours(2))
            .build();

    repository.saveAll(List.of(txMatch1, txNoMatch, txMatch2));

    List<ExchangeTransaction> found = repository.findByReportingDate(DATE_2025_01_16);

    assertThat(found).hasSize(2);
    assertThat(found)
        .extracting(ExchangeTransaction::getCode)
        .containsExactlyInAnyOrder("MATCH1", "MATCH2");
    assertThat(found)
        .allSatisfy(tx -> assertThat(tx.getReportingDate()).isEqualTo(DATE_2025_01_16));
  }

  @Test
  @DisplayName("findByReportingDate returns empty list when no transactions match the date")
  void findByReportingDate_returnsEmptyList_whenNoMatches() {
    ExchangeTransaction txNoMatch =
        exampleTransactionBuilderWithFixedTime()
            .reportingDate(DATE_2025_01_17)
            .code("NOMATCH")
            .build();
    repository.save(txNoMatch);

    List<ExchangeTransaction> found = repository.findByReportingDate(DATE_2025_01_16);

    assertThat(found).isEmpty();
  }

  @Test
  @DisplayName("findByReportingDate returns empty list when repository is empty")
  void findByReportingDate_returnsEmptyList_whenRepositoryIsEmpty() {
    List<ExchangeTransaction> found = repository.findByReportingDate(DATE_2025_01_16);
    assertThat(found).isEmpty();
  }
}
