package ee.tuleva.onboarding.audit.health;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

@Repository
@Slf4j
public class AuditHealthRepository {

  @PersistenceContext private EntityManager entityManager;

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
    return new AuditLogInterval() {
      @Override
      public Double getMaxIntervalSeconds() {
        return resultSeconds;
      }
    };
  }

  public Optional<Instant> findLastAuditEventTimestamp() {
    Query query = entityManager.createNativeQuery(LAST_EVENT_TIMESTAMP_SQL);
    Object result = query.getSingleResult();
    return convertDbResultToInstant(result);
  }

  Optional<Instant> convertDbResultToInstant(Object result) {
    if (result == null) {
      return Optional.empty();
    }

    if (result instanceof Timestamp) {
      return Optional.of(((Timestamp) result).toInstant());
    } else if (result instanceof OffsetDateTime) {
      return Optional.of(((OffsetDateTime) result).toInstant());
    } else if (result instanceof Instant) {
      return Optional.of((Instant) result);
    } else {
      log.warn(
          "Unexpected object type for last audit event timestamp: {}", result.getClass().getName());
      try {
        return Optional.of(Instant.parse(result.toString()));
      } catch (Exception e) {
        log.error(
            "Failed to convert last audit event timestamp of type {} to Instant.",
            result.getClass().getName(),
            e);
        return Optional.empty();
      }
    }
  }
}
