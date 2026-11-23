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
  void findsAlertsNotPostedThatWereFoundBeforeTheCutoffOldestFirst() {
    var later = repository.recordNotPosted(SEB, REPORT_DATE, "later", FIVE_PAST_NINE);
    var earlier = repository.recordNotPosted(SEB, REPORT_DATE, "earlier", NINE);
    repository.recordNotPosted(SEB, REPORT_DATE, "at the cutoff", TEN_PAST_NINE);
    var posted = repository.recordNotPosted(SEB, REPORT_DATE, "posted", NINE);
    var givenUp = repository.recordNotPosted(SEB, REPORT_DATE, "given up", NINE);
    repository.markPosted(posted, NINE);
    repository.giveUp(givenUp);

    var notPosted = repository.findNotPostedFoundBefore(TEN_PAST_NINE);

    assertThat(notPosted)
        .containsExactly(
            new HealthCheckAlert(earlier, SEB, REPORT_DATE, "earlier", NINE),
            new HealthCheckAlert(later, SEB, REPORT_DATE, "later", FIVE_PAST_NINE));
  }

  @Test
  void anAlertIsPostedOnceAndRecordsWhen() {
    var id = repository.recordNotPosted(SEB, REPORT_DATE, "warning", NINE);

    var first = repository.markPosted(id, FIVE_PAST_NINE);
    var second = repository.markPosted(id, TEN_PAST_NINE);

    assertThat(List.of(first, second)).containsExactly(true, false);
    assertThat(statusAndPostedAt(id)).isEqualTo("POSTED " + FIVE_PAST_NINE);
  }

  @Test
  void aGivenUpAlertIsNeitherPostedNorGivenUpAgain() {
    var id = repository.recordNotPosted(SEB, REPORT_DATE, "warning", NINE);

    var givenUp = repository.giveUp(id);
    var postedAfter = repository.markPosted(id, TEN_PAST_NINE);
    var givenUpAgain = repository.giveUp(id);

    assertThat(List.of(givenUp, postedAfter, givenUpAgain)).containsExactly(true, false, false);
    assertThat(statusAndPostedAt(id)).isEqualTo("GIVEN_UP null");
  }

  @Test
  void aPostedAlertIsNotGivenUp() {
    var id = repository.recordNotPosted(SEB, REPORT_DATE, "warning", NINE);
    repository.markPosted(id, FIVE_PAST_NINE);

    var givenUp = repository.giveUp(id);

    assertThat(givenUp).isFalse();
    assertThat(statusAndPostedAt(id)).isEqualTo("POSTED " + FIVE_PAST_NINE);
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*(pg|ci).*")
  void theTableTakesOnlyTheThreeStatuses() {
    var id = repository.recordNotPosted(SEB, REPORT_DATE, "warning", NINE);

    assertThatThrownBy(() -> setStatus(id, "'CLAIMED'", "NULL"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*(pg|ci).*")
  void aPostedAlertCarriesItsPostedTime() {
    var id = repository.recordNotPosted(SEB, REPORT_DATE, "warning", NINE);

    assertThatThrownBy(() -> setStatus(id, "'POSTED'", "NULL"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*(pg|ci).*")
  void aGivenUpAlertCarriesNoPostedTime() {
    var id = repository.recordNotPosted(SEB, REPORT_DATE, "warning", NINE);

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

  private String statusAndPostedAt(long id) {
    return jdbcClient
        .sql("SELECT status, posted_at FROM investment_health_check_alert WHERE id = :id")
        .param("id", id)
        .query(
            (rs, rowNum) -> {
              var postedAt = rs.getTimestamp("posted_at");
              return rs.getString("status")
                  + " "
                  + (postedAt == null ? null : postedAt.toInstant());
            })
        .single();
  }
}
