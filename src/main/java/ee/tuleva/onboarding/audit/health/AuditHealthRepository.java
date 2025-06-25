package ee.tuleva.onboarding.audit.health;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

@Repository
@Slf4j
@RequiredArgsConstructor
public class AuditHealthRepository {

  @PersistenceContext private final EntityManager entityManager;

  private static final String LONGEST_INTERVAL_SQL =
      """
        WITH ordered_logs AS (
            SELECT
                action_tstamp_tx,
                LAG(action_tstamp_tx, 1) OVER (ORDER BY action_tstamp_tx ASC) AS prev_action_tstamp_tx
            FROM
                audit.logged_actions
            WHERE
                action_tstamp_tx >= :sinceTime
        ),
        time_diffs AS (
            SELECT
                EXTRACT(EPOCH FROM (action_tstamp_tx - prev_action_tstamp_tx)) AS interval_seconds
            FROM
                ordered_logs
            WHERE
                prev_action_tstamp_tx IS NOT NULL
        ),
        positive_intervals AS (
            SELECT interval_seconds
            FROM time_diffs
            WHERE interval_seconds IS NOT NULL AND interval_seconds > 0
        )
        SELECT
            CAST(COALESCE(MAX(pi.interval_seconds), 0) AS DOUBLE PRECISION) AS maxIntervalSeconds
        FROM
            positive_intervals pi
        """;

  private static final String LAST_EVENT_TIMESTAMP_SQL =
      "SELECT MAX(la.action_tstamp_tx) FROM audit.logged_actions la";

  public AuditLogInterval findLongestIntervalSecondsSince(Instant sinceTime) {
    Query query = entityManager.createNativeQuery(LONGEST_INTERVAL_SQL);
    query.setParameter("sinceTime", sinceTime);
    Number singleResult = (Number) query.getSingleResult();
    final Double resultSeconds = singleResult.doubleValue();
    return new AuditLogInterval(resultSeconds);
  }

  public Optional<Instant> findLastAuditEventTimestamp() {
    try {
      Object resultObject =
          entityManager
              .createNativeQuery(LAST_EVENT_TIMESTAMP_SQL, Instant.class)
              .getSingleResult();

      if (resultObject == null) {
        return Optional.empty();
      }
      return Optional.of((Instant) resultObject);
    } catch (Exception e) {
      log.warn("Could not retrieve last audit event timestamp. Error: {}", e.getMessage(), e);
      return Optional.empty();
    }
  }
}
