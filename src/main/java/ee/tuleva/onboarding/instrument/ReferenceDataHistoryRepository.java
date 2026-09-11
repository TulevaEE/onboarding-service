package ee.tuleva.onboarding.instrument;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ReferenceDataHistoryRepository {

  private final JdbcClient jdbcClient;
  private final Clock clock;

  public List<ReferenceDataChange> unnotifiedChanges() {
    return jdbcClient
        .sql(
            "SELECT id, table_name, record_key, operation, changed_by, changed_at, old_values, new_values"
                + " FROM reference_data_history WHERE notified_at IS NULL ORDER BY id")
        .query(
            (rs, rowNum) ->
                new ReferenceDataChange(
                    rs.getLong("id"),
                    rs.getString("table_name"),
                    rs.getString("record_key"),
                    rs.getString("operation"),
                    rs.getString("changed_by"),
                    rs.getTimestamp("changed_at").toInstant(),
                    rs.getString("old_values"),
                    rs.getString("new_values")))
        .list();
  }

  public void markNotified(List<Long> ids) {
    if (ids.isEmpty()) {
      return;
    }
    jdbcClient
        .sql("UPDATE reference_data_history SET notified_at = :notifiedAt WHERE id IN (:ids)")
        .param("notifiedAt", Timestamp.from(clock.instant()))
        .param("ids", ids)
        .update();
  }
}
