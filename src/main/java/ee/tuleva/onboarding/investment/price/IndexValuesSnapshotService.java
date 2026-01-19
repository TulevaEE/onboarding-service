package ee.tuleva.onboarding.investment.price;

import java.time.Clock;
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
  private final Clock clock;

  @Transactional
  public List<IndexValuesSnapshot> createSnapshot() {
    Instant snapshotTime = clock.instant();
    LocalDate today = LocalDate.now(clock);

    log.info("Creating index values snapshot: date={}, snapshotTime={}", today, snapshotTime);

    List<IndexValuesSnapshot> snapshots = fetchCurrentDateIndexValues(today, snapshotTime);

    if (snapshots.isEmpty()) {
      log.warn("No index values found for current date: date={}", today);
      return snapshots;
    }

    snapshotRepository.saveAll(snapshots);

    log.info(
        "Index values snapshot created: date={}, snapshotTime={}, count={}",
        today,
        snapshotTime,
        snapshots.size());

    return snapshots;
  }

  private List<IndexValuesSnapshot> fetchCurrentDateIndexValues(
      LocalDate date, Instant snapshotTime) {
    Instant createdAt = clock.instant();

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
