package ee.tuleva.onboarding.instrument;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class InstrumentReferenceHistoryRepository {

  private final JdbcClient jdbcClient;
  private final Clock clock;

  public List<InstrumentReferenceChange> unnotifiedChanges() {
    return jdbcClient
        .sql(
            "SELECT id, isin, operation, changed_by, changed_at, old_values, new_values"
                + " FROM instrument_reference_history WHERE notified_at IS NULL ORDER BY id")
        .query(
            (rs, rowNum) ->
                new InstrumentReferenceChange(
                    rs.getLong("id"),
                    rs.getString("isin"),
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
        .sql("UPDATE instrument_reference_history SET notified_at = :notifiedAt WHERE id IN (:ids)")
        .param("notifiedAt", Timestamp.from(clock.instant()))
        .param("ids", ids)
        .update();
  }
}
