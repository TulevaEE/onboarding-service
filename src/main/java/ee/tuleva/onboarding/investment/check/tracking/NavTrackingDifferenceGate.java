package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.check.tracking.TrackingCheckType.MODEL_PORTFOLIO;
import static ee.tuleva.onboarding.investment.event.PipelineStep.TRACKING_DIFFERENCE;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.event.PipelineTracker;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NavTrackingDifferenceGate {

  private final TrackingDifferenceService trackingDifferenceService;
  private final TrackingDifferenceNotifier trackingDifferenceNotifier;
  private final PipelineTracker pipelineTracker;

  public Optional<String> check(TulevaFund fund, LocalDate navDate) {
    pipelineTracker.stepStarted(TRACKING_DIFFERENCE);
    try {
      var results = trackingDifferenceService.checkFund(fund, navDate);
      if (results.isEmpty()) {
        // No NAV pair or no model data — the check could not run. Surface it (the report is going
        // out unvalidated) but do not block.
        trackingDifferenceNotifier.notifyCheckCouldNotRun(fund, navDate);
      } else {
        trackingDifferenceNotifier.notify(results);
      }
      pipelineTracker.stepCompleted(TRACKING_DIFFERENCE);

      // Block only on the NAV-correctness residual, not on the informational fund-vs-model TD.
      // On a MOC trade / model-switch day the fund-vs-model TD breaches but navResidual stays ~0
      // (the fund earned its begin-of-day portfolio's return), so the NAV report is not blocked.
      var breaches =
          results.stream()
              .filter(r -> r.checkType() == MODEL_PORTFOLIO)
              .filter(TrackingDifferenceResult::navResidualBreach)
              .toList();
      if (!breaches.isEmpty()) {
        var details =
            breaches.stream()
                .map(
                    r ->
                        "%s navResidual=%s (fund=%s, implied=%s, TD=%s)"
                            .formatted(
                                r.checkType(),
                                r.navResidual().toPlainString(),
                                r.fundReturn().toPlainString(),
                                r.impliedFundReturn().toPlainString(),
                                r.trackingDifference().toPlainString()))
                .collect(joining("; "));
        return Optional.of(
            "TD breach: fund=%s, date=%s, %s".formatted(fund.getCode(), navDate, details));
      }
      return Optional.empty();
    } catch (TrackingDifferenceService.IncompletePriceDataException e) {
      if (e.completedResults().isEmpty()) {
        // The check could not run (missing security prices) — surface it explicitly instead of the
        // misleading empty "within limits".
        trackingDifferenceNotifier.notifyCheckCouldNotRun(fund, navDate);
      } else {
        trackingDifferenceNotifier.notify(e.completedResults());
      }
      pipelineTracker.stepFailed(TRACKING_DIFFERENCE, e.getMessage());
      log.warn("TD gate incomplete price data, proceeding: fund={}, date={}", fund, navDate);
      return Optional.empty();
    } catch (Exception e) {
      pipelineTracker.stepFailed(TRACKING_DIFFERENCE, e.getMessage());
      log.warn("TD gate error, proceeding: fund={}, date={}", fund, navDate, e);
      return Optional.empty();
    }
  }
}
