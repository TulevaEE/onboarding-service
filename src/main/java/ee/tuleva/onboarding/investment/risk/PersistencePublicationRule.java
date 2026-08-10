package ee.tuleva.onboarding.investment.risk;

import static java.time.DayOfWeek.MONDAY;
import static java.time.temporal.TemporalAdjusters.previousOrSame;

import ee.tuleva.onboarding.investment.risk.PublishedSeries.PublishedPoint;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
class PersistencePublicationRule implements PublicationRule {

  private static final int PERSISTENCE_WINDOW_MONTHS = 4;
  private static final int WINDOW_START_TOLERANCE_DAYS = 7;

  @Override
  public PublishedSeries publish(List<ReferencePoint> points) {
    var classified = points.stream().filter(point -> point.riskClass() != null).toList();
    if (classified.isEmpty()) {
      return PublishedSeries.empty();
    }

    var published = new ArrayList<PublishedPoint>(classified.size());
    var carried = classified.getFirst().riskClass();
    for (var point : classified) {
      var window = window(classified, point.date());
      if (isWindowComplete(window, point.date()) && isFullyOutside(window, carried)) {
        carried = prevailingClass(window);
      }
      published.add(new PublishedPoint(point.date(), carried));
    }
    return new PublishedSeries(published);
  }

  private boolean isFullyOutside(List<ReferencePoint> window, @Nullable Integer publishedClass) {
    return window.stream().noneMatch(point -> Objects.equals(point.riskClass(), publishedClass));
  }

  private List<ReferencePoint> window(List<ReferencePoint> classified, LocalDate evalDate) {
    var windowStart = evalDate.minusMonths(PERSISTENCE_WINDOW_MONTHS);
    return classified.stream()
        .filter(point -> point.date().isAfter(windowStart) && !point.date().isAfter(evalDate))
        .toList();
  }

  private boolean isWindowComplete(List<ReferencePoint> window, LocalDate evalDate) {
    if (window.isEmpty()) {
      return false;
    }
    var windowStart = evalDate.minusMonths(PERSISTENCE_WINDOW_MONTHS);
    if (window.getFirst().date().isAfter(windowStart.plusDays(WINDOW_START_TOLERANCE_DAYS))) {
      return false;
    }
    return hasNoMissingWeek(window);
  }

  /**
   * A reference point is dated on the week's last NAV day, which a public holiday moves off Friday.
   * Consecutiveness therefore has to be read on the ISO week, not on the raw dates — otherwise
   * every holiday would look like a missing week and freeze the migration assessment for four
   * months.
   */
  private boolean hasNoMissingWeek(List<ReferencePoint> window) {
    for (int i = 1; i < window.size(); i++) {
      var previousWeek = window.get(i - 1).date().with(previousOrSame(MONDAY));
      var currentWeek = window.get(i).date().with(previousOrSame(MONDAY));
      if (!previousWeek.plusWeeks(1).equals(currentWeek)) {
        return false;
      }
    }
    return true;
  }

  private @Nullable Integer prevailingClass(List<ReferencePoint> window) {
    Map<Integer, Integer> counts = new LinkedHashMap<>();
    window.forEach(point -> counts.merge(point.riskClass(), 1, Integer::sum));
    return counts.entrySet().stream()
        .max(
            Comparator.comparingInt(Map.Entry<Integer, Integer>::getValue)
                .thenComparingInt(Map.Entry::getKey))
        .map(Map.Entry::getKey)
        .orElseThrow();
  }
}
