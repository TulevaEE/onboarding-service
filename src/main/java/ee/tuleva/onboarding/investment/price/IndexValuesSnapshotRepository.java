package ee.tuleva.onboarding.investment.price;

import static java.time.ZoneOffset.UTC;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class IndexValuesSnapshotRepository {

  private final JdbcClient jdbcClient;

  public void saveAll(List<IndexValuesSnapshot> snapshots) {
    for (IndexValuesSnapshot snapshot : snapshots) {
      jdbcClient
          .sql(
              """
              INSERT INTO index_values_snapshot
              (snapshot_time, key, date, value, provider, source_updated_at, created_at)
              VALUES (:snapshotTime, :key, :date, :value, :provider, :sourceUpdatedAt, :createdAt)
              """)
          .param("snapshotTime", OffsetDateTime.ofInstant(snapshot.snapshotTime(), UTC))
          .param("key", snapshot.key())
          .param("date", snapshot.date())
          .param("value", snapshot.value())
          .param("provider", snapshot.provider())
          .param("sourceUpdatedAt", OffsetDateTime.ofInstant(snapshot.sourceUpdatedAt(), UTC))
          .param("createdAt", OffsetDateTime.ofInstant(snapshot.createdAt(), UTC))
          .update();
    }
  }
}
