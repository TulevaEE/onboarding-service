package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.TrackingCheckType.MODEL_PORTFOLIO;
import static ee.tuleva.onboarding.investment.check.tracking.NavReportHold.Residual.AT_THRESHOLD;
import static ee.tuleva.onboarding.investment.check.tracking.NavReportHold.Residual.BELOW_THRESHOLD;
import static ee.tuleva.onboarding.investment.check.tracking.NavReportHold.Run.COMPLETED;
import static ee.tuleva.onboarding.investment.check.tracking.NavReportHold.Run.FAILED;
import static ee.tuleva.onboarding.investment.check.tracking.NavReportHold.Run.INCOMPLETE_PRICES;
import static ee.tuleva.onboarding.investment.check.tracking.NavReportHold.holdsTheReport;
import static ee.tuleva.onboarding.pipeline.PipelineStep.TRACKING_DIFFERENCE;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.investment.check.tracking.NavReportHold.Check;
import ee.tuleva.onboarding.investment.check.tracking.NavReportHold.Run;
import ee.tuleva.onboarding.pipeline.PipelineTracker;
import ee.tuleva.onboarding.savings.fund.nav.NavPublicationGate;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NavTrackingDifferenceGate implements NavPublicationGate {

  private final TrackingDifferenceService trackingDifferenceService;
  private final TrackingDifferenceNotifier trackingDifferenceNotifier;
  private final PipelineTracker pipelineTracker;

  @Override
  public Optional<String> check(TulevaFund fund, LocalDate navDate) {
    pipelineTracker.stepStarted(TRACKING_DIFFERENCE);
    try {
      var check = trackingDifferenceService.checkFund(fund, navDate);
      var results = check.results();
      if (results.isEmpty()) {
        trackingDifferenceNotifier.notifyCheckCouldNotRun(fund, navDate);
      } else {
        trackingDifferenceNotifier.notify(results, check.checksNotMeasured());
      }
      pipelineTracker.stepCompleted(TRACKING_DIFFERENCE);

      return hold(fund, navDate, COMPLETED, modelPortfolioNavResidualBreaches(results));
    } catch (TrackingDifferenceService.IncompletePriceDataException e) {
      if (e.completedResults().isEmpty()) {
        trackingDifferenceNotifier.notifyCheckCouldNotRun(fund, navDate);
      } else {
        trackingDifferenceNotifier.notify(e.completedResults());
      }
      pipelineTracker.stepFailed(TRACKING_DIFFERENCE, e.getMessage());
      log.warn("TD gate incomplete price data, proceeding: fund={}, date={}", fund, navDate);
      return hold(
          fund,
          navDate,
          INCOMPLETE_PRICES,
          modelPortfolioNavResidualBreaches(e.completedResults()));
    } catch (Exception e) {
      trackingDifferenceNotifier.notifyCheckFailed(fund, navDate, FailureReason.of(e));
      pipelineTracker.stepFailed(TRACKING_DIFFERENCE, e.getMessage());
      log.warn("TD gate error, proceeding: fund={}, date={}", fund, navDate, e);
      return hold(fund, navDate, FAILED, List.of());
    }
  }

  private static List<TrackingDifferenceResult> modelPortfolioNavResidualBreaches(
      List<TrackingDifferenceResult> results) {
    return results.stream()
        .filter(r -> r.checkType() == MODEL_PORTFOLIO)
        .filter(TrackingDifferenceResult::navResidualBreach)
        .toList();
  }

  private static Optional<String> hold(
      TulevaFund fund, LocalDate navDate, Run run, List<TrackingDifferenceResult> breaches) {
    var check = new Check(run, breaches.isEmpty() ? BELOW_THRESHOLD : AT_THRESHOLD);
    if (!holdsTheReport(check)) {
      return Optional.empty();
    }
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
}
