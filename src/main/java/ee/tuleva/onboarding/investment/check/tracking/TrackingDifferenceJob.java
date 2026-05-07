package ee.tuleva.onboarding.investment.check.tracking;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.event.RunTrackingDifferenceBackfillRequested;
import ee.tuleva.onboarding.investment.event.RunTrackingDifferenceCheckRequested;
import java.util.List;
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
  void onTrackingDifferenceCheckRequested(RunTrackingDifferenceCheckRequested event) {
    log.info("Starting ad-hoc tracking difference check");

    try {
      var results = trackingDifferenceService.runChecksForFunds(List.of(TulevaFund.values()));
      trackingDifferenceNotifier.notify(results);
      log.info("Tracking difference check completed: resultCount={}", results.size());
    } catch (TrackingDifferenceService.IncompletePriceDataException e) {
      trackingDifferenceNotifier.notify(e.completedResults());
      log.error("Tracking difference check incomplete", e);
    } catch (Exception e) {
      log.error("Tracking difference check failed", e);
    }
  }

  @EventListener
  void onTrackingDifferenceBackfillRequested(RunTrackingDifferenceBackfillRequested event) {
    log.info("Starting tracking difference backfill");

    try {
      var results = trackingDifferenceService.backfillChecks(7);
      trackingDifferenceNotifier.notify(results);
      log.info("Tracking difference backfill completed: resultCount={}", results.size());
    } catch (TrackingDifferenceService.IncompletePriceDataException e) {
      trackingDifferenceNotifier.notify(e.completedResults());
      log.error("Tracking difference backfill incomplete", e);
    } catch (Exception e) {
      log.error("Tracking difference backfill failed", e);
    }
  }
}
