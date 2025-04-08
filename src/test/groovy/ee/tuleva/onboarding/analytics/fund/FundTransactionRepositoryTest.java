package ee.tuleva.onboarding.analytics.fund;

import static ee.tuleva.onboarding.analytics.fund.FundTransactionFixture.anotherExampleTransactionBuilder;
import static ee.tuleva.onboarding.analytics.fund.FundTransactionFixture.exampleTransactionBuilder;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.time.TestClockHolder;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
class FundTransactionRepositoryTest {

  @Autowired FundTransactionRepository repository;

  @Autowired DataSource dataSource;

  private static final String TARGET_FUND_ISIN = "EE1111111111";
  private static final String OTHER_FUND_ISIN = "EE2222222222";
  private final LocalDateTime creationTime = LocalDateTime.now(TestClockHolder.clock);
  private static final LocalDate START_DATE = LocalDate.of(2025, 4, 1);
  private static final LocalDate END_DATE = LocalDate.of(2025, 4, 30);
  private static final LocalDate DATE_BEFORE = START_DATE.minusDays(1); // March 31
  private static final LocalDate DATE_START = START_DATE; // April 1
  private static final LocalDate DATE_MIDDLE = START_DATE.plusDays(10); // April 11
  private static final LocalDate DATE_END = END_DATE; // April 30
  private static final LocalDate DATE_AFTER = END_DATE.plusDays(1); // May 1

  private static final String CREATE_PUBLIC_SCHEMA = "CREATE SCHEMA IF NOT EXISTS public";
  private static final String CREATE_FUND_TRANSACTIONS_TABLE =
      "CREATE TABLE IF NOT EXISTS public.fund_transaction ("
          + "id BIGSERIAL PRIMARY KEY,"
          + "transaction_date DATE NOT NULL,"
          + "isin VARCHAR(255),"
          + "person_name VARCHAR(255),"
          + "personal_id VARCHAR(255) NOT NULL,"
          + "pension_account VARCHAR(255),"
          + "country VARCHAR(255),"
          + "transaction_type VARCHAR(255) NOT NULL,"
          + "purpose VARCHAR(255),"
          + "application_type VARCHAR(255),"
          + "unit_amount NUMERIC(19, 8) NOT NULL,"
          + "price NUMERIC(19, 8),"
          + "nav NUMERIC(19, 8),"
          + "amount NUMERIC(19, 2) NOT NULL,"
          + "service_fee NUMERIC(19, 2),"
          + "date_created TIMESTAMP NOT NULL"
          + ");";
  private static final String TRUNCATE_FUND_TRANSACTIONS_TABLE =
      "TRUNCATE TABLE public.fund_transaction RESTART IDENTITY";

