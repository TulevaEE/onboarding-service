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

  private final JdbcClient jdbcClient;

  boolean anyNotPosted() {
    return jdbcClient
            .sql("SELECT COUNT(*) FROM investment_health_check_alert WHERE status = :notPosted")
            .param("notPosted", NOT_POSTED.name())
            .query(Long.class)
            .single()
        > 0;
  }

  long recordNotPosted(
      ReportProvider provider,
      LocalDate reportDate,
      String message,
      Instant foundAt,
      int unansweredPosts) {
    var keyHolder = new GeneratedKeyHolder();
    jdbcClient
        .sql(
            """
            INSERT INTO investment_health_check_alert
              (provider, report_date, message, found_at, status, unanswered_posts)
            VALUES (:provider, :reportDate, :message, :foundAt, :notPosted, :unansweredPosts)
            """)
        .param("provider", provider.name())
        .param("reportDate", reportDate)
        .param("message", message)
        .param("foundAt", Timestamp.from(foundAt))
        .param("notPosted", NOT_POSTED.name())
        .param("unansweredPosts", unansweredPosts)
        .update(keyHolder, "id");
    return requireNonNull(keyHolder.getKey(), "Health check alert insert returned no id")
        .longValue();
  }

  List<HealthCheckAlert> findNotPosted() {
    return jdbcClient
        .sql(
            """
            SELECT id, provider, report_date, message, found_at, unanswered_posts
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
                    rs.getInt("unanswered_posts")))
        .list();
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

  boolean recordUnansweredPost(long id) {
    return whileNotPosted(id, "unanswered_posts = unanswered_posts + 1").update() == 1;
  }

  boolean giveUpAfterUnansweredPost(long id) {
    return whileNotPosted(id, "status = :givenUp, unanswered_posts = unanswered_posts + 1")
            .param("givenUp", GIVEN_UP.name())
            .update()
        == 1;
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
}
