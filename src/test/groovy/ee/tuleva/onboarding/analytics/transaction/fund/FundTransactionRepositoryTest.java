package ee.tuleva.onboarding.analytics.transaction.fund;

import static ee.tuleva.onboarding.analytics.transaction.fund.FundTransactionFixture.anotherExampleTransactionBuilder;
import static ee.tuleva.onboarding.analytics.transaction.fund.FundTransactionFixture.exampleTransactionBuilder;
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
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
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
  @DisplayName("findLatestTransactionDateByIsin returns max date for the specified ISIN")
  void findLatestTransactionDateByIsin_returnsMaxDate_forSpecificIsin() {
    LocalDate targetDate1 = LocalDate.of(2025, 3, 15);
    LocalDate targetDate2 = LocalDate.of(2025, 4, 1); // Latest for target ISIN
    LocalDate otherDate = LocalDate.of(2025, 4, 5); // Latest overall, but wrong ISIN

    FundTransaction tx1 =
        exampleTransactionBuilder(TARGET_FUND_ISIN, creationTime)
            .transactionDate(targetDate1)
            .personalId("P100")
            .build();
    FundTransaction tx2 =
        anotherExampleTransactionBuilder(TARGET_FUND_ISIN, creationTime)
            .transactionDate(targetDate2)
            .personalId("P200")
            .build();
    FundTransaction tx3 =
        exampleTransactionBuilder(OTHER_FUND_ISIN, creationTime)
            .transactionDate(otherDate)
            .personalId("P300")
            .build();

    repository.saveAll(List.of(tx1, tx2, tx3));

    Optional<LocalDate> latestDate = repository.findLatestTransactionDateByIsin(TARGET_FUND_ISIN);

    assertThat(latestDate).isPresent().hasValue(targetDate2);
  }

  @Test
  @DisplayName(
      "findLatestTransactionDateByIsin returns correct max date even if other ISIN has later date")
  void findLatestTransactionDateByIsin_returnsCorrectMaxDate_evenIfOtherIsinLater() {
    LocalDate targetDate1 = LocalDate.of(2025, 3, 15);
    LocalDate targetDate2 = LocalDate.of(2025, 4, 1); // Latest for target ISIN
    LocalDate otherDate = LocalDate.of(2025, 4, 5); // Latest overall

    FundTransaction tx1 =
        exampleTransactionBuilder(TARGET_FUND_ISIN, creationTime)
            .transactionDate(targetDate1)
            .personalId("P100")
            .build();
    FundTransaction tx2 =
        anotherExampleTransactionBuilder(TARGET_FUND_ISIN, creationTime)
            .transactionDate(targetDate2)
            .personalId("P200")
            .build();
    FundTransaction tx3 =
        exampleTransactionBuilder(OTHER_FUND_ISIN, creationTime)
            .transactionDate(otherDate)
            .personalId("P300")
            .build();

    repository.saveAll(List.of(tx1, tx2, tx3));

    Optional<LocalDate> latestDateTarget =
        repository.findLatestTransactionDateByIsin(TARGET_FUND_ISIN);
    Optional<LocalDate> latestDateOther =
        repository.findLatestTransactionDateByIsin(OTHER_FUND_ISIN);

    assertThat(latestDateTarget).isPresent().hasValue(targetDate2);
    assertThat(latestDateOther).isPresent().hasValue(otherDate);
  }

  @Test
  @DisplayName("findLatestTransactionDateByIsin returns empty Optional when no data for that ISIN")
  void findLatestTransactionDateByIsin_returnsEmptyOptional_whenNoDataForIsin() {
    LocalDate otherDate = LocalDate.of(2025, 4, 5);
    FundTransaction tx1 =
        exampleTransactionBuilder(OTHER_FUND_ISIN, creationTime)
            .transactionDate(otherDate)
            .personalId("P300")
            .build();

    repository.save(tx1);

    Optional<LocalDate> latestDate = repository.findLatestTransactionDateByIsin(TARGET_FUND_ISIN);
    assertThat(latestDate).isEmpty();
  }

  @Test
  @DisplayName("findLatestTransactionDateByIsin returns empty Optional when repository is empty")
  void findLatestTransactionDateByIsin_returnsEmptyOptional_whenRepoIsEmpty() {
    Optional<LocalDate> latestDate = repository.findLatestTransactionDateByIsin(TARGET_FUND_ISIN);
    assertThat(latestDate).isEmpty();
  }

  @Test
  @DisplayName("findAll returns all inserted records")
  void testFindAll() {
    FundTransaction tx1 = exampleTransactionBuilder(TARGET_FUND_ISIN, creationTime).build();
    FundTransaction tx2 = anotherExampleTransactionBuilder(TARGET_FUND_ISIN, creationTime).build();

    repository.saveAll(List.of(tx1, tx2));

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
    FundTransaction txKeepBefore =
        exampleTransactionBuilder(TARGET_FUND_ISIN, creationTime)
            .transactionDate(DATE_BEFORE)
            .personalId("P1")
            .build();
    FundTransaction txDeleteStart =
        exampleTransactionBuilder(TARGET_FUND_ISIN, creationTime)
            .transactionDate(DATE_START)
            .personalId("P2")
            .build();
    FundTransaction txDeleteMiddle =
        exampleTransactionBuilder(TARGET_FUND_ISIN, creationTime)
            .transactionDate(DATE_MIDDLE)
            .personalId("P3")
            .build();
    FundTransaction txDeleteEnd =
        anotherExampleTransactionBuilder(TARGET_FUND_ISIN, creationTime)
            .transactionDate(DATE_END)
            .personalId("P4")
            .build();
    FundTransaction txKeepAfter =
        exampleTransactionBuilder(TARGET_FUND_ISIN, creationTime)
            .transactionDate(DATE_AFTER)
            .personalId("P5")
            .build();
    FundTransaction txKeepOtherIsin =
        exampleTransactionBuilder(OTHER_FUND_ISIN, creationTime)
            .transactionDate(DATE_MIDDLE)
            .personalId("P6")
            .build();

    repository.saveAll(
        List.of(
            txKeepBefore,
            txDeleteStart,
            txDeleteMiddle,
            txDeleteEnd,
            txKeepAfter,
            txKeepOtherIsin));
    assertThat(repository.count()).isEqualTo(6);

    int deletedCount =
        repository.deleteByIsinAndTransactionDateBetween(TARGET_FUND_ISIN, START_DATE, END_DATE);

    assertThat(deletedCount).isEqualTo(3);

    List<FundTransaction> remaining = repository.findAll();
    assertThat(remaining).hasSize(3);

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

    int deletedCount =
        repository.deleteByIsinAndTransactionDateBetween(TARGET_FUND_ISIN, START_DATE, END_DATE);

    assertThat(deletedCount).isZero();
    assertThat(repository.count()).isEqualTo(initialCount);
  }

  @Test
  @DisplayName(
      "deleteByIsinAndTransactionDateBetween returns 0 and deletes nothing when no date match")
  void deleteByIsinAndTransactionDateBetween_noDateMatch() {
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

    int deletedCount =
        repository.deleteByIsinAndTransactionDateBetween(TARGET_FUND_ISIN, START_DATE, END_DATE);

    assertThat(deletedCount).isZero();
    assertThat(repository.count()).isEqualTo(initialCount);
  }

  @Test
  @DisplayName("deleteByIsinAndTransactionDateBetween returns 0 when repository is empty")
  void deleteByIsinAndTransactionDateBetween_emptyRepo() {
    assertThat(repository.count()).isZero();

    int deletedCount =
        repository.deleteByIsinAndTransactionDateBetween(TARGET_FUND_ISIN, START_DATE, END_DATE);

    assertThat(deletedCount).isZero();
    assertThat(repository.count()).isZero();
  }
}
