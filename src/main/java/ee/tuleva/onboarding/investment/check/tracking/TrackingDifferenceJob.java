package ee.tuleva.onboarding.investment.check.tracking;

import ee.tuleva.onboarding.investment.event.FeeAccrualPositionsSynced;
import ee.tuleva.onboarding.investment.event.RunTrackingDifferenceCheckRequested;
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

  @EventListener
  void onFeeAccrualPositionsSynced(FeeAccrualPositionsSynced event) {
    runTrackingDifferenceChecks();
  }

  @EventListener
  void onTrackingDifferenceCheckRequested(RunTrackingDifferenceCheckRequested event) {
    runTrackingDifferenceChecks();
  }

  private void runTrackingDifferenceChecks() {
    log.info("Starting tracking difference check");

    try {
      var results = trackingDifferenceService.runChecks();
      trackingDifferenceNotifier.notify(results);

      log.info("Tracking difference check completed: resultCount={}", results.size());
    } catch (Exception e) {
      log.error("Tracking difference check failed", e);
    }
  }
}
