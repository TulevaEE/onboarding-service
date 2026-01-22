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

  private static final int PREVIOUS_DAYS_TO_INCLUDE = 7;

  private final JdbcClient jdbcClient;
  private final IndexValuesSnapshotRepository snapshotRepository;

  @Transactional
  public List<IndexValuesSnapshot> createSnapshot() {
    Instant snapshotTime = ClockHolder.clock().instant();
    LocalDate today = LocalDate.now(ClockHolder.clock());
    LocalDate startDate = today.minusDays(PREVIOUS_DAYS_TO_INCLUDE);

    List<LocalDate> datesWithData = findDatesWithData(startDate, today);
    if (datesWithData.isEmpty()) {
      log.warn("No index values found for last {} days", PREVIOUS_DAYS_TO_INCLUDE + 1);
      return List.of();
    }

    log.info(
        "Creating index values snapshot: dateRange={} to {}, datesWithData={}, snapshotTime={}",
        startDate,
        today,
        datesWithData.size(),
        snapshotTime);

    List<IndexValuesSnapshot> snapshots = fetchIndexValuesForDates(datesWithData, snapshotTime);

    if (snapshots.isEmpty()) {
      log.warn("No index values found for dates: dates={}", datesWithData);
      return snapshots;
    }

    snapshotRepository.saveAll(snapshots);

    log.info(
        "Index values snapshot created: dateRange={} to {}, snapshotTime={}, count={}",
        startDate,
        today,
        snapshotTime,
        snapshots.size());

    return snapshots;
  }

  private List<LocalDate> findDatesWithData(LocalDate startDate, LocalDate endDate) {
    return jdbcClient
        .sql(
            """
            SELECT DISTINCT date FROM index_values
            WHERE date >= :startDate AND date <= :endDate
            ORDER BY date
            """)
        .param("startDate", startDate)
        .param("endDate", endDate)
        .query(LocalDate.class)
        .list();
  }

  private List<IndexValuesSnapshot> fetchIndexValuesForDates(
      List<LocalDate> dates, Instant snapshotTime) {
    Instant createdAt = ClockHolder.clock().instant();

    return jdbcClient
        .sql(
            """
            SELECT key, date, value, provider, updated_at
            FROM index_values
            WHERE date IN (:dates)
            """)
        .param("dates", dates)
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
