package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@Import(HealthCheckAlertRepository.class)
class HealthCheckAlertRepositoryTest {

  private static final LocalDate REPORT_DATE = LocalDate.of(2026, 10, 30);
  private static final Instant NINE = Instant.parse("2026-10-31T07:00:00Z");
  private static final Instant FIVE_PAST_NINE = Instant.parse("2026-10-31T07:05:00Z");
  private static final Instant TEN_PAST_NINE = Instant.parse("2026-10-31T07:10:00Z");

  @Autowired HealthCheckAlertRepository repository;
  @Autowired JdbcClient jdbcClient;

  @Test
  void findsEveryAlertNotPostedOldestFirstWithItsUnansweredPosts() {
    var later = repository.recordNotPosted(SEB, REPORT_DATE, "later", TEN_PAST_NINE, 0);
    var earlier = repository.recordNotPosted(SEB, REPORT_DATE, "earlier", NINE, 1);
    var posted = repository.recordNotPosted(SEB, REPORT_DATE, "posted", NINE, 0);
    var givenUp = repository.recordNotPosted(SEB, REPORT_DATE, "given up", NINE, 0);
    repository.markPosted(posted, FIVE_PAST_NINE);
    repository.giveUp(givenUp);

    var notPosted = repository.findNotPosted();

    assertThat(notPosted)
        .containsExactly(
            new HealthCheckAlert(earlier, SEB, REPORT_DATE, "earlier", NINE, 1),
            new HealthCheckAlert(later, SEB, REPORT_DATE, "later", TEN_PAST_NINE, 0));
  }

  @Test
  void knowsWhetherAnyAlertIsWaitingToBePosted() {
    var noneRecorded = repository.anyNotPosted();
    var id = repository.recordNotPosted(SEB, REPORT_DATE, "warning", NINE, 0);
    var oneWaiting = repository.anyNotPosted();
    repository.markPosted(id, FIVE_PAST_NINE);
    var itWasPosted = repository.anyNotPosted();

    assertThat(List.of(noneRecorded, oneWaiting, itWasPosted)).containsExactly(false, true, false);
  }

  @Test
  void anAlertIsPostedOnceAndRecordsWhen() {
    var id = repository.recordNotPosted(SEB, REPORT_DATE, "warning", NINE, 0);

    var first = repository.markPosted(id, FIVE_PAST_NINE);
    var second = repository.markPosted(id, TEN_PAST_NINE);

    assertThat(List.of(first, second)).containsExactly(true, false);
    assertThat(row(id)).isEqualTo("POSTED at " + FIVE_PAST_NINE + ", unanswered 0");
  }

  @Test
  void aGivenUpAlertIsNeitherPostedNorGivenUpAgain() {
    var id = repository.recordNotPosted(SEB, REPORT_DATE, "warning", NINE, 0);

    var givenUp = repository.giveUp(id);
    var postedAfter = repository.markPosted(id, TEN_PAST_NINE);
    var givenUpAgain = repository.giveUp(id);

    assertThat(List.of(givenUp, postedAfter, givenUpAgain)).containsExactly(true, false, false);
    assertThat(row(id)).isEqualTo("GIVEN_UP at null, unanswered 0");
  }

  @Test
  void aPostedAlertIsNotGivenUp() {
    var id = repository.recordNotPosted(SEB, REPORT_DATE, "warning", NINE, 0);
    repository.markPosted(id, FIVE_PAST_NINE);

    var givenUp = repository.giveUp(id);

    assertThat(givenUp).isFalse();
    assertThat(row(id)).isEqualTo("POSTED at " + FIVE_PAST_NINE + ", unanswered 0");
  }

  @Test
  void anUnansweredPostIsCountedOnAnAlertStillWaiting() {
    var id = repository.recordNotPosted(SEB, REPORT_DATE, "warning", NINE, 0);

    var counted = repository.recordUnansweredPost(id);

    assertThat(counted).isTrue();
    assertThat(row(id)).isEqualTo("NOT_POSTED at null, unanswered 1");
  }

  @Test
  void anAlertGivenUpAfterAnUnansweredPostCountsThatPost() {
    var id = repository.recordNotPosted(SEB, REPORT_DATE, "warning", NINE, 1);

    var givenUp = repository.giveUpAfterUnansweredPost(id);
    var givenUpAgain = repository.giveUpAfterUnansweredPost(id);
    var countedAfter = repository.recordUnansweredPost(id);

    assertThat(List.of(givenUp, givenUpAgain, countedAfter)).containsExactly(true, false, false);
    assertThat(row(id)).isEqualTo("GIVEN_UP at null, unanswered 2");
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*(pg|ci).*")
  void theTableTakesOnlyTheThreeStatuses() {
    var id = repository.recordNotPosted(SEB, REPORT_DATE, "warning", NINE, 0);

    assertThatThrownBy(() -> setStatus(id, "'CLAIMED'", "NULL"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*(pg|ci).*")
  void aPostedAlertCarriesItsPostedTime() {
    var id = repository.recordNotPosted(SEB, REPORT_DATE, "warning", NINE, 0);

    assertThatThrownBy(() -> setStatus(id, "'POSTED'", "NULL"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*(pg|ci).*")
  void aGivenUpAlertCarriesNoPostedTime() {
    var id = repository.recordNotPosted(SEB, REPORT_DATE, "warning", NINE, 0);

    assertThatThrownBy(() -> setStatus(id, "'GIVEN_UP'", "CURRENT_TIMESTAMP"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void setStatus(long id, String status, String postedAt) {
    jdbcClient
        .sql(
            "UPDATE investment_health_check_alert SET status = %s, posted_at = %s WHERE id = :id"
                .formatted(status, postedAt))
        .param("id", id)
        .update();
  }

  private String row(long id) {
    return jdbcClient
        .sql(
            "SELECT status, posted_at, unanswered_posts FROM investment_health_check_alert"
                + " WHERE id = :id")
        .param("id", id)
        .query(
            (rs, rowNum) -> {
              var postedAt = rs.getTimestamp("posted_at");
              return "%s at %s, unanswered %d"
                  .formatted(
                      rs.getString("status"),
                      postedAt == null ? null : postedAt.toInstant(),
                      rs.getInt("unanswered_posts"));
            })
        .single();
  }
}
