package ee.tuleva.onboarding.investment.risk;

import ee.tuleva.onboarding.investment.risk.PublishedSeries.PublishedPoint;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
class MajorityPublicationRule implements PublicationRule {

  private static final int MAJORITY_WINDOW_MONTHS = 4;

  @Override
  public PublishedSeries publish(List<ReferencePoint> points) {
    var classified = points.stream().filter(point -> point.riskClass() != null).toList();
    if (classified.isEmpty()) {
      return PublishedSeries.empty();
    }

    var published = new ArrayList<PublishedPoint>(classified.size());
    Integer carried = null;
    for (var point : classified) {
      var majority = strictMajority(classified, point.date());
      if (majority != null) {
        carried = majority;
      }
      if (carried != null) {
        published.add(new PublishedPoint(point.date(), carried));
      }
    }
    return new PublishedSeries(published);
  }

  private @Nullable Integer strictMajority(List<ReferencePoint> classified, LocalDate evalDate) {
    var windowStart = evalDate.minusMonths(MAJORITY_WINDOW_MONTHS);
    Map<Integer, Integer> counts = new LinkedHashMap<>();
    var total = 0;
    for (var point : classified) {
      if (point.date().isAfter(windowStart) && !point.date().isAfter(evalDate)) {
        counts.merge(point.riskClass(), 1, Integer::sum);
        total++;
      }
    }
    var required = total;
    return counts.entrySet().stream()
        .filter(entry -> entry.getValue() * 2 > required)
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse(null);
  }
}
