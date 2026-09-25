package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.TrackingCheckType.MODEL_PORTFOLIO;
import static ee.tuleva.onboarding.investment.check.tracking.TrackingDifferenceJob.GAP_LOOKBACK_DAYS;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.math.BigDecimal.ZERO;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;

import ee.tuleva.onboarding.investment.event.RunTrackingDifferenceBackfillRequested;
import ee.tuleva.onboarding.investment.event.RunTrackingDifferenceCheckRequested;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TrackingDifferenceJobTest {

  private static final LocalDate NAV_DATE = LocalDate.of(2026, 9, 11);

  @Mock TrackingDifferenceService service;
  @Mock TrackingDifferenceNotifier notifier;

  @InjectMocks TrackingDifferenceJob job;

  private static TrackingDifferenceResult result(LocalDate checkDate) {
    return TrackingDifferenceResult.builder()
        .fund(TUK75)
        .checkDate(checkDate)
        .checkType(MODEL_PORTFOLIO)
        .trackingDifference(ZERO)
        .fundReturn(ZERO)
        .benchmarkReturn(ZERO)
        .breach(false)
        .build();
  }

  @Test
  void adHocEventDelegatesToServiceAndNotifier() {
    var results = List.<TrackingDifferenceResult>of();
    given(service.runChecksForFunds(anyList())).willReturn(results);

    job.onTrackingDifferenceCheckRequested(new RunTrackingDifferenceCheckRequested());

    then(service).should().runChecksForFunds(anyList());
    then(notifier).should().notify(results);
  }

  // A check that threw did not run. Logging that and saying nothing leaves the last Slack
  // message on the channel looking like the last successful check.
  @Test
  void adHocFailureIsReportedRatherThanOnlyLogged() {
    doThrow(new RuntimeException("boom")).when(service).runChecksForFunds(anyList());

    job.onTrackingDifferenceCheckRequested(new RunTrackingDifferenceCheckRequested());

    then(notifier).should().notifyRunFailed("TD check", "boom");
    then(notifier).should(never()).notify(anyList());
  }

  // A run that covered only some funds is not a run that covered them all. Posting the partial
  // result on its own reads as the whole picture, with the skipped funds simply absent.
  @Test
  void adHocPartialRunNamesTheFundsItCouldNotCheck() {
    doThrow(
            new TrackingDifferenceService.IncompletePriceDataException(
                "Incomplete security price data:\nTUK75: IE00MISSING1", List.of()))
        .when(service)
        .runChecksForFunds(anyList());

    job.onTrackingDifferenceCheckRequested(new RunTrackingDifferenceCheckRequested());

    then(notifier)
        .should()
        .notifyRunIncomplete("TD check", "Incomplete security price data:\nTUK75: IE00MISSING1");
  }

  @Test
  void adHocNotifiesPartialResultsOnIncompletePriceData() {
    var partialResults = List.<TrackingDifferenceResult>of();
    doThrow(
            new TrackingDifferenceService.IncompletePriceDataException(
                "missing prices", partialResults))
        .when(service)
        .runChecksForFunds(anyList());

    job.onTrackingDifferenceCheckRequested(new RunTrackingDifferenceCheckRequested());

    then(notifier).should().notify(partialResults);
  }

  // The NAV publication writes the check events on an ordinary day, so most evenings there is no
  // gap at all. The per-day notifier reads an empty list as "nothing actionable was checked" and
  // posts it to INVESTMENT, which would put a warning on the channel every weekday for the case
  // that means everything worked.
  @Test
  void anEveningWithNoGapToFillSaysNothing() {
    given(service.fillGaps(GAP_LOOKBACK_DAYS))
        .willReturn(new GapFillRun(List.of(), List.of(), Map.of()));

    job.fillTrackingDifferenceGaps();

    then(service).should().fillGaps(GAP_LOOKBACK_DAYS);
    then(notifier).should(never()).notify(anyList());
    then(notifier).should(never()).notifyGapFillSummary(any());
  }

  // One missed day is the case the daily run exists for, and its breach is current enough to read
  // as the daily alert it would have been.
  @Test
  void aSingleMissedDayIsReportedAsTheDayItself() {
    var results = List.of(result(NAV_DATE));
    given(service.fillGaps(GAP_LOOKBACK_DAYS))
        .willReturn(new GapFillRun(results, List.of(), Map.of()));

    job.fillTrackingDifferenceGaps();

    then(notifier).should().notify(results);
    then(notifier).should(never()).notifyGapFillSummary(any());
  }

  // The first run after a deploy or an outage can carry weeks of dates. Sent through the per-day
  // formatter, every historical breach arrives today as a fresh "TD BREACH DETECTED".
  @Test
  void aFillCoveringSeveralDaysIsSummarisedRatherThanReplayedDayByDay() {
    var run =
        new GapFillRun(
            List.of(result(NAV_DATE.minusDays(1)), result(NAV_DATE)), List.of(), Map.of());
    given(service.fillGaps(GAP_LOOKBACK_DAYS)).willReturn(run);

    job.fillTrackingDifferenceGaps();

    then(notifier).should().notifyGapFillSummary(run);
    then(notifier).should(never()).notify(anyList());
  }

  @Test
  void aSingleDayRecheckedBecauseItsNavWasCorrectedIsSummarisedSoTheMessageSaysWhyItRan() {
    var run =
        new GapFillRun(List.of(result(NAV_DATE)), List.of(), Map.of(TUK75, List.of(NAV_DATE)));
    given(service.fillGaps(GAP_LOOKBACK_DAYS)).willReturn(run);

    job.fillTrackingDifferenceGaps();

    then(notifier).should().notifyGapFillSummary(run);
    then(notifier).should(never()).notify(anyList());
  }

  @Test
  void aCorrectedNavDateThatCouldNotBeRecheckedIsReportedOnlyAsTheDateThatCouldNotRun() {
    var gap =
        new GapFailure(
            NAV_DATE, "fund=TUK75, missingIsins=[IE00MISSING1]", 0, NAV_DATE.plusDays(30));
    given(service.fillGaps(GAP_LOOKBACK_DAYS))
        .willReturn(new GapFillRun(List.of(), List.of(gap), Map.of(TUK75, List.of(NAV_DATE))));

    job.fillTrackingDifferenceGaps();

    then(notifier)
        .should()
        .notifyRunIncomplete(
            "TD daily gap fill",
            "Incomplete security price data:\n"
                + "checkDate=2026-09-11, fund=TUK75, missingIsins=[IE00MISSING1]");
    then(notifier).should(never()).notifyGapFillSummary(any());
    then(notifier).should(never()).notify(anyList());
  }

  @Test
  void aFailedDailyRunIsReportedRatherThanOnlyLogged() {
    doThrow(new RuntimeException("boom")).when(service).fillGaps(GAP_LOOKBACK_DAYS);

    job.fillTrackingDifferenceGaps();

    then(notifier).should().notifyRunFailed("TD daily gap fill", "boom");
    then(notifier).should(never()).notify(anyList());
  }

  @Test
  void anIncompleteDailyRunNamesEveryDateItCouldNotCheckAndMarksTheStandingOnes() {
    var freshGap =
        new GapFailure(
            NAV_DATE, "fund=TUK75, missingIsins=[IE00MISSING1]", 1, NAV_DATE.plusDays(30));
    var standingGap =
        new GapFailure(
            NAV_DATE.minusDays(10),
            "fund=TUK00, missingIsins=[IE00MISSING2]",
            11,
            NAV_DATE.plusDays(20));
    given(service.fillGaps(GAP_LOOKBACK_DAYS))
        .willReturn(new GapFillRun(List.of(), List.of(freshGap, standingGap), Map.of()));

    job.fillTrackingDifferenceGaps();

    then(notifier)
        .should()
        .notifyRunIncomplete(
            "TD daily gap fill",
            """
            Incomplete security price data:
            checkDate=2026-09-11, fund=TUK75, missingIsins=[IE00MISSING1]
            checkDate=2026-09-01, fund=TUK00, missingIsins=[IE00MISSING2] [standing gap: \
            unfilled for 11 days, last attempt 2026-10-01 — the missing price has to be inserted \
            by hand, nothing backfills it]""");
    then(notifier).should(never()).notify(anyList());
  }

  @Test
  void anIncompleteDailyRunStillReportsTheDatesItDidFill() {
    var filled = List.of(result(NAV_DATE));
    var gap =
        new GapFailure(
            NAV_DATE.minusDays(1), "fund=TUK00, missingIsins=[IE00MISSING1]", 2, NAV_DATE);
    given(service.fillGaps(GAP_LOOKBACK_DAYS))
        .willReturn(new GapFillRun(filled, List.of(gap), Map.of()));

    job.fillTrackingDifferenceGaps();

    then(notifier)
        .should()
        .notifyRunIncomplete(
            "TD daily gap fill",
            "Incomplete security price data:\n"
                + "checkDate=2026-09-10, fund=TUK00, missingIsins=[IE00MISSING1]");
    then(notifier).should().notify(filled);
  }

  @Test
  void backfillEventDelegatesToServiceAndSummarises() {
    var results = List.<TrackingDifferenceResult>of();
    given(service.backfillChecks(7)).willReturn(results);

    job.onTrackingDifferenceBackfillRequested(new RunTrackingDifferenceBackfillRequested(7));

    then(service).should().backfillChecks(7);
    then(notifier).should().notifyBackfillSummary(7, results);
  }

  @Test
  void theSeptemberNavCorrectionBackfillRerunsTheThirtyDaysReachingBackBeforeTheCorrection() {
    var results = List.<TrackingDifferenceResult>of();
    given(service.backfillChecks(30)).willReturn(results);

    job.backfillAfterTheSeptemberNavCorrection();

    then(service).should().backfillChecks(30);
    then(notifier).should().notifyBackfillSummary(30, results);
  }

  @Test
  void backfillReachesAsFarBackAsTheEventAsksFor() {
    var results = List.<TrackingDifferenceResult>of();
    given(service.backfillChecks(40)).willReturn(results);

    job.onTrackingDifferenceBackfillRequested(new RunTrackingDifferenceBackfillRequested(40));

    then(service).should().backfillChecks(40);
    then(service).should(never()).backfillChecks(7);
  }

  @Test
  void backfillSummarisesRatherThanPostingEveryDay() {
    var results = List.<TrackingDifferenceResult>of();
    given(service.backfillChecks(40)).willReturn(results);

    job.onTrackingDifferenceBackfillRequested(new RunTrackingDifferenceBackfillRequested(40));

    then(notifier).should().notifyBackfillSummary(40, results);
    then(notifier).should(never()).notify(anyList());
  }

  @Test
  void backfillFailureIsReportedRatherThanOnlyLogged() {
    doThrow(new RuntimeException("boom")).when(service).backfillChecks(7);

    job.onTrackingDifferenceBackfillRequested(new RunTrackingDifferenceBackfillRequested(7));

    then(notifier).should().notifyRunFailed("TD backfill", "boom");
    then(notifier).should(never()).notify(anyList());
  }

  @Test
  void aBackfillThatCouldNotPriceEveryDateIsSummarisedAsIncompleteNeverAsComplete() {
    var partialResults = List.<TrackingDifferenceResult>of();
    doThrow(
            new TrackingDifferenceService.IncompletePriceDataException(
                "missing prices", partialResults))
        .when(service)
        .backfillChecks(7);

    job.onTrackingDifferenceBackfillRequested(new RunTrackingDifferenceBackfillRequested(7));

    then(notifier).should().notifyRunIncomplete("TD backfill", "missing prices");
    then(notifier).should().notifyIncompleteBackfillSummary(7, partialResults);
    then(notifier).should(never()).notifyBackfillSummary(anyInt(), anyList());
  }

  @Test
  void aBackfillFailureCarryingNoMessageIsNamedByItsTypeInsteadOfNull() {
    doThrow(new NullPointerException()).when(service).backfillChecks(7);

    job.onTrackingDifferenceBackfillRequested(new RunTrackingDifferenceBackfillRequested(7));

    then(notifier).should().notifyRunFailed("TD backfill", "NullPointerException");
  }
}
