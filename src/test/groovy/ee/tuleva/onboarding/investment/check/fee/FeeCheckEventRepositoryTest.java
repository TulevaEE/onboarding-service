package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.investment.check.fee.FeeCheckScope.ALL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckScope.MANAGEMENT;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.INFO;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.PASS;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.CUSTODIAN_POSITION_COMPLETENESS;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.FEE_BASE_COMPLETENESS;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.SETTLEMENT_COMPLETENESS;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Limit;

@DataJpaTest
class FeeCheckEventRepositoryTest {

  private static final LocalDate MAY = LocalDate.of(2026, 5, 1);
  private static final LocalDate JUNE = LocalDate.of(2026, 6, 1);
  private static final Instant BASE_TIME = Instant.parse("2026-06-03T07:00:00Z");

  @Autowired FeeCheckEventRepository repository;

  private int saved = 0;

  // V1_179 had to drop the unique constraints from all three existing check-event tables because
  // the notifier diffs the two latest rows. This table must not reintroduce one.
  @Test
  void twoRunsOnTheSameDayWithTheSameKeyBothPersist() {
    save(FEE_BASE_COMPLETENESS, ALL, null, PASS);
    save(FEE_BASE_COMPLETENESS, ALL, null, FAIL);

    assertThat(repository.findAll()).hasSize(2);
  }

  // The whole anti-noise design rests on these two never seeing each other's rows: a null argument
  // to the fee-month method renders as "= ?" and would match nothing, so a shared method would
  // default every daily check to PASS and re-alert a persisting deviation every single day.
  @Test
  void theDailyQueryOnlySeesRowsWithoutAFeeMonth() {
    save(FEE_BASE_COMPLETENESS, ALL, null, FAIL);
    save(FEE_BASE_COMPLETENESS, ALL, MAY, PASS);

    var found = repository.findLatestDelivered(TUK75, FEE_BASE_COMPLETENESS, ALL, Limit.of(2));

    assertThat(found).singleElement().satisfies(e -> assertThat(e.getSeverity()).isEqualTo(FAIL));
  }

  @Test
  void theMonthlyQueryOnlySeesRowsForThatFeeMonth() {
    save(SETTLEMENT_COMPLETENESS, MANAGEMENT, MAY, FAIL);
    save(SETTLEMENT_COMPLETENESS, MANAGEMENT, JUNE, PASS);
    save(SETTLEMENT_COMPLETENESS, MANAGEMENT, null, PASS);

    var found =
        repository.findLatestDeliveredForFeeMonth(
            TUK75, SETTLEMENT_COMPLETENESS, MANAGEMENT, MAY, Limit.of(2));

    assertThat(found).singleElement().satisfies(e -> assertThat(e.getFeeMonth()).isEqualTo(MAY));
  }

  // A new month must start its own transition history, or a June failure following a May failure
  // reads as "same severity" and goes silent.
  @Test
  void eachFeeMonthKeepsItsOwnHistory() {
    save(SETTLEMENT_COMPLETENESS, MANAGEMENT, MAY, PASS);
    save(SETTLEMENT_COMPLETENESS, MANAGEMENT, MAY, FAIL);
    save(SETTLEMENT_COMPLETENESS, MANAGEMENT, JUNE, FAIL);

    var june =
        repository.findLatestDeliveredForFeeMonth(
            TUK75, SETTLEMENT_COMPLETENESS, MANAGEMENT, JUNE, Limit.of(2));

    assertThat(june).hasSize(1);
  }

  @Test
  void theTwoLatestRowsComeBackNewestFirst() {
    save(FEE_BASE_COMPLETENESS, ALL, null, PASS);
    save(FEE_BASE_COMPLETENESS, ALL, null, FAIL);
    save(FEE_BASE_COMPLETENESS, ALL, null, PASS);

    var found = repository.findLatestDelivered(TUK75, FEE_BASE_COMPLETENESS, ALL, Limit.of(2));

    assertThat(found).extracting(FeeCheckEvent::getSeverity).containsExactly(PASS, FAIL);
  }

