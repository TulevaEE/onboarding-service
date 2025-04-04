package ee.tuleva.onboarding.analytics.thirdpillar;

import static ee.tuleva.onboarding.analytics.thirdpillar.AnalyticsThirdPillarTransactionFixture.*;
import static org.assertj.core.api.Assertions.assertThat; // Using AssertJ for Optional checks
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
  void existsBy_returnsTrue_whenMatchingRowPresent() {
    // given
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

    // when
    repository.save(entity);
    boolean postCheck =
        repository
            .existsByReportingDateAndPersonalIdAndTransactionTypeAndTransactionValueAndShareAmount(
                entity.getReportingDate(),
                entity.getPersonalId(),
                entity.getTransactionType(),
                entity.getTransactionValue(),
                entity.getShareAmount());

    // then
    assertTrue(postCheck, "Should return true after insertion");
  }

  @Test
  void existsBy_returnsFalse_whenNoMatchingRowPresent() {
    // given
    AnalyticsThirdPillarTransaction entity = exampleTransaction();
    repository.save(entity);

    // when
    boolean mismatchCheck =
        repository
            .existsByReportingDateAndPersonalIdAndTransactionTypeAndTransactionValueAndShareAmount(
                entity.getReportingDate(),
                entity.getPersonalId(),
                entity.getTransactionType(),
                entity.getTransactionValue().add(BigDecimal.ONE),
                entity.getShareAmount());

    // then
    assertFalse(mismatchCheck, "Should return false for mismatching fields");
  }

  @Test
  void findAll_returnsAllInsertedRecords() {
    // given
    AnalyticsThirdPillarTransaction aTransaction1 = exampleTransaction();
    AnalyticsThirdPillarTransaction aTransaction2 = anotherExampleTransaction();
    repository.saveAll(List.of(aTransaction1, aTransaction2));

    // when
    var all = repository.findAll();

    // then
    assertEquals(2, all.size(), "There should be 2 records in total");
    assertTrue(all.stream().anyMatch(t -> t.getPersonalId().equals(aTransaction1.getPersonalId())));
    assertTrue(all.stream().anyMatch(t -> t.getPersonalId().equals(aTransaction2.getPersonalId())));
  }

  @Test
  void findLatestReportingDate_returnsEmpty_whenTableIsEmpty() {
    // when
    Optional<LocalDate> latestDate = repository.findLatestReportingDate();

    // then
    assertThat(latestDate).isEmpty();
  }

  @Test
  void findLatestReportingDate_returnsDate_whenSingleRecordExists() {
    // given
    LocalDate expectedDate = LocalDate.of(2024, 5, 15);
    AnalyticsThirdPillarTransaction transaction =
        exampleTransactionBuilder().reportingDate(expectedDate).build();
    repository.save(transaction);

    // when
    Optional<LocalDate> latestDate = repository.findLatestReportingDate();

    // then
    assertThat(latestDate).isPresent().contains(expectedDate);
  }

  @Test
  void findLatestReportingDate_returnsLatestDate_whenMultipleRecordsExist() {
    // given
    LocalDate date1 = LocalDate.of(2024, 5, 15);
    LocalDate date2 = LocalDate.of(2024, 6, 1);
    LocalDate date3 = LocalDate.of(2024, 4, 30);

    AnalyticsThirdPillarTransaction t1 = exampleTransactionBuilder().reportingDate(date1).build();
    AnalyticsThirdPillarTransaction t2 =
        anotherExampleTransactionBuilder().reportingDate(date2).build();
    AnalyticsThirdPillarTransaction t3 =
        exampleTransactionBuilder().personalId("diff").reportingDate(date3).build();

    repository.saveAll(List.of(t1, t2, t3));

    // when
    Optional<LocalDate> latestDate = repository.findLatestReportingDate();

    // then
    assertThat(latestDate).isPresent().contains(date2);
  }
}
