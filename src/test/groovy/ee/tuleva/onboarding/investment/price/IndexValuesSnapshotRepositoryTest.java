package ee.tuleva.onboarding.investment.price;

import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@Import(IndexValuesSnapshotRepository.class)
class IndexValuesSnapshotRepositoryTest {

  @Autowired IndexValuesSnapshotRepository repository;
  @Autowired JdbcClient jdbcClient;

  private static final LocalDate DATE = LocalDate.of(2026, 1, 15);
  private static final Instant SNAPSHOT_TIME = Instant.parse("2026-01-15T11:30:00Z");
  private static final Instant CREATED_AT = Instant.parse("2026-01-15T11:30:01Z");
  private static final Instant SOURCE_UPDATED_AT = Instant.parse("2026-01-15T10:00:00Z");

  @Test
  void saveAll_persistsSnapshots() {
    List<IndexValuesSnapshot> snapshots =
        List.of(
            new IndexValuesSnapshot(
                null,
                SNAPSHOT_TIME,
                "EE3600109435",
                DATE,
                new BigDecimal("1.23456"),
                "PENSIONIKESKUS",
                SOURCE_UPDATED_AT,
                CREATED_AT),
            new IndexValuesSnapshot(
                null,
                SNAPSHOT_TIME,
                "SGAS.DE",
                DATE,
                new BigDecimal("12.34500"),
                "YAHOO",
                SOURCE_UPDATED_AT,
                CREATED_AT));

    repository.saveAll(snapshots);

    List<String> savedKeys =
        jdbcClient
            .sql("SELECT key FROM index_values_snapshot WHERE snapshot_time = :snapshotTime")
            .param("snapshotTime", OffsetDateTime.ofInstant(SNAPSHOT_TIME, UTC))
            .query(String.class)
            .list();

    assertThat(savedKeys).containsExactlyInAnyOrder("EE3600109435", "SGAS.DE");
  }
}