  // A row whose alert never reached anyone must not become the baseline, or the deviation that
  // first appeared during the outage is treated as already-reported and stays silent.
  @Test
  void aRowWhoseAlertWasNeverDeliveredIsSkipped() {
    save(FEE_BASE_COMPLETENESS, ALL, null, PASS);
    saveUndelivered(FEE_BASE_COMPLETENESS, ALL, FAIL);

    var found = repository.findLatestDelivered(TUK75, FEE_BASE_COMPLETENESS, ALL, Limit.of(2));

    assertThat(found).extracting(FeeCheckEvent::getSeverity).containsExactly(PASS);
  }

  // Two rows written close enough together to share a timestamp must still come back newest
  // first, or the baseline the notifier diffs against is whichever the database happened to
  // return - and the same state could alert or stay silent from one run to the next.
  @Test
  void rowsSharingATimestampAreOrderedByIdSoTheLatestStillWins() {
    saveAt(BASE_TIME, PASS);
    saveAt(BASE_TIME, FAIL);

    var found = repository.findLatestDelivered(TUK75, FEE_BASE_COMPLETENESS, ALL, Limit.of(2));

    assertThat(found).extracting(FeeCheckEvent::getSeverity).containsExactly(FAIL, PASS);
  }

  @Test
  void nestedResultDetailSurvivesTheJsonRoundTrip() {
    var detail = Map.<String, Object>of("mismatches", Map.of("2026-05-04", "missing=44980.96"));
    var event = event(FEE_BASE_COMPLETENESS, ALL, null, FAIL);
    event.setResult(detail);

    repository.saveAndFlush(event);

    assertThat(repository.findAll().getFirst().getResult()).isEqualTo(detail);
  }

  @Test
  void aFundWithNothingOutstandingHasNoUnresolvedDeviationDate() {
    saveOn(LocalDate.of(2026, 6, 1), FEE_BASE_COMPLETENESS, ALL, PASS);

    assertThat(repository.findOldestUnresolvedDailyDeviationDate(TUK75)).isEmpty();
  }

  @Test
  void theOldestOutstandingDeviationIsTheOneStillUncleared() {
    saveOn(LocalDate.of(2026, 6, 1), FEE_BASE_COMPLETENESS, ALL, FAIL);
    saveOn(LocalDate.of(2026, 6, 2), FEE_BASE_COMPLETENESS, ALL, FAIL);

    assertThat(repository.findOldestUnresolvedDailyDeviationDate(TUK75))
        .contains(LocalDate.of(2026, 6, 1));
  }

  @Test
  void aDeviationFollowedByACleanRunIsNoLongerOutstanding() {
    saveOn(LocalDate.of(2026, 6, 1), FEE_BASE_COMPLETENESS, ALL, FAIL);
    saveOn(LocalDate.of(2026, 6, 2), FEE_BASE_COMPLETENESS, ALL, PASS);

    assertThat(repository.findOldestUnresolvedDailyDeviationDate(TUK75)).isEmpty();
  }

  // A clean run only clears its own check type and scope. One leg recovering must not shorten the
  // window the other leg still needs.
  @Test
  void aCleanRunOnOneCheckDoesNotClearAnother() {
    saveOn(LocalDate.of(2026, 6, 1), FEE_BASE_COMPLETENESS, ALL, FAIL);
    saveOn(LocalDate.of(2026, 6, 2), SETTLEMENT_COMPLETENESS, MANAGEMENT, PASS);

    assertThat(repository.findOldestUnresolvedDailyDeviationDate(TUK75))
        .contains(LocalDate.of(2026, 6, 1));
  }

  @Test
  void aMonthlyDeviationDoesNotWidenTheDailyWindow() {
    var event = event(SETTLEMENT_COMPLETENESS, MANAGEMENT, MAY, FAIL);
    event.setCheckDate(LocalDate.of(2026, 6, 1));
    event.setCreatedAt(BASE_TIME.plusSeconds(saved++));
    repository.saveAndFlush(event);

    assertThat(repository.findOldestUnresolvedDailyDeviationDate(TUK75)).isEmpty();
  }

