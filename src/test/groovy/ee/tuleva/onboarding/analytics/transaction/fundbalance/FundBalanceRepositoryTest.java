package ee.tuleva.onboarding.analytics.transaction.fundbalance;

import static ee.tuleva.onboarding.analytics.transaction.fundbalance.FundBalanceFixture.*;
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
class FundBalanceRepositoryTest {

  @Autowired FundBalanceRepository repository;

  @Autowired DataSource dataSource;

  private final LocalDateTime creationTime = LocalDateTime.now(TestClockHolder.clock);

  private static final String CREATE_FUND_BALANCE_TABLE =
      """
            CREATE TABLE IF NOT EXISTS fund_balance (
                id BIGSERIAL PRIMARY KEY,
                security_name VARCHAR(255),
                isin VARCHAR(255),
                nav NUMERIC(19, 8),
                balance NUMERIC(19, 2),
                count_investors INTEGER,
                count_units NUMERIC(19, 8),
                count_units_bron NUMERIC(19, 8),
                count_units_free NUMERIC(19, 8),
                count_units_arest NUMERIC(19, 8),
                count_units_fm NUMERIC(19, 8),
                fund_manager VARCHAR(255),
                request_date DATE NOT NULL,
                date_created TIMESTAMP NOT NULL
            );
            """;
  private static final String TRUNCATE_FUND_BALANCE_TABLE =
      "TRUNCATE TABLE fund_balance RESTART IDENTITY";

  @BeforeAll
  static void createSchema(@Autowired DataSource dataSource) throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(CREATE_FUND_BALANCE_TABLE);
    }
  }

  @BeforeEach
  void cleanUp() throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(TRUNCATE_FUND_BALANCE_TABLE);
    }
  }

  @Test
  @DisplayName("findLatestRequestDate returns max date when data exists")
  void findLatestRequestDate_returnsMaxDate() {
    // given
    FundBalance fb1 = entityBuilder(creationTime).requestDate(DATE_1).isin(ISIN_1).build();
    FundBalance fb2 = entityBuilder(creationTime).requestDate(DATE_2).isin(ISIN_1).build();
    FundBalance fb3 = entityBuilder(creationTime).requestDate(DATE_1).isin(ISIN_2).build();
    repository.saveAll(List.of(fb1, fb2, fb3));

    // when
    Optional<LocalDate> latestDate = repository.findLatestRequestDate();

    // then
    assertThat(latestDate).isPresent().hasValue(DATE_2);
  }

  @Test
  @DisplayName("findLatestRequestDate returns empty Optional when repository is empty")
  void findLatestRequestDate_returnsEmptyOptional_whenRepoIsEmpty() {
    // when
    Optional<LocalDate> latestDate = repository.findLatestRequestDate();

    // then
    assertThat(latestDate).isEmpty();
  }

  @Test
  @DisplayName("deleteByRequestDate deletes correct rows and returns count")
  void deleteByRequestDate_deletesCorrectRows() {
    // given
    FundBalance fbKeep = entityBuilder(creationTime).requestDate(DATE_1).isin(ISIN_1).build();
    FundBalance fbDelete1 = entityBuilder(creationTime).requestDate(DATE_2).isin(ISIN_1).build();
    FundBalance fbDelete2 = entityBuilder(creationTime).requestDate(DATE_2).isin(ISIN_2).build();
    repository.saveAll(List.of(fbKeep, fbDelete1, fbDelete2));
    assertThat(repository.count()).isEqualTo(3);

    // when
    int deletedCount = repository.deleteByRequestDate(DATE_2);

    // then
    assertThat(deletedCount).isEqualTo(2);
    List<FundBalance> remaining = repository.findAll();
    assertThat(remaining).hasSize(1);
    assertThat(remaining.get(0).getRequestDate()).isEqualTo(DATE_1);
    assertThat(remaining.get(0).getIsin()).isEqualTo(ISIN_1);
  }

  @Test
  @DisplayName("deleteByRequestDate returns 0 and deletes nothing when no date match")
  void deleteByRequestDate_noDateMatch() {
    // given
    FundBalance fb1 = entityBuilder(creationTime).requestDate(DATE_1).isin(ISIN_1).build();
    repository.save(fb1);
    long initialCount = repository.count();
    assertThat(initialCount).isEqualTo(1);

    // when
    int deletedCount = repository.deleteByRequestDate(DATE_2);

    // then
    assertThat(deletedCount).isZero();
    assertThat(repository.count()).isEqualTo(initialCount);
  }

  @Test
  @DisplayName("deleteByRequestDate returns 0 when repository is empty")
  void deleteByRequestDate_emptyRepo() {
    // given
    assertThat(repository.count()).isZero();

    // when
    int deletedCount = repository.deleteByRequestDate(DATE_1);

    // then
    assertThat(deletedCount).isZero();
    assertThat(repository.count()).isZero();
  }
}
