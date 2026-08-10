package ee.tuleva.onboarding.investment.risk;

import static ee.tuleva.onboarding.investment.risk.RiskIndicatorStatus.CHANGE_CONFIRMED;
import static ee.tuleva.onboarding.investment.risk.RiskIndicatorStatus.CHANGE_PENDING;
import static ee.tuleva.onboarding.investment.risk.RiskIndicatorStatus.STABLE;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

record PublishedSeries(List<PublishedPoint> points) {

  private static final int ANALYSIS_LOOKBACK_MONTHS = 24;
  private static final int RECENT_CHANGE_MONTHS = 4;
  private static final int TELEMETRY_WINDOW_MONTHS = 4;

  static PublishedSeries empty() {
    return new PublishedSeries(List.of());
  }

  boolean isEmpty() {
    return points.isEmpty();
  }

  List<Run> runs() {
    var runs = new ArrayList<Run>();
    for (var point : withinLookback()) {
      if (!runs.isEmpty() && runs.getLast().riskClass() == point.riskClass()) {
        runs.set(runs.size() - 1, runs.getLast().extendTo(point.date()));
      } else {
        runs.add(new Run(point.riskClass(), point.date(), point.date(), 1));
      }
    }
    return runs;
  }

  private List<PublishedPoint> withinLookback() {
    var cutoff = points.getLast().date().minusMonths(ANALYSIS_LOOKBACK_MONTHS);
    return points.stream().filter(point -> point.date().isAfter(cutoff)).toList();
  }

  PublishedRiskIndicator analyse(
      TulevaFund fund, RiskIndicatorType indicatorType, List<ReferencePoint> rawPoints) {
    var classified = rawPoints.stream().filter(point -> point.riskClass() != null).toList();
    var evaluationDate = classified.getLast().date();
    var rawLatestClass = classified.getLast().riskClass();

    var runs = runs();
    var currentRun = runs.getLast();
    var previousPublishedClass = runs.size() > 1 ? runs.get(runs.size() - 2).riskClass() : null;

    var telemetryWindow =
        classified.stream()
            .filter(
                point -> point.date().isAfter(evaluationDate.minusMonths(TELEMETRY_WINDOW_MONTHS)))
            .toList();
    var matching =
        telemetryWindow.stream()
            .filter(point -> java.util.Objects.equals(point.riskClass(), rawLatestClass))
            .count();

    return new PublishedRiskIndicator(
        fund,
        indicatorType,
        currentRun.riskClass(),
        rawLatestClass,
        previousPublishedClass,
        currentRun.start(),
        currentRun.referencePoints(),
        telemetryWindow.size(),
        (int) matching,
        classified.getLast().volatility(),
        status(currentRun, previousPublishedClass, rawLatestClass, evaluationDate));
  }

  private RiskIndicatorStatus status(
      Run currentRun,
      @Nullable Integer previousPublishedClass,
      @Nullable Integer rawLatestClass,
      LocalDate evaluationDate) {
    if (rawLatestClass != null && rawLatestClass != currentRun.riskClass()) {
      return CHANGE_PENDING;
    }
    if (previousPublishedClass != null
        && currentRun.start().isAfter(evaluationDate.minusMonths(RECENT_CHANGE_MONTHS))) {
      return CHANGE_CONFIRMED;
    }
    return STABLE;
  }

  record PublishedPoint(LocalDate date, int riskClass) {}

  record Run(int riskClass, LocalDate start, LocalDate end, int referencePoints) {
    Run extendTo(LocalDate date) {
      return new Run(riskClass, start, date, referencePoints + 1);
    }
  }
}
