package ee.tuleva.onboarding.investment.check.tracking;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;

import ee.tuleva.onboarding.investment.event.RunTrackingDifferenceBackfillRequested;
import ee.tuleva.onboarding.investment.event.RunTrackingDifferenceCheckRequested;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TrackingDifferenceJobTest {

  @Mock TrackingDifferenceService service;
  @Mock TrackingDifferenceNotifier notifier;

  @InjectMocks TrackingDifferenceJob job;

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

  @Test
  void backfillEventDelegatesToServiceAndSummarises() {
    var results = List.<TrackingDifferenceResult>of();
    given(service.backfillChecks(7)).willReturn(results);

    job.onTrackingDifferenceBackfillRequested(new RunTrackingDifferenceBackfillRequested(7));

    then(service).should().backfillChecks(7);
    then(notifier).should().notifyBackfillSummary(7, results);
  }

  // A backfill deep enough to reach a corrected daily check is the whole point of the parameter:
  // fixing the check leaves every already-written event behind it, and only a run that reaches
  // back that far rewrites them.
  @Test
  void backfillReachesAsFarBackAsTheEventAsksFor() {
    var results = List.<TrackingDifferenceResult>of();
    given(service.backfillChecks(40)).willReturn(results);

    job.onTrackingDifferenceBackfillRequested(new RunTrackingDifferenceBackfillRequested(40));

    then(service).should().backfillChecks(40);
    then(service).should(never()).backfillChecks(7);
  }

  // One line per fund and check type, not one per fund-day: a 40-day backfill produces hundreds
  // of results, and posting them individually buries the channel it is meant to inform.
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
  void backfillNotifiesPartialResultsOnIncompletePriceData() {
    var partialResults = List.<TrackingDifferenceResult>of();
    doThrow(
            new TrackingDifferenceService.IncompletePriceDataException(
                "missing prices", partialResults))
        .when(service)
        .backfillChecks(7);

    job.onTrackingDifferenceBackfillRequested(new RunTrackingDifferenceBackfillRequested(7));

    then(notifier).should().notifyBackfillSummary(7, partialResults);
  }

  @Test
  void aBackfillFailureCarryingNoMessageIsNamedByItsTypeInsteadOfNull() {
    doThrow(new NullPointerException()).when(service).backfillChecks(7);

    job.onTrackingDifferenceBackfillRequested(new RunTrackingDifferenceBackfillRequested(7));

    then(notifier).should().notifyRunFailed("TD backfill", "NullPointerException");
  }
}
