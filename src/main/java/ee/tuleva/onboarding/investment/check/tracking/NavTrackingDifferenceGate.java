package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.check.tracking.TrackingCheckType.MODEL_PORTFOLIO;
import static ee.tuleva.onboarding.investment.event.PipelineStep.TRACKING_DIFFERENCE;
import static java.util.Objects.requireNonNull;
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
        trackingDifferenceNotifier.notifyCheckCouldNotRun(fund, navDate);
      } else {
        trackingDifferenceNotifier.notify(results);
      }
      pipelineTracker.stepCompleted(TRACKING_DIFFERENCE);

      var breaches =
          results.stream()
              .filter(r -> r.checkType() == MODEL_PORTFOLIO)
              .filter(TrackingDifferenceResult::navResidualBreach)
              .toList();
      if (!breaches.isEmpty()) {
        var details =
            breaches.stream()
                .map(
                    r -> {
                      var navResidual = requireNonNull(r.navResidual());
                      var implied = requireNonNull(r.bodImpliedFundReturn());
                      return "%s navResidual=%s (fund=%s, implied=%s, TD=%s)"
                          .formatted(
                              r.checkType(),
                              navResidual.toPlainString(),
                              r.fundReturn().toPlainString(),
                              implied.toPlainString(),
                              r.trackingDifference().toPlainString());
                    })
                .collect(joining("; "));
        return Optional.of(
            "TD breach: fund=%s, date=%s, %s".formatted(fund.getCode(), navDate, details));
      }
      return Optional.empty();
    } catch (TrackingDifferenceService.IncompletePriceDataException e) {
      if (e.completedResults().isEmpty()) {
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
