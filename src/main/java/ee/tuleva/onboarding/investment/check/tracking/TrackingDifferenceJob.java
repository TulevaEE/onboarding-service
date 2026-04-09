package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.event.PipelineStep.TRACKING_DIFFERENCE;

import ee.tuleva.onboarding.investment.event.PipelineTracker;
import ee.tuleva.onboarding.investment.event.RunTrackingDifferenceCheckRequested;
import ee.tuleva.onboarding.savings.fund.nav.AllNavCalculationsCompleted;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile({"production", "staging"})
public class TrackingDifferenceJob {

  private final TrackingDifferenceService trackingDifferenceService;
  private final TrackingDifferenceNotifier trackingDifferenceNotifier;
  private final PipelineTracker pipelineTracker;

  @EventListener
  void onAllNavCalculationsCompleted(AllNavCalculationsCompleted event) {
    runTrackingDifferenceChecks();
  }

  @EventListener
  void onTrackingDifferenceCheckRequested(RunTrackingDifferenceCheckRequested event) {
    runTrackingDifferenceChecks();
  }

  private void runTrackingDifferenceChecks() {
    pipelineTracker.stepStarted(TRACKING_DIFFERENCE);
    log.info("Starting tracking difference check");

    try {
      var results = trackingDifferenceService.runChecks();
      trackingDifferenceNotifier.notify(results);
      pipelineTracker.stepCompleted(TRACKING_DIFFERENCE);

      log.info("Tracking difference check completed: resultCount={}", results.size());
    } catch (TrackingDifferenceService.IncompletePriceDataException e) {
      trackingDifferenceNotifier.notify(e.completedResults());
      pipelineTracker.stepFailed(TRACKING_DIFFERENCE, e.getMessage());
      log.error("Tracking difference check incomplete", e);
    } catch (Exception e) {
      pipelineTracker.stepFailed(TRACKING_DIFFERENCE, e.getMessage());
      log.error("Tracking difference check failed", e);
    }
  }
}
