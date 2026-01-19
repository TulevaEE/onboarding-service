package ee.tuleva.onboarding.investment.price;

import ee.tuleva.onboarding.time.ClockHolder;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexValuesSnapshotService {

  private final JdbcClient jdbcClient;
  private final IndexValuesSnapshotRepository snapshotRepository;

  @Transactional
  public List<IndexValuesSnapshot> createSnapshot() {
    Instant snapshotTime = ClockHolder.clock().instant();

    LocalDate latestDate = findLatestDate();
    if (latestDate == null) {
      log.warn("No index values found in database");
      return List.of();
    }

    log.info("Creating index values snapshot: date={}, snapshotTime={}", latestDate, snapshotTime);

    List<IndexValuesSnapshot> snapshots = fetchIndexValuesForDate(latestDate, snapshotTime);

    if (snapshots.isEmpty()) {
      log.warn("No index values found for date: date={}", latestDate);
      return snapshots;
    }

    snapshotRepository.saveAll(snapshots);

    log.info(
        "Index values snapshot created: date={}, snapshotTime={}, count={}",
        latestDate,
        snapshotTime,
        snapshots.size());

    return snapshots;
  }

  private LocalDate findLatestDate() {
    return jdbcClient
        .sql("SELECT MAX(date) FROM index_values")
        .query(LocalDate.class)
        .optional()
        .orElse(null);
  }

  private List<IndexValuesSnapshot> fetchIndexValuesForDate(LocalDate date, Instant snapshotTime) {
    Instant createdAt = ClockHolder.clock().instant();

    return jdbcClient
        .sql(
            """
            SELECT key, date, value, provider, updated_at
            FROM index_values
            WHERE date = :date
            """)
        .param("date", date)
        .query(
            (rs, rowNum) ->
                new IndexValuesSnapshot(
                    null,
                    snapshotTime,
                    rs.getString("key"),
                    rs.getDate("date").toLocalDate(),
                    rs.getBigDecimal("value"),
                    rs.getString("provider"),
                    rs.getTimestamp("updated_at").toInstant(),
                    createdAt))
        .list();
  }
}
