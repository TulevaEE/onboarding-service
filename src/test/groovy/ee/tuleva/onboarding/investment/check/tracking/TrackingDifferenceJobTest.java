package ee.tuleva.onboarding.investment.check.tracking;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;

import ee.tuleva.onboarding.investment.event.PipelineTracker;
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
  void navCompletedDelegatesToServiceAndNotifier() {
    var results = List.<TrackingDifferenceResult>of();
    given(service.runChecks()).willReturn(results);

    job.onNavCalculationCompleted(new NavCalculationCompleted());

    then(service).should().runChecks();
    then(notifier).should().notify(results);
  }

  @Test
  void adHocEventDelegatesToServiceAndNotifier() {
    var results = List.<TrackingDifferenceResult>of();
    given(service.runChecks()).willReturn(results);

    job.onTrackingDifferenceCheckRequested(new RunTrackingDifferenceCheckRequested());

    then(service).should().runChecks();
    then(notifier).should().notify(results);
  }

  @Test
  void swallowsExceptions() {
    doThrow(new RuntimeException("boom")).when(service).runChecks();

    job.onNavCalculationCompleted(new NavCalculationCompleted());

    then(notifier).shouldHaveNoInteractions();
  }
}
