package ee.tuleva.onboarding.investment.risk;

import java.time.LocalDate;
import java.util.List;

record CalculatedSeries(List<ReferencePoint> points, List<LocalDate> skippedDates) {

  static CalculatedSeries empty() {
    return new CalculatedSeries(List.of(), List.of());
  }
}
