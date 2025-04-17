package ee.tuleva.onboarding.analytics.transaction.exchange;

import static ee.tuleva.onboarding.analytics.transaction.exchange.ExchangeTransactionFixture.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class ExchangeTransactionRepositoryTest {

  @Autowired ExchangeTransactionRepository repository;

  @Autowired DataSource dataSource;

  private static final LocalDateTime FIXED_TEST_TIME =
      LocalDateTime.of(2025, Month.FEBRUARY, 15, 10, 00);

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
    LocalDate dateToDelete = LocalDate.of(2025, 1, 15);
    LocalDate dateToKeep = LocalDate.of(2025, 1, 16);

    ExchangeTransaction tx1 =
        exampleTransactionBuilderWithFixedTime().reportingDate(dateToDelete).code("DEL1").build();
    ExchangeTransaction tx2 =
        exampleTransactionBuilderWithFixedTime()
            .reportingDate(dateToDelete)
            .code("DEL2")
            .dateCreated(FIXED_TEST_TIME.plusSeconds(1))
            .build();
    ExchangeTransaction tx3 =
        exampleTransactionBuilderWithFixedTime()
            .reportingDate(dateToKeep)
            .code("KEEP1")
            .dateCreated(FIXED_TEST_TIME.plusSeconds(2))
            .build();

    repository.saveAll(List.of(tx1, tx2, tx3));
    assertThat(repository.count()).isEqualTo(3);

    int deletedCount = repository.deleteByReportingDate(dateToDelete);

    assertThat(deletedCount).isEqualTo(2);
    assertThat(repository.count()).isEqualTo(1);
    List<ExchangeTransaction> remaining = repository.findAll();
    assertThat(remaining).hasSize(1);
    assertThat(remaining.get(0).getCode()).isEqualTo("KEEP1");
    assertThat(remaining.get(0).getReportingDate()).isEqualTo(dateToKeep);
  }

  @Test
  @DisplayName("deleteByReportingDate returns 0 and deletes nothing when no records match the date")
  void deleteByReportingDate_deletesNothing_whenNoMatchingDate() {
    LocalDate datePresent = LocalDate.of(2025, 1, 15);
    LocalDate dateToDelete = LocalDate.of(2025, 1, 16);

    ExchangeTransaction tx1 =
        exampleTransactionBuilderWithFixedTime().reportingDate(datePresent).code("KEEP1").build();
    repository.save(tx1);
    assertThat(repository.count()).isEqualTo(1);

    int deletedCount = repository.deleteByReportingDate(dateToDelete);

    assertThat(deletedCount).isEqualTo(0);
    assertThat(repository.count()).isEqualTo(1);
    assertThat(repository.findAll().get(0).getCode()).isEqualTo("KEEP1");
  }

  @Test
  @DisplayName("deleteByReportingDate returns 0 when the table is empty")
  void deleteByReportingDate_deletesNothing_whenTableIsEmpty() {
    LocalDate dateToDelete = LocalDate.of(2025, 1, 15);
    assertThat(repository.count()).isEqualTo(0);

    int deletedCount = repository.deleteByReportingDate(dateToDelete);

    assertThat(deletedCount).isEqualTo(0);
    assertThat(repository.count()).isEqualTo(0);
  }
}
