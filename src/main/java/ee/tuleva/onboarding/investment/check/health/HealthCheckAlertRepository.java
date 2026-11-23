package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckAlertRepository.Status.GIVEN_UP;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckAlertRepository.Status.NOT_POSTED;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckAlertRepository.Status.POSTED;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.investment.report.ReportProvider;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
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

  private final JdbcClient jdbcClient;

  long recordNotPosted(
      ReportProvider provider, LocalDate reportDate, String message, Instant foundAt) {
    var keyHolder = new GeneratedKeyHolder();
    jdbcClient
        .sql(
            """
            INSERT INTO investment_health_check_alert
              (provider, report_date, message, found_at, status)
            VALUES (:provider, :reportDate, :message, :foundAt, :status)
            """)
        .param("provider", provider.name())
        .param("reportDate", reportDate)
        .param("message", message)
        .param("foundAt", Timestamp.from(foundAt))
        .param("status", NOT_POSTED.name())
        .update(keyHolder, "id");
    return requireNonNull(keyHolder.getKey(), "Health check alert insert returned no id")
        .longValue();
  }

  List<HealthCheckAlert> findNotPostedFoundBefore(Instant cutoff) {
    return jdbcClient
        .sql(
            """
            SELECT id, provider, report_date, message, found_at
            FROM investment_health_check_alert
            WHERE status = :status AND found_at < :cutoff
            ORDER BY found_at, id
            """)
        .param("status", NOT_POSTED.name())
        .param("cutoff", Timestamp.from(cutoff))
        .query(
            (rs, rowNum) ->
                new HealthCheckAlert(
                    rs.getLong("id"),
                    ReportProvider.valueOf(rs.getString("provider")),
                    rs.getObject("report_date", LocalDate.class),
                    rs.getString("message"),
                    rs.getTimestamp("found_at").toInstant()))
        .list();
  }

  boolean markPosted(long id, Instant postedAt) {
    return jdbcClient
            .sql(
                """
                UPDATE investment_health_check_alert
                SET status = :posted, posted_at = :postedAt
                WHERE id = :id AND status = :notPosted
                """)
            .param("posted", POSTED.name())
            .param("postedAt", Timestamp.from(postedAt))
            .param("id", id)
            .param("notPosted", NOT_POSTED.name())
            .update()
        == 1;
  }

  boolean giveUp(long id) {
    return jdbcClient
            .sql(
                """
                UPDATE investment_health_check_alert
                SET status = :givenUp
                WHERE id = :id AND status = :notPosted
                """)
            .param("givenUp", GIVEN_UP.name())
            .param("id", id)
            .param("notPosted", NOT_POSTED.name())
            .update()
        == 1;
  }
}
