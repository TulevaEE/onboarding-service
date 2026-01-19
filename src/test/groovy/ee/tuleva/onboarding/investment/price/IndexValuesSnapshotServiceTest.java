package ee.tuleva.onboarding.investment.price;

import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.time.ClockHolder;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@Import({IndexValuesSnapshotService.class, IndexValuesSnapshotRepository.class})
class IndexValuesSnapshotServiceTest {

  @Autowired JdbcClient jdbcClient;
  @Autowired IndexValuesSnapshotService service;

  private static final Instant FIXED_INSTANT = Instant.parse("2026-01-15T11:30:00Z");
  private static final LocalDate FIXED_DATE = LocalDate.of(2026, 1, 15);

  @BeforeEach
  void setUp() {
    ClockHolder.setClock(Clock.fixed(FIXED_INSTANT, UTC));
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void createSnapshot_savesIndexValuesForCurrentDate() {
    insertIndexValue("EE3600109435", FIXED_DATE, new BigDecimal("1.23456"), "PENSIONIKESKUS");
    insertIndexValue("SGAS.DE", FIXED_DATE, new BigDecimal("12.34500"), "YAHOO");

    List<IndexValuesSnapshot> snapshots = service.createSnapshot();

    assertThat(snapshots).hasSize(2);
    assertThat(snapshots)
        .extracting(IndexValuesSnapshot::key)
        .containsExactlyInAnyOrder("EE3600109435", "SGAS.DE");
    assertThat(snapshots).allSatisfy(s -> assertThat(s.snapshotTime()).isEqualTo(FIXED_INSTANT));
    assertThat(snapshots).allSatisfy(s -> assertThat(s.date()).isEqualTo(FIXED_DATE));
  }

  @Test
  void createSnapshot_ignoresIndexValuesForOtherDates() {
    insertIndexValue("EE3600109435", FIXED_DATE, new BigDecimal("1.23456"), "PENSIONIKESKUS");
    insertIndexValue("SGAS.DE", FIXED_DATE.minusDays(1), new BigDecimal("12.34500"), "YAHOO");

    List<IndexValuesSnapshot> snapshots = service.createSnapshot();

    assertThat(snapshots).hasSize(1);
    assertThat(snapshots.getFirst().key()).isEqualTo("EE3600109435");
  }

  @Test
  void createSnapshot_returnsEmptyListWhenNoIndexValuesForCurrentDate() {
    insertIndexValue("EE3600109435", FIXED_DATE.minusDays(1), new BigDecimal("1.23456"), "TEST");

    List<IndexValuesSnapshot> snapshots = service.createSnapshot();

    assertThat(snapshots).isEmpty();
  }

  @Test
  void createSnapshot_persistsSnapshotsToDatabase() {
    insertIndexValue("EE3600109435", FIXED_DATE, new BigDecimal("1.23456"), "PENSIONIKESKUS");

    service.createSnapshot();

    List<String> savedKeys =
        jdbcClient
            .sql("SELECT key FROM index_values_snapshot WHERE snapshot_time = :snapshotTime")
            .param("snapshotTime", OffsetDateTime.ofInstant(FIXED_INSTANT, UTC))
            .query(String.class)
            .list();

    assertThat(savedKeys).containsExactly("EE3600109435");
  }

  private void insertIndexValue(String key, LocalDate date, BigDecimal value, String provider) {
    jdbcClient
        .sql(
            """
            INSERT INTO index_values (key, date, value, provider, updated_at)
            VALUES (:key, :date, :value, :provider, :updatedAt)
            """)
        .param("key", key)
        .param("date", date)
        .param("value", value)
        .param("provider", provider)
        .param("updatedAt", OffsetDateTime.ofInstant(FIXED_INSTANT, UTC))
        .update();
  }
}
