package ee.tuleva.onboarding.investment.risk;

import java.time.LocalDate;
import java.util.List;

/**
 * Dates the calculator could not turn into a reference point travel out beside the points it could.
 * A skipped date is not an absence of news: the publication rules count reference points inside a
 * window, so a hole moves the majority threshold every later run is measured against. Reaching only
 * the log would leave that shift with no trace anyone reads.
 */
record CalculatedSeries(List<ReferencePoint> points, List<LocalDate> skippedDates) {

  static CalculatedSeries empty() {
    return new CalculatedSeries(List.of(), List.of());
  }
}
