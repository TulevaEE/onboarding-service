package ee.tuleva.onboarding.investment.risk;

import static ee.tuleva.onboarding.investment.risk.RiskIndicatorStatus.CHANGE_CONFIRMED;
import static ee.tuleva.onboarding.investment.risk.RiskIndicatorStatus.CHANGE_PENDING;
import static ee.tuleva.onboarding.investment.risk.RiskIndicatorStatus.STABLE;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

  /**
   * Runs are collapsed over the whole published series, not over the analysis lookback, so that
   * {@code publishedSince} reports when the class was really first published rather than the edge
   * of the lookback window. The lookback only decides whether the preceding class is recent enough
   * to still be worth reporting.
   */
  List<Run> runs() {
    var runs = new ArrayList<Run>();
    for (var point : points) {
      if (!runs.isEmpty() && runs.getLast().riskClass() == point.riskClass()) {
        runs.set(runs.size() - 1, runs.getLast().extendTo(point.date()));
      } else {
        runs.add(new Run(point.riskClass(), point.date(), point.date(), 1));
      }
    }
    return runs;
  }

  PublishedRiskIndicator analyse(
      TulevaFund fund, RiskIndicatorType indicatorType, List<ReferencePoint> rawPoints) {
    var classified = rawPoints.stream().filter(point -> point.riskClass() != null).toList();
    var latest = classified.getLast();
    var evaluationDate = latest.date();
    var rawLatestClass = latest.riskClass();

    var runs = runs();
    var currentRun = runs.getLast();
    var changedWithinLookback =
        currentRun.start().isAfter(evaluationDate.minusMonths(ANALYSIS_LOOKBACK_MONTHS));
    var previousPublishedClass =
        runs.size() > 1 && changedWithinLookback ? runs.get(runs.size() - 2).riskClass() : null;

    var telemetryWindow =
        classified.stream()
            .filter(
                point -> point.date().isAfter(evaluationDate.minusMonths(TELEMETRY_WINDOW_MONTHS)))
            .toList();
    var matching =
        telemetryWindow.stream()
            .filter(point -> Objects.equals(point.riskClass(), rawLatestClass))
            .count();

    var rawRun = trailingRawRun(classified, rawLatestClass);

    return new PublishedRiskIndicator(
        fund,
        indicatorType,
        evaluationDate,
        currentRun.riskClass(),
        rawLatestClass,
        previousPublishedClass,
        currentRun.start(),
        rawRun.start(),
        currentRun.referencePoints(),
        rawRun.referencePoints(),
        telemetryWindow.size(),
        (int) matching,
        latest.observationCount(),
        latest.volatility(),
        status(currentRun, previousPublishedClass, rawLatestClass, evaluationDate));
  }

  /**
   * How long the raw (unpublished) class has held at the end of the series. Drives the "CESR
   * four-month threshold is N weeks away" line in the digest.
   */
  private Run trailingRawRun(List<ReferencePoint> classified, @Nullable Integer rawLatestClass) {
    var start = classified.getLast().date();
    var referencePoints = 0;
    for (int i = classified.size() - 1; i >= 0; i--) {
      if (!Objects.equals(classified.get(i).riskClass(), rawLatestClass)) {
        break;
      }
      start = classified.get(i).date();
      referencePoints++;
    }
    return new Run(
        rawLatestClass == null ? 0 : rawLatestClass,
        start,
        classified.getLast().date(),
        referencePoints);
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
