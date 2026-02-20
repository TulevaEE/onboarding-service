package ee.tuleva.onboarding.analytics.transaction.exchange.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.analytics.transaction.exchange.ExchangeTransactionFixture;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.time.TestClockHolder;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class ExchangeTransactionSnapshotRepositoryTest {

  @Autowired ExchangeTransactionSnapshotRepository repository;

  @Autowired DataSource dataSource;

  private static final LocalDateTime FIXED_SNAPSHOT_TAKEN_AT =
      LocalDateTime.of(2025, Month.MARCH.getValue(), 10, 10, 30, 0, 0);
  private static final LocalDateTime FIXED_CREATED_AT =
      LocalDateTime.of(2025, Month.MARCH.getValue(), 10, 10, 30, 5, 0);
  private static final LocalDateTime FIXED_SOURCE_DATE_CREATED =
      LocalDateTime.of(2025, Month.FEBRUARY, 15, 10, 0, 0);

  private static final String CREATE_PUBLIC_SCHEMA = "CREATE SCHEMA IF NOT EXISTS public";
  private static final String CREATE_EXCHANGE_TRANSACTION_SNAPSHOT_TABLE =
      "CREATE TABLE IF NOT EXISTS public.exchange_transaction_snapshot ("
          + "id BIGSERIAL PRIMARY KEY,"
          + "snapshot_taken_at TIMESTAMP NOT NULL,"
          + "created_at TIMESTAMP NOT NULL,"
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
          + "source_date_created TIMESTAMP NOT NULL"
          + ");";

  private static final String TRUNCATE_EXCHANGE_TRANSACTION_SNAPSHOT_TABLE =
      "TRUNCATE TABLE public.exchange_transaction_snapshot";

  @BeforeAll
  static void createSchema(@Autowired DataSource dataSource) throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(CREATE_PUBLIC_SCHEMA);
      stmt.execute(CREATE_EXCHANGE_TRANSACTION_SNAPSHOT_TABLE);
    }
    ClockHolder.setClock(TestClockHolder.clock);
  }

  @BeforeEach
  void cleanUp() throws Exception {
    ClockHolder.setClock(TestClockHolder.clock);
    ExchangeTransactionFixture.exampleTransactionBuilder().dateCreated(FIXED_SOURCE_DATE_CREATED);

    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(TRUNCATE_EXCHANGE_TRANSACTION_SNAPSHOT_TABLE);
    }
  }

  @AfterEach
  void resetClock() {
    ClockHolder.setDefaultClock();
  }

  private ExchangeTransactionSnapshot createTestSnapshot(String code) {
    return ExchangeTransactionSnapshotFixture.exampleSnapshotBuilderFromTransaction(
            ExchangeTransactionFixture.exampleTransactionBuilder()
                .dateCreated(FIXED_SOURCE_DATE_CREATED)
                .build())
        .code(code)
        .snapshotTakenAt(FIXED_SNAPSHOT_TAKEN_AT)
        .createdAt(FIXED_CREATED_AT)
        .build();
  }

  @Test
  @DisplayName("save persists snapshot and assigns ID")
  void save_persistsSnapshotAndAssignsId() {
    ExchangeTransactionSnapshot snapshot = createTestSnapshot("SNAP001");
    assertThat(snapshot.getId()).isNull();

    ExchangeTransactionSnapshot savedSnapshot = repository.save(snapshot);

    assertThat(savedSnapshot).isNotNull();
    assertThat(savedSnapshot.getId()).isNotNull().isPositive();
    assertThat(savedSnapshot.getCode()).isEqualTo("SNAP001");
    assertThat(savedSnapshot.getSnapshotTakenAt()).isEqualTo(FIXED_SNAPSHOT_TAKEN_AT);
    assertThat(savedSnapshot.getCreatedAt()).isEqualTo(FIXED_CREATED_AT);
    assertThat(savedSnapshot.getSourceDateCreated()).isEqualTo(FIXED_SOURCE_DATE_CREATED);
  }

  @Test
  @DisplayName("findAll returns all saved snapshots")
  void findAll_returnsAllSavedSnapshots() {
    ExchangeTransactionSnapshot snapshot1 = createTestSnapshot("SNAP001");
    ExchangeTransactionSnapshot snapshot2 =
        ExchangeTransactionSnapshotFixture.exampleSnapshotBuilderFromTransaction(
                ExchangeTransactionFixture.anotherExampleTransaction())
            .code("SNAP002")
            .snapshotTakenAt(FIXED_SNAPSHOT_TAKEN_AT.plusHours(1))
            .createdAt(FIXED_CREATED_AT.plusHours(1))
            .sourceDateCreated(FIXED_SOURCE_DATE_CREATED.plusDays(1))
            .build();

    repository.saveAll(List.of(snapshot1, snapshot2));

    List<ExchangeTransactionSnapshot> allSnapshots = repository.findAll();

    assertThat(allSnapshots).hasSize(2);
    assertThat(allSnapshots)
        .extracting(ExchangeTransactionSnapshot::getCode)
        .containsExactlyInAnyOrder("SNAP001", "SNAP002");
  }

  @Test
  @DisplayName("findById returns correct snapshot when it exists")
  void findById_returnsCorrectSnapshot() {
    ExchangeTransactionSnapshot snapshot1 = createTestSnapshot("SNAP001");
    ExchangeTransactionSnapshot savedSnapshot = repository.save(snapshot1);
    Long savedId = savedSnapshot.getId();

    Optional<ExchangeTransactionSnapshot> foundSnapshotOpt = repository.findById(savedId);

    assertThat(foundSnapshotOpt).isPresent();
    ExchangeTransactionSnapshot foundSnapshot = foundSnapshotOpt.get();
    assertThat(foundSnapshot.getId()).isEqualTo(savedId);
    assertThat(foundSnapshot.getCode()).isEqualTo("SNAP001");
  }

  @Test
  @DisplayName("findById returns empty Optional when snapshot does not exist")
  void findById_returnsEmpty_whenNotFound() {
    Optional<ExchangeTransactionSnapshot> foundSnapshotOpt = repository.findById(999L);
    assertThat(foundSnapshotOpt).isNotPresent();
  }

  @Test
  @DisplayName("count returns correct number of snapshots")
  void count_returnsCorrectNumberOfSnapshots() {
    assertThat(repository.count()).isEqualTo(0);

    ExchangeTransactionSnapshot snapshot1 = createTestSnapshot("SNAP001");
    repository.save(snapshot1);
    assertThat(repository.count()).isEqualTo(1);

    ExchangeTransactionSnapshot snapshot2 = createTestSnapshot("SNAP002");
    repository.save(snapshot2);
    assertThat(repository.count()).isEqualTo(2);
  }

  @Test
  @DisplayName("deleteAll removes all snapshots")
  void deleteAll_removesAllSnapshots() {
    ExchangeTransactionSnapshot snapshot1 = createTestSnapshot("SNAP001");
    ExchangeTransactionSnapshot snapshot2 = createTestSnapshot("SNAP002");
    repository.saveAll(List.of(snapshot1, snapshot2));
    assertThat(repository.count()).isEqualTo(2);

    repository.deleteAll();

    assertThat(repository.count()).isEqualTo(0);
  }

  @Test
  @DisplayName("saveAll persists multiple snapshots")
  void saveAll_persistsMultipleSnapshots() {
    ExchangeTransactionSnapshot snapshot1 = createTestSnapshot("SNAP001");
    ExchangeTransactionSnapshot snapshot2 = createTestSnapshot("SNAP002");

    List<ExchangeTransactionSnapshot> savedSnapshots =
        repository.saveAll(List.of(snapshot1, snapshot2));

    assertThat(savedSnapshots).hasSize(2);
    assertThat(repository.count()).isEqualTo(2);
    assertThat(savedSnapshots.get(0).getId()).isNotNull();
    assertThat(savedSnapshots.get(1).getId()).isNotNull();
  }
}