  // A day the check could not look at found no deviation, which is not the same claim as there
  // being none. Letting it clear the window closes an unfixed deviation, and the next run that
  // no longer covers the divergent date reports the whole thing as cleared.
  @Test
  void aRunThatCouldNotCheckDoesNotResolveAnOpenDeviation() {
    saveOn(LocalDate.of(2026, 6, 1), FEE_BASE_COMPLETENESS, ALL, FAIL);
    saveOn(LocalDate.of(2026, 6, 2), FEE_BASE_COMPLETENESS, ALL, NOT_RUN);

    assertThat(repository.findOldestUnresolvedDailyDeviationDate(TUK75))
        .contains(LocalDate.of(2026, 6, 1));
  }

  // A coverage gap only defers the question. Once a run does look and comes back clean, the
  // deviation is resolved and the window must narrow again.
  @Test
  void aCleanRunAfterADayTheCheckCouldNotRunStillResolvesTheDeviation() {
    saveOn(LocalDate.of(2026, 6, 1), FEE_BASE_COMPLETENESS, ALL, FAIL);
    saveOn(LocalDate.of(2026, 6, 2), FEE_BASE_COMPLETENESS, ALL, NOT_RUN);
    saveOn(LocalDate.of(2026, 6, 3), FEE_BASE_COMPLETENESS, ALL, PASS);

    assertThat(repository.findOldestUnresolvedDailyDeviationDate(TUK75)).isEmpty();
  }

  // INFO means we looked, found a difference and it needs no correction. That resolves the
  // deviation exactly as a PASS does.
  @Test
  void aDeviationExplainedAsNeedingNoActionIsNoLongerOutstanding() {
    saveOn(LocalDate.of(2026, 6, 1), CUSTODIAN_POSITION_COMPLETENESS, ALL, FAIL);
    saveOn(LocalDate.of(2026, 6, 2), CUSTODIAN_POSITION_COMPLETENESS, ALL, INFO);

    assertThat(repository.findOldestUnresolvedDailyDeviationDate(TUK75)).isEmpty();
  }

  // Not looking is not a deviation either: a coverage gap must not widen the window on its own,
  // or every fund with no position report yet would drag the window back indefinitely.
  @Test
  void aRunThatCouldNotCheckIsNotItselfAnOpenDeviation() {
    saveOn(LocalDate.of(2026, 6, 1), FEE_BASE_COMPLETENESS, ALL, NOT_RUN);

    assertThat(repository.findOldestUnresolvedDailyDeviationDate(TUK75)).isEmpty();
  }

  private void saveOn(
      LocalDate checkDate, FeeCheckType checkType, FeeCheckScope scope, FeeCheckSeverity severity) {
    var event = event(checkType, scope, null, severity);
    event.setCheckDate(checkDate);
    event.setCreatedAt(BASE_TIME.plusSeconds(saved++));
    repository.saveAndFlush(event);
  }

  // createdAt is set explicitly and distinctly: the notifier orders on it, and rows written in the
  // same run would otherwise be free to come back in either order.
  private void save(
      FeeCheckType checkType,
      FeeCheckScope scope,
      @Nullable LocalDate feeMonth,
      FeeCheckSeverity severity) {
    var event = event(checkType, scope, feeMonth, severity);
    event.setCreatedAt(BASE_TIME.plusSeconds(saved++));
    repository.saveAndFlush(event);
  }

  private void saveAt(Instant createdAt, FeeCheckSeverity severity) {
    var event = event(FEE_BASE_COMPLETENESS, ALL, null, severity);
    event.setCreatedAt(createdAt);
    repository.saveAndFlush(event);
  }

  private void saveUndelivered(
      FeeCheckType checkType, FeeCheckScope scope, FeeCheckSeverity severity) {
    var event = event(checkType, scope, null, severity);
    event.setCreatedAt(BASE_TIME.plusSeconds(saved++));
    event.setAlertFailed(true);
    repository.saveAndFlush(event);
  }

  private FeeCheckEvent event(
      FeeCheckType checkType,
      FeeCheckScope scope,
      @Nullable LocalDate feeMonth,
      FeeCheckSeverity severity) {
    return FeeCheckEvent.builder()
        .fund(TUK75)
        .checkDate(LocalDate.of(2026, 6, 3))
        .feeMonth(feeMonth)
        .checkType(checkType)
        .feeScope(scope)
        .severity(severity)
        .deviationFound(severity == WARNING || severity == FAIL)
        .result(Map.of())
        .build();
  }
}
