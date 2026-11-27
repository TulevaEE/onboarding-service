package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckAlertRepository.Status.GIVEN_UP;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckAlertRepository.Status.NOT_POSTED;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckAlertRepository.Status.POSTED;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.investment.report.ReportProvider;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class HealthCheckAlertRepository {

  enum Status {
    NOT_POSTED,
    POSTED,
    GIVEN_UP
  }

  private static final int FIRST_ATTEMPT = 1;

  private final JdbcClient jdbcClient;

  long recordNotPosted(
      ReportProvider provider, LocalDate reportDate, String message, Instant foundAt) {
    var keyHolder = new GeneratedKeyHolder();
    jdbcClient
        .sql(
            """
            INSERT INTO investment_health_check_alert
              (provider, report_date, message, found_at, status)
            VALUES (:provider, :reportDate, :message, :foundAt, :notPosted)
            """)
        .param("provider", provider.name())
        .param("reportDate", reportDate)
        .param("message", message)
        .param("foundAt", Timestamp.from(foundAt))
        .param("notPosted", NOT_POSTED.name())
        .update(keyHolder, "id");
    return requireNonNull(keyHolder.getKey(), "Health check alert insert returned no id")
        .longValue();
  }

  List<HealthCheckAlert> findNotPosted() {
    return jdbcClient
        .sql(
            """
            SELECT id, provider, report_date, message, found_at, attempts, last_attempt_at
            FROM investment_health_check_alert
            WHERE status = :notPosted
            ORDER BY found_at, id
            """)
        .param("notPosted", NOT_POSTED.name())
        .query(
            (rs, rowNum) ->
                new HealthCheckAlert(
                    rs.getLong("id"),
                    ReportProvider.valueOf(rs.getString("provider")),
                    rs.getObject("report_date", LocalDate.class),
                    rs.getString("message"),
                    rs.getTimestamp("found_at").toInstant(),
                    rs.getInt("attempts"),
                    lastAttemptAt(rs)))
        .list();
  }

  boolean claimAttempt(long id, int seenAttempts, Instant at) {
    return whileNotPostedAt(id, seenAttempts, "attempts = attempts + 1, last_attempt_at = :at")
            .param("at", Timestamp.from(at))
            .update()
        == 1;
  }

  boolean refundAttempt(long id, int claimedAttempts, Instant previousAttemptAt) {
    return whileNotPostedAt(
                id,
                claimedAttempts,
                "attempts = attempts - 1, last_attempt_at = :previousAttemptAt")
            .param("previousAttemptAt", Timestamp.from(previousAttemptAt))
            .update()
        == 1;
  }

  boolean refundFirstAttempt(long id) {
    return whileNotPostedAt(id, FIRST_ATTEMPT, "attempts = 0, last_attempt_at = NULL").update()
        == 1;
  }

  boolean markPosted(long id, Instant postedAt) {
    return whileNotPosted(id, "status = :posted, posted_at = :postedAt")
            .param("posted", POSTED.name())
            .param("postedAt", Timestamp.from(postedAt))
            .update()
        == 1;
  }

  boolean giveUp(long id) {
    return whileNotPosted(id, "status = :givenUp").param("givenUp", GIVEN_UP.name()).update() == 1;
  }

  private StatementSpec whileNotPosted(long id, String assignments) {
    return jdbcClient
        .sql(
            "UPDATE investment_health_check_alert SET "
                + assignments
                + " WHERE id = :id AND status = :notPosted")
        .param("id", id)
        .param("notPosted", NOT_POSTED.name());
  }

  private StatementSpec whileNotPostedAt(long id, int attempts, String assignments) {
    return jdbcClient
        .sql(
            "UPDATE investment_health_check_alert SET "
                + assignments
                + " WHERE id = :id AND status = :notPosted AND attempts = :attempts")
        .param("id", id)
        .param("notPosted", NOT_POSTED.name())
        .param("attempts", attempts);
  }

  private static @Nullable Instant lastAttemptAt(ResultSet rs) throws SQLException {
    var lastAttemptAt = rs.getTimestamp("last_attempt_at");
    return lastAttemptAt == null ? null : lastAttemptAt.toInstant();
  }
}
