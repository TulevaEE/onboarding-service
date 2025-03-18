package ee.tuleva.onboarding.analytics.exchange;

import static ee.tuleva.onboarding.analytics.exchange.ExchangeTransactionFixture.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.Statement;
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

  private static final String CREATE_PUBLIC_SCHEMA = "CREATE SCHEMA IF NOT EXISTS public";
  private static final String CREATE_EXCHANGE_TRANSACTIONS_TABLE =
      "CREATE TABLE IF NOT EXISTS public.exchange_transactions ("
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
      "TRUNCATE TABLE public.exchange_transactions";

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

  @Test
  @DisplayName("existsBy... returns true only when a matching row is present")
  void testExistsByReportingDateAndSecurityFromAndSecurityToAndCodeAndUnitAmountAndPercentage() {
    // GIVEN
    ExchangeTransaction entity = exampleTransaction();

    boolean preCheck =
        repository
            .existsByReportingDateAndSecurityFromAndSecurityToAndCodeAndUnitAmountAndPercentage(
                entity.getReportingDate(),
                entity.getSecurityFrom(),
                entity.getSecurityTo(),
                entity.getCode(),
                entity.getUnitAmount(),
                entity.getPercentage());
    assertFalse(preCheck, "No matching row should exist before insert");

    // WHEN
    repository.save(entity);

    // THEN
    boolean postCheck =
        repository
            .existsByReportingDateAndSecurityFromAndSecurityToAndCodeAndUnitAmountAndPercentage(
                entity.getReportingDate(),
                entity.getSecurityFrom(),
                entity.getSecurityTo(),
                entity.getCode(),
                entity.getUnitAmount(),
                entity.getPercentage());
    assertTrue(postCheck, "Should return true after insertion");

    boolean mismatchCheck =
        repository
            .existsByReportingDateAndSecurityFromAndSecurityToAndCodeAndUnitAmountAndPercentage(
                entity.getReportingDate(),
                entity.getSecurityFrom(),
                entity.getSecurityTo(),
                "WRONG_CODE",
                entity.getUnitAmount(),
                entity.getPercentage());
    assertFalse(mismatchCheck, "Should return false for mismatching fields");
  }

  @Test
  @DisplayName("findAll returns all inserted records")
  void testFindAll() {
    ExchangeTransaction tx1 = exampleTransaction();
    ExchangeTransaction tx2 = anotherExampleTransaction();

    repository.saveAll(List.of(tx1, tx2));

    var all = repository.findAll();
    assertTrue(all.size() == 2, "Should find exactly 2 records");
  }
}
