package ee.tuleva.onboarding.analytics.transaction.thirdpillar;

import static ee.tuleva.onboarding.analytics.transaction.thirdpillar.AnalyticsThirdPillarTransactionFixture.*;
import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
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
  void findAll_returnsAllInsertedRecords() {
    // given
    AnalyticsThirdPillarTransaction aTransaction1 = exampleTransaction();
    AnalyticsThirdPillarTransaction aTransaction2 = anotherExampleTransaction();
    repository.saveAll(List.of(aTransaction1, aTransaction2));

    // when
    var all = repository.findAll();

    // then
    assertThat(all).hasSize(2);
    assertThat(all)
        .extracting(AnalyticsThirdPillarTransaction::getPersonalId)
        .containsExactlyInAnyOrder(aTransaction1.getPersonalId(), aTransaction2.getPersonalId());
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

  @Test
  void deleteByReportingDateBetween_deletesRecordsWithinRange() {
    // given
    LocalDate dateBefore = LocalDate.of(2024, 4, 30);
    LocalDate dateStart = LocalDate.of(2024, 5, 1);
    LocalDate dateMiddle = LocalDate.of(2024, 5, 15);
    LocalDate dateEnd = LocalDate.of(2024, 5, 31);
    LocalDate dateAfter = LocalDate.of(2024, 6, 1);

    AnalyticsThirdPillarTransaction tBefore =
        exampleTransactionBuilder().reportingDate(dateBefore).personalId("ID_BEFORE").build();
    AnalyticsThirdPillarTransaction tStart =
        exampleTransactionBuilder().reportingDate(dateStart).personalId("ID_START").build();
    AnalyticsThirdPillarTransaction tMiddle =
        exampleTransactionBuilder().reportingDate(dateMiddle).personalId("ID_MIDDLE").build();
    AnalyticsThirdPillarTransaction tEnd =
        exampleTransactionBuilder().reportingDate(dateEnd).personalId("ID_END").build();
    AnalyticsThirdPillarTransaction tAfter =
        exampleTransactionBuilder().reportingDate(dateAfter).personalId("ID_AFTER").build();

    repository.saveAll(List.of(tBefore, tStart, tMiddle, tEnd, tAfter));
    assertThat(repository.count()).isEqualTo(5);

    // when
    int deletedCount = repository.deleteByReportingDateBetween(dateStart, dateEnd);

    // then
    assertThat(deletedCount).isEqualTo(3);
    assertThat(repository.count()).isEqualTo(2);
    List<AnalyticsThirdPillarTransaction> remaining = repository.findAll();
    assertThat(remaining)
        .extracting(AnalyticsThirdPillarTransaction::getPersonalId)
        .containsExactlyInAnyOrder("ID_BEFORE", "ID_AFTER");
  }

  @Test
  void deleteByReportingDateBetween_deletesNothing_whenNoRecordsInRange() {
    // given
    LocalDate dateBefore = LocalDate.of(2024, 4, 30);
    LocalDate dateAfter = LocalDate.of(2024, 6, 1);
    LocalDate rangeStart = LocalDate.of(2024, 5, 1);
    LocalDate rangeEnd = LocalDate.of(2024, 5, 31);

    AnalyticsThirdPillarTransaction tBefore =
        exampleTransactionBuilder().reportingDate(dateBefore).personalId("ID_BEFORE").build();
    AnalyticsThirdPillarTransaction tAfter =
        exampleTransactionBuilder().reportingDate(dateAfter).personalId("ID_AFTER").build();

    repository.saveAll(List.of(tBefore, tAfter));
    assertThat(repository.count()).isEqualTo(2);

    // when
    int deletedCount = repository.deleteByReportingDateBetween(rangeStart, rangeEnd);

    // then
    assertThat(deletedCount).isEqualTo(0);
    assertThat(repository.count()).isEqualTo(2);
  }

  @Test
  void deleteByReportingDateBetween_deletesNothing_whenTableIsEmpty() {
    // given
    assertThat(repository.count()).isEqualTo(0);
    LocalDate rangeStart = LocalDate.of(2024, 5, 1);
    LocalDate rangeEnd = LocalDate.of(2024, 5, 31);

    // when
    int deletedCount = repository.deleteByReportingDateBetween(rangeStart, rangeEnd);

    // then
    assertThat(deletedCount).isEqualTo(0);
    assertThat(repository.count()).isEqualTo(0);
  }
}
