package ee.tuleva.onboarding.analytics.thirdpillar;

import static ee.tuleva.onboarding.analytics.thirdpillar.AnalyticsThirdPillarTransactionFixture.CREATE_ANALYTICS_SCHEMA;
import static ee.tuleva.onboarding.analytics.thirdpillar.AnalyticsThirdPillarTransactionFixture.CREATE_THIRD_PILLAR_TABLE;
import static ee.tuleva.onboarding.analytics.thirdpillar.AnalyticsThirdPillarTransactionFixture.TRUNCATE_THIRD_PILLAR_TABLE;
import static ee.tuleva.onboarding.analytics.thirdpillar.AnalyticsThirdPillarTransactionFixture.anotherExampleTransaction;
import static ee.tuleva.onboarding.analytics.thirdpillar.AnalyticsThirdPillarTransactionFixture.exampleTransaction;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
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
class AnalyticsThirdPillarTransactionRepositoryTest {

  @Autowired AnalyticsThirdPillarTransactionRepository repository;

  @Autowired DataSource dataSource;

  @BeforeAll
  static void createSchema(@Autowired DataSource dataSource) throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(CREATE_ANALYTICS_SCHEMA);
      stmt.execute(CREATE_THIRD_PILLAR_TABLE);
    }
  }

  @BeforeEach
  void cleanUp() throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(TRUNCATE_THIRD_PILLAR_TABLE);
    }
  }

  @Test
  @DisplayName("existsBy... returns true only if a matching row is present")
  void testExistsByFields() {
    AnalyticsThirdPillarTransaction entity = exampleTransaction();

    boolean preCheck =
        repository
            .existsByReportingDateAndPersonalIdAndTransactionTypeAndTransactionValueAndShareAmount(
                entity.getReportingDate(),
                entity.getPersonalId(),
                entity.getTransactionType(),
                entity.getTransactionValue(),
                entity.getShareAmount());
    assertFalse(preCheck, "No row should match yet");

    repository.save(entity);

    boolean postCheck =
        repository
            .existsByReportingDateAndPersonalIdAndTransactionTypeAndTransactionValueAndShareAmount(
                entity.getReportingDate(),
                entity.getPersonalId(),
                entity.getTransactionType(),
                entity.getTransactionValue(),
                entity.getShareAmount());
    assertTrue(postCheck, "Should return true after insertion");

    boolean mismatchCheck =
        repository
            .existsByReportingDateAndPersonalIdAndTransactionTypeAndTransactionValueAndShareAmount(
                entity.getReportingDate(),
                entity.getPersonalId(),
                entity.getTransactionType(),
                entity.getTransactionValue().add(BigDecimal.ONE),
                entity.getShareAmount());
    assertFalse(mismatchCheck, "Should return false for mismatching fields");
  }

  @Test
  @DisplayName("findAll should return all inserted records and match the fields of each fixture")
  void testFindAll() {
    AnalyticsThirdPillarTransaction aTransaction1 = exampleTransaction();
    AnalyticsThirdPillarTransaction aTransaction2 = anotherExampleTransaction();

    repository.saveAll(List.of(aTransaction1, aTransaction2));

    var all = repository.findAll();
    assertEquals(2, all.size(), "There should be 2 records in total");

    var first =
        all.stream()
            .filter(x -> x.getPersonalId().equals(aTransaction1.getPersonalId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("First fixture not found in DB"));

    assertEquals(aTransaction1.getReportingDate(), first.getReportingDate());
    assertEquals(aTransaction1.getFullName(), first.getFullName());
    assertEquals(aTransaction1.getTransactionValue(), first.getTransactionValue());

    var second =
        all.stream()
            .filter(x -> x.getPersonalId().equals(aTransaction2.getPersonalId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Second fixture not found in DB"));

    assertEquals(aTransaction2.getReportingDate(), second.getReportingDate());
    assertEquals(aTransaction2.getFullName(), second.getFullName());
    assertEquals(aTransaction2.getTransactionValue(), second.getTransactionValue());
  }
}
