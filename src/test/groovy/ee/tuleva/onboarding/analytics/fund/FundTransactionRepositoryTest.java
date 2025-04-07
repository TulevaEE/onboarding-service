package ee.tuleva.onboarding.analytics.fund;

import static ee.tuleva.onboarding.analytics.fund.FundTransactionFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

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

  private static final String FUND_ISIN = "EE1234567890";
  private final LocalDateTime creationTime = LocalDateTime.now();

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
          + "date_created TIMESTAMP NOT NULL,"
          + "CONSTRAINT fund_transaction_unique_key UNIQUE (transaction_date, personal_id, transaction_type, amount, unit_amount)"
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
  @DisplayName("existsBy... returns true only when a matching row is present")
  void testExistsByUniqueKeyFields() {
    FundTransaction entity = exampleTransactionBuilder(FUND_ISIN, creationTime).build();

    boolean preCheck =
        repository.existsByTransactionDateAndPersonalIdAndTransactionTypeAndAmountAndUnitAmount(
            entity.getTransactionDate(),
            entity.getPersonalId(),
            entity.getTransactionType(),
            entity.getAmount(),
            entity.getUnitAmount());
    assertFalse(preCheck, "No matching row should exist before insert");

    repository.save(entity);

    boolean postCheck =
        repository.existsByTransactionDateAndPersonalIdAndTransactionTypeAndAmountAndUnitAmount(
            entity.getTransactionDate(),
            entity.getPersonalId(),
            entity.getTransactionType(),
            entity.getAmount(),
            entity.getUnitAmount());
    assertTrue(postCheck, "Should return true after insertion");

    boolean mismatchCheck =
        repository.existsByTransactionDateAndPersonalIdAndTransactionTypeAndAmountAndUnitAmount(
            entity.getTransactionDate(),
            "DIFFERENT_ID", // Mismatch
            entity.getTransactionType(),
            entity.getAmount(),
            entity.getUnitAmount());
    assertFalse(mismatchCheck, "Should return false for mismatching fields");
  }

  @Test
  @DisplayName("findLatestTransactionDate returns max date when data exists")
  void findLatestTransactionDate_returnsMaxDate() {
    LocalDate date1 = LocalDate.of(2025, 3, 15);
    LocalDate date2 = LocalDate.of(2025, 4, 1);
    LocalDate date3 = LocalDate.of(2025, 3, 30);

    FundTransaction tx1 =
        exampleTransactionBuilder(FUND_ISIN, creationTime).transactionDate(date1).build();
    FundTransaction tx2 =
        anotherExampleTransactionBuilder(FUND_ISIN, creationTime).transactionDate(date2).build();
    FundTransaction tx3 =
        exampleTransactionBuilder(FUND_ISIN, creationTime)
            .transactionDate(date3)
            .personalId("50001011111")
            .build(); // Different ID to avoid unique constraint

    repository.saveAll(List.of(tx1, tx2, tx3));

    Optional<LocalDate> latestDate = repository.findLatestTransactionDate();

    assertTrue(latestDate.isPresent(), "Optional should contain a date");
    assertEquals(date2, latestDate.get(), "Should return the maximum transaction date");
  }

  @Test
  @DisplayName("findLatestTransactionDate returns empty Optional when no data exists")
  void findLatestTransactionDate_returnsEmptyOptional_whenNoData() {
    Optional<LocalDate> latestDate = repository.findLatestTransactionDate();

    assertTrue(latestDate.isEmpty(), "Optional should be empty when repository is empty");
  }

  @Test
  @DisplayName("findAll returns all inserted records")
  void testFindAll() {
    FundTransaction tx1 = exampleTransactionBuilder(FUND_ISIN, creationTime).build();
    FundTransaction tx2 = anotherExampleTransactionBuilder(FUND_ISIN, creationTime).build();

    repository.saveAll(List.of(tx1, tx2));

    List<FundTransaction> all = repository.findAll();
    assertThat(all).hasSize(2);
    assertThat(all)
        .extracting(FundTransaction::getPersonalId)
        .containsExactlyInAnyOrder(tx1.getPersonalId(), tx2.getPersonalId());
  }
}
