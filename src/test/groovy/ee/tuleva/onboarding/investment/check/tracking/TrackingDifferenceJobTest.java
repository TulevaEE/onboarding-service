package ee.tuleva.onboarding.investment.check.tracking;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;

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

  @Test
  void adHocSwallowsExceptions() {
    doThrow(new RuntimeException("boom")).when(service).runChecksForFunds(anyList());

    job.onTrackingDifferenceCheckRequested(new RunTrackingDifferenceCheckRequested());

    then(notifier).shouldHaveNoInteractions();
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
  void backfillEventDelegatesToServiceAndNotifier() {
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
}