  @BeforeAll
  static void createSchema(@Autowired DataSource dataSource) throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(CREATE_PUBLIC_SCHEMA);
      stmt.execute(CREATE_FUND_TRANSACTIONS_TABLE);
    }
  }

  @BeforeEach
  void cleanUp() throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(TRUNCATE_FUND_TRANSACTIONS_TABLE);
    }
  }

  @Test
  @DisplayName("findLatestTransactionDate returns max date when data exists")
  void findLatestTransactionDate_returnsMaxDate() {
    LocalDate date1 = LocalDate.of(2025, 3, 15);
    LocalDate date2 = LocalDate.of(2025, 4, 1);
    LocalDate date3 = LocalDate.of(2025, 3, 30);

    FundTransaction tx1 =
        exampleTransactionBuilder(TARGET_FUND_ISIN, creationTime)
            .transactionDate(date1)
            .personalId("P100")
            .build();
    FundTransaction tx2 =
        anotherExampleTransactionBuilder(TARGET_FUND_ISIN, creationTime)
            .transactionDate(date2)
            .personalId("P200")
            .build();
    FundTransaction tx3 =
        exampleTransactionBuilder(TARGET_FUND_ISIN, creationTime)
            .transactionDate(date3)
            .personalId("P300")
            .build();

    repository.saveAll(List.of(tx1, tx2, tx3));

    Optional<LocalDate> latestDate = repository.findLatestTransactionDate();

    assertThat(latestDate).isPresent().hasValue(date2);
  }

  @Test
  @DisplayName("findLatestTransactionDate returns empty Optional when no data exists")
  void findLatestTransactionDate_returnsEmptyOptional_whenNoData() {
    Optional<LocalDate> latestDate = repository.findLatestTransactionDate();
    assertThat(latestDate).isEmpty();
  }

  @Test
  @DisplayName("findAll returns all inserted records")
  void testFindAll() {
    // Given
    FundTransaction tx1 = exampleTransactionBuilder(TARGET_FUND_ISIN, creationTime).build();
    FundTransaction tx2 = anotherExampleTransactionBuilder(TARGET_FUND_ISIN, creationTime).build();

    // When
    repository.saveAll(List.of(tx1, tx2));

    // Then
    List<FundTransaction> all = repository.findAll();
    assertThat(all).hasSize(2);
    assertThat(all)
        .extracting(FundTransaction::getPersonalId)
        .containsExactlyInAnyOrder(
            FundTransactionFixture.Dto.NEW_PERSONAL_ID,
            FundTransactionFixture.Dto.DUPLICATE_PERSONAL_ID);
  }

  @Test
  @DisplayName("deleteByIsinAndTransactionDateBetween deletes correct rows and returns count")
  void deleteByIsinAndTransactionDateBetween_deletesCorrectRows() {
    // Given
    FundTransaction txKeepBefore =
        exampleTransactionBuilder(TARGET_FUND_ISIN, creationTime)
            .transactionDate(DATE_BEFORE)
            .personalId("P1")
            .build(); // Keep
    FundTransaction txDeleteStart =
        exampleTransactionBuilder(TARGET_FUND_ISIN, creationTime)
            .transactionDate(DATE_START)
            .personalId("P2")
            .build(); // Delete
    FundTransaction txDeleteMiddle =
        exampleTransactionBuilder(TARGET_FUND_ISIN, creationTime)
            .transactionDate(DATE_MIDDLE)
            .personalId("P3")
            .build(); // Delete
    FundTransaction txDeleteEnd =
        anotherExampleTransactionBuilder(TARGET_FUND_ISIN, creationTime)
            .transactionDate(DATE_END)
            .personalId("P4")
            .build(); // Delete
    FundTransaction txKeepAfter =
        exampleTransactionBuilder(TARGET_FUND_ISIN, creationTime)
            .transactionDate(DATE_AFTER)
            .personalId("P5")
            .build(); // Keep
    FundTransaction txKeepOtherIsin =
        exampleTransactionBuilder(OTHER_FUND_ISIN, creationTime)
            .transactionDate(DATE_MIDDLE)
            .personalId("P6")
            .build(); // Keep

    repository.saveAll(
        List.of(
            txKeepBefore,
            txDeleteStart,
            txDeleteMiddle,
            txDeleteEnd,
            txKeepAfter,
            txKeepOtherIsin));
    assertThat(repository.count()).isEqualTo(6);

    // When
    int deletedCount =
        repository.deleteByIsinAndTransactionDateBetween(TARGET_FUND_ISIN, START_DATE, END_DATE);

    // Then
    assertThat(deletedCount).isEqualTo(3); // P2, P3, P4 should be deleted

    List<FundTransaction> remaining = repository.findAll();
    assertThat(remaining).hasSize(3); // P1, P5, P6 remain

    assertThat(remaining)
        .extracting(FundTransaction::getPersonalId)
        .containsExactlyInAnyOrder("P1", "P5", "P6");

    assertThat(remaining)
        .filteredOn(tx -> tx.getPersonalId().equals("P1") || tx.getPersonalId().equals("P5"))
        .allMatch(tx -> tx.getIsin().equals(TARGET_FUND_ISIN));
    assertThat(remaining)
        .filteredOn(tx -> tx.getPersonalId().equals("P6"))
        .allMatch(tx -> tx.getIsin().equals(OTHER_FUND_ISIN));
  }

  @Test
  @DisplayName(
      "deleteByIsinAndTransactionDateBetween returns 0 and deletes nothing when no ISIN match")
  void deleteByIsinAndTransactionDateBetween_noIsinMatch() {
    // Given
    FundTransaction tx1 =
        exampleTransactionBuilder(OTHER_FUND_ISIN, creationTime)
            .transactionDate(DATE_MIDDLE)
            .personalId("P1")
            .build();
    FundTransaction tx2 =
        anotherExampleTransactionBuilder(OTHER_FUND_ISIN, creationTime)
            .transactionDate(DATE_START)
            .personalId("P2")
            .build();
    repository.saveAll(List.of(tx1, tx2));
    long initialCount = repository.count();
    assertThat(initialCount).isEqualTo(2);

    // When
    int deletedCount =
        repository.deleteByIsinAndTransactionDateBetween(TARGET_FUND_ISIN, START_DATE, END_DATE);

    // Then
    assertThat(deletedCount).isZero();
    assertThat(repository.count()).isEqualTo(initialCount);
  }

  @Test
  @DisplayName(
      "deleteByIsinAndTransactionDateBetween returns 0 and deletes nothing when no date match")
  void deleteByIsinAndTransactionDateBetween_noDateMatch() {
    // Given
    FundTransaction tx1 =
        exampleTransactionBuilder(TARGET_FUND_ISIN, creationTime)
            .transactionDate(DATE_BEFORE)
            .personalId("P1")
            .build();
    FundTransaction tx2 =
        anotherExampleTransactionBuilder(TARGET_FUND_ISIN, creationTime)
            .transactionDate(DATE_AFTER)
            .personalId("P2")
            .build();
    repository.saveAll(List.of(tx1, tx2));
    long initialCount = repository.count();
    assertThat(initialCount).isEqualTo(2);

    // When
    int deletedCount =
        repository.deleteByIsinAndTransactionDateBetween(TARGET_FUND_ISIN, START_DATE, END_DATE);

    // Then
    assertThat(deletedCount).isZero();
    assertThat(repository.count()).isEqualTo(initialCount);
  }

  @Test
  @DisplayName("deleteByIsinAndTransactionDateBetween returns 0 when repository is empty")
  void deleteByIsinAndTransactionDateBetween_emptyRepo() {
    // Given
    assertThat(repository.count()).isZero();

    // When
    int deletedCount =
        repository.deleteByIsinAndTransactionDateBetween(TARGET_FUND_ISIN, START_DATE, END_DATE);

    // Then
    assertThat(deletedCount).isZero();
    assertThat(repository.count()).isZero();
  }
}
