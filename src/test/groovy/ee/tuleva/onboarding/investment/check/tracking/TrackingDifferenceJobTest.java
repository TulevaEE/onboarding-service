package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;

import ee.tuleva.onboarding.investment.event.PipelineTracker;
import ee.tuleva.onboarding.investment.event.RunTrackingDifferenceBackfillRequested;
import ee.tuleva.onboarding.investment.event.RunTrackingDifferenceCheckRequested;
import ee.tuleva.onboarding.savings.fund.nav.NavCalculationCompleted;
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
  @Mock PipelineTracker pipelineTracker;

  @InjectMocks TrackingDifferenceJob job;

  @Test
  void navCompletedDelegatesToServiceAndNotifierForSpecificFunds() {
    var funds = List.of(TUK75, TUK00);
    var results = List.<TrackingDifferenceResult>of();
    given(service.runChecksForFunds(funds)).willReturn(results);

    job.onNavCalculationCompleted(new NavCalculationCompleted(funds));

    then(service).should().runChecksForFunds(funds);
    then(notifier).should().notify(results);
  }

  @Test
  void adHocEventDelegatesToServiceAndNotifier() {
    var results = List.<TrackingDifferenceResult>of();
    given(service.runChecksForFunds(anyList())).willReturn(results);

    job.onTrackingDifferenceCheckRequested(new RunTrackingDifferenceCheckRequested());

    then(service).should().runChecksForFunds(anyList());
    then(notifier).should().notify(results);
  }

  @Test
  void swallowsExceptions() {
    var funds = List.of(TUK75, TUK00);
    doThrow(new RuntimeException("boom")).when(service).runChecksForFunds(funds);

    job.onNavCalculationCompleted(new NavCalculationCompleted(funds));

    then(notifier).shouldHaveNoInteractions();
  }

  @Test
  void backfillEventDelegatesToServiceBackfillAndNotifier() {
    var results = List.<TrackingDifferenceResult>of();
    given(service.backfillChecks(7)).willReturn(results);

    job.onTrackingDifferenceBackfillRequested(new RunTrackingDifferenceBackfillRequested());

    then(service).should().backfillChecks(7);
    then(notifier).should().notify(results);
  }

  @Test
  void backfillSwallowsExceptions() {
    doThrow(new RuntimeException("boom")).when(service).backfillChecks(7);

    job.onTrackingDifferenceBackfillRequested(new RunTrackingDifferenceBackfillRequested());

    then(notifier).shouldHaveNoInteractions();
  }

  @Test
  void backfillNotifiesPartialResultsOnIncompletePriceData() {
    var partialResults = List.<TrackingDifferenceResult>of();
    doThrow(
            new TrackingDifferenceService.IncompletePriceDataException(
                "missing prices", partialResults))
        .when(service)
        .backfillChecks(7);

    job.onTrackingDifferenceBackfillRequested(new RunTrackingDifferenceBackfillRequested());

    then(notifier).should().notify(partialResults);
  }

  @Test
  void notifiesPartialResultsOnIncompletePriceData() {
    var funds = List.of(TUK75);
    var partialResults = List.<TrackingDifferenceResult>of();
    doThrow(
            new TrackingDifferenceService.IncompletePriceDataException(
                "missing prices", partialResults))
        .when(service)
        .runChecksForFunds(funds);

    job.onNavCalculationCompleted(new NavCalculationCompleted(funds));

    then(notifier).should().notify(partialResults);
  }
}
