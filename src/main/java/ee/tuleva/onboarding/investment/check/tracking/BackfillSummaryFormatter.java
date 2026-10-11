package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.check.tracking.BreachAmounts.formatPercent;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

final class BackfillSummaryFormatter {

  private BackfillSummaryFormatter() {}

  static String format(String header, List<TrackingDifferenceResult> results) {
    var message = new StringBuilder(header);
    results.stream()
        .collect(
            Collectors.groupingBy(
                r -> "%s %s".formatted(r.fund().getCode(), r.checkType()),
                TreeMap::new,
                Collectors.toList()))
        .forEach(
            (fundAndCheckType, group) ->
                message.append(formatBackfillGroup(fundAndCheckType, group)));
    return message.toString();
  }

  private static String formatBackfillGroup(
      String fundAndCheckType, List<TrackingDifferenceResult> group) {
    var dates = group.stream().map(TrackingDifferenceResult::checkDate).sorted().toList();
    var breaches = group.stream().filter(TrackingDifferenceResult::hasAnyBreach).count();
    var worst =
        group.stream()
            .map(TrackingDifferenceResult::trackingDifference)
            .max(Comparator.comparing(BigDecimal::abs))
            .orElse(BigDecimal.ZERO);
    return "\n  %s: %d check dates %s to %s, %d breaches, largest TD %s%%"
        .formatted(
            fundAndCheckType,
            dates.size(),
            dates.getFirst(),
            dates.getLast(),
            breaches,
            formatPercent(worst));
  }
}
