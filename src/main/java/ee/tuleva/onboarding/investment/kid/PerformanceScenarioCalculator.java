package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.investment.kid.WindowSource.FULL_LENGTH;
import static ee.tuleva.onboarding.investment.kid.WindowSource.SHORTER;
import static java.util.Comparator.comparingDouble;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
class PerformanceScenarioCalculator {

  private static final int MONTHS_PER_YEAR = 12;
  private static final int ANNEX_IV_P7_SHORTEST_WINDOW_MONTHS = 12;

  PerformanceScenarios scenarios(
      List<MonthEndPrice> prices, int horizonYears, double ongoingCharges) {
    var months = horizonYears * MONTHS_PER_YEAR;
    return pick(
        fullLengthWindows(prices, months),
        shorterWindowsEndingWithThePeriod(prices, months),
        ongoingCharges);
  }

  static PerformanceScenarios pick(
      List<ScenarioWindow> fullLength, List<ScenarioWindow> shorter, double ongoingCharges) {
    Comparator<ScenarioWindow> worstFirst =
        comparingDouble((ScenarioWindow window) -> window.netFactor(ongoingCharges))
            .thenComparing(ScenarioWindow::end)
            .thenComparing(ScenarioWindow::start);
    var median =
        ContinuousPercentile.of(
            fullLength.stream().map(window -> window.netFactor(ongoingCharges)).toList(), 0.5);
    Comparator<ScenarioWindow> closestToMedianFirst =
        comparingDouble(
                (ScenarioWindow window) -> Math.abs(window.netFactor(ongoingCharges) - median))
            .thenComparing(ScenarioWindow::end);
    Comparator<ScenarioWindow> bestFirst =
        comparingDouble((ScenarioWindow window) -> -window.netFactor(ongoingCharges))
            .thenComparing(ScenarioWindow::end);
    return new PerformanceScenarios(
        Stream.concat(fullLength.stream(), shorter.stream()).min(worstFirst).orElseThrow(),
        fullLength.stream().min(closestToMedianFirst).orElseThrow(),
        fullLength.stream().min(bestFirst).orElseThrow());
  }

  private static List<ScenarioWindow> fullLengthWindows(List<MonthEndPrice> prices, int months) {
    if (prices.size() <= months) {
      throw new KidCalculationRefusedException(
          "Too few month-end prices for one full window: prices=%d, windowMonths=%d"
              .formatted(prices.size(), months));
    }
    return IntStream.range(months, prices.size())
        .mapToObj(end -> window(FULL_LENGTH, prices.get(end - months), prices.get(end), months))
        .toList();
  }

  private static List<ScenarioWindow> shorterWindowsEndingWithThePeriod(
      List<MonthEndPrice> prices, int months) {
    var last = prices.size() - 1;
    return IntStream.range(ANNEX_IV_P7_SHORTEST_WINDOW_MONTHS, months)
        .mapToObj(length -> window(SHORTER, prices.get(last - length), prices.get(last), length))
        .toList();
  }

  private static ScenarioWindow window(
      WindowSource source, MonthEndPrice start, MonthEndPrice end, int months) {
    return new ScenarioWindow(
        source, start.monthEnd(), end.monthEnd(), months, end.price() / start.price());
  }
}
