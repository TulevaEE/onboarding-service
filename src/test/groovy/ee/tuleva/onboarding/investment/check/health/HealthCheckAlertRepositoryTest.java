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
  void findsEveryAlertNotPostedOldestFirstWithItsAttempts() {
    var later = repository.recordNotPosted(SEB, REPORT_DATE, "later", TEN_PAST_NINE);
    var earlier = repository.recordNotPosted(SEB, REPORT_DATE, "earlier", NINE);
    var posted = repository.recordNotPosted(SEB, REPORT_DATE, "posted", NINE);
    var givenUp = repository.recordNotPosted(SEB, REPORT_DATE, "given up", NINE);
    repository.claimAttempt(earlier, 0, FIVE_PAST_NINE);
    repository.markPosted(posted, FIVE_PAST_NINE);
    repository.giveUp(givenUp);

    var notPosted = repository.findNotPosted();

    assertThat(notPosted)
        .containsExactly(
            new HealthCheckAlert(earlier, SEB, REPORT_DATE, "earlier", NINE, 1, FIVE_PAST_NINE),
            new HealthCheckAlert(later, SEB, REPORT_DATE, "later", TEN_PAST_NINE, 0, null));
  }

  @Test
  void aSavedAlertWaitsWithNoAttempts() {
    var id = repository.recordNotPosted(SEB, REPORT_DATE, "warning", NINE);

    assertThat(row(id)).isEqualTo("NOT_POSTED, attempts 0, last null, posted null");
  }

  @Test
  void aClaimCountsTheAttemptAndItsTimeOnlyWhileTheAlertWaits() {
    var id = repository.recordNotPosted(SEB, REPORT_DATE, "warning", NINE);

    var first = repository.claimAttempt(id, 0, FIVE_PAST_NINE);
    var second = repository.claimAttempt(id, 1, TEN_PAST_NINE);
    repository.giveUp(id);
    var afterGivingUp = repository.claimAttempt(id, 2, TEN_PAST_NINE);

    assertThat(List.of(first, second, afterGivingUp)).containsExactly(true, true, false);
    assertThat(row(id)).isEqualTo("GIVEN_UP, attempts 2, last " + TEN_PAST_NINE + ", posted null");
  }

  @Test
  void aClaimWithAStaleAttemptCountFailsSoTwoRunsCannotBothPostOneAlert() {
    var id = repository.recordNotPosted(SEB, REPORT_DATE, "warning", NINE);

    var firstRun = repository.claimAttempt(id, 0, FIVE_PAST_NINE);
    var overlappingRun = repository.claimAttempt(id, 0, TEN_PAST_NINE);

    assertThat(List.of(firstRun, overlappingRun)).containsExactly(true, false);
    assertThat(row(id))
        .isEqualTo("NOT_POSTED, attempts 1, last " + FIVE_PAST_NINE + ", posted null");
  }

  @Test
  void aRefundTakesTheAttemptBackAndRestoresTheEarlierAttemptTime() {
    var id = repository.recordNotPosted(SEB, REPORT_DATE, "warning", NINE);
    repository.claimAttempt(id, 0, FIVE_PAST_NINE);
    repository.claimAttempt(id, 1, TEN_PAST_NINE);

    var refunded = repository.refundAttempt(id, 2, FIVE_PAST_NINE);

    assertThat(refunded).isTrue();
    assertThat(row(id))
        .isEqualTo("NOT_POSTED, attempts 1, last " + FIVE_PAST_NINE + ", posted null");
  }

  @Test
  void aRefundOfTheFirstAttemptLeavesTheAlertAsIfNeverTried() {
    var id = repository.recordNotPosted(SEB, REPORT_DATE, "warning", NINE);
    repository.claimAttempt(id, 0, FIVE_PAST_NINE);

    var refunded = repository.refundFirstAttempt(id);

    assertThat(refunded).isTrue();
    assertThat(row(id)).isEqualTo("NOT_POSTED, attempts 0, last null, posted null");
  }

  @Test
  void aRefundForAnAttemptCountThatMovedOnChangesNothing() {
    var id = repository.recordNotPosted(SEB, REPORT_DATE, "warning", NINE);
    repository.claimAttempt(id, 0, FIVE_PAST_NINE);
    repository.claimAttempt(id, 1, TEN_PAST_NINE);

    var staleRefund = repository.refundAttempt(id, 1, NINE);
    var staleFirstRefund = repository.refundFirstAttempt(id);

    assertThat(List.of(staleRefund, staleFirstRefund)).containsExactly(false, false);
    assertThat(row(id))
        .isEqualTo("NOT_POSTED, attempts 2, last " + TEN_PAST_NINE + ", posted null");
  }

  @Test
  void anAlertIsPostedOnceAndRecordsWhen() {
    var id = repository.recordNotPosted(SEB, REPORT_DATE, "warning", NINE);
    repository.claimAttempt(id, 0, FIVE_PAST_NINE);

    var first = repository.markPosted(id, FIVE_PAST_NINE);
    var second = repository.markPosted(id, TEN_PAST_NINE);

    assertThat(List.of(first, second)).containsExactly(true, false);
    assertThat(row(id))
        .isEqualTo("POSTED, attempts 1, last " + FIVE_PAST_NINE + ", posted " + FIVE_PAST_NINE);
  }

  @Test
  void aGivenUpAlertIsNeitherPostedNorGivenUpAgainNorRefunded() {
    var id = repository.recordNotPosted(SEB, REPORT_DATE, "warning", NINE);

    var givenUp = repository.giveUp(id);
    var postedAfter = repository.markPosted(id, TEN_PAST_NINE);
    var givenUpAgain = repository.giveUp(id);
    var refundedAfter = repository.refundFirstAttempt(id);

    assertThat(List.of(givenUp, postedAfter, givenUpAgain, refundedAfter))
        .containsExactly(true, false, false, false);
    assertThat(row(id)).isEqualTo("GIVEN_UP, attempts 0, last null, posted null");
  }

  @Test
  void aPostedAlertIsNotGivenUp() {
    var id = repository.recordNotPosted(SEB, REPORT_DATE, "warning", NINE);
    repository.markPosted(id, FIVE_PAST_NINE);

    var givenUp = repository.giveUp(id);

    assertThat(givenUp).isFalse();
    assertThat(row(id)).isEqualTo("POSTED, attempts 0, last null, posted " + FIVE_PAST_NINE);
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

  private String row(long id) {
    return jdbcClient
        .sql(
            "SELECT status, attempts, last_attempt_at, posted_at FROM investment_health_check_alert"
                + " WHERE id = :id")
        .param("id", id)
        .query(
            (rs, rowNum) -> {
              var lastAttemptAt = rs.getTimestamp("last_attempt_at");
              var postedAt = rs.getTimestamp("posted_at");
              return "%s, attempts %d, last %s, posted %s"
                  .formatted(
                      rs.getString("status"),
                      rs.getInt("attempts"),
                      lastAttemptAt == null ? null : lastAttemptAt.toInstant(),
                      postedAt == null ? null : postedAt.toInstant());
            })
        .single();
  }
}
