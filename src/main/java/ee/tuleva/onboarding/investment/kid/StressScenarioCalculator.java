package ee.tuleva.onboarding.investment.kid;

import static java.util.Comparator.comparing;
import static java.util.Comparator.comparingDouble;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

@Component
class StressScenarioCalculator {

  private static final int ANNEX_II_P9_RISK_HISTORY_YEARS = 5;

  StressScenario stress(List<FundValue> dailyCloses, LocalDate periodEnd, int horizonYears) {
    var setting = StressSetting.forHorizon(horizonYears);
    var returns = logReturns(riskHistory(dailyCloses, periodEnd));
    var volatilities = rollingVolatilities(returns, setting.windowReturns());
    var stressedVolatility =
        ContinuousPercentile.of(
            volatilities.stream().map(WindowVolatility::volatility).toList(),
            setting.volatilityPercentile());
    var moments = Moments.of(returns);
    var representative =
        volatilities.stream()
            .min(
                comparingDouble(
                        (WindowVolatility window) ->
                            Math.abs(window.volatility() - stressedVolatility))
                    .thenComparing(WindowVolatility::end))
            .orElseThrow();
    var logReturn =
        stressedLogReturn(
            stressedVolatility,
            moments.skewness(),
            moments.excessKurtosis(),
            observationsInLast(dailyCloses, periodEnd, horizonYears),
            setting.z());
    if (!Double.isFinite(logReturn)) {
      throw new KidCalculationRefusedException(
          "Stress scenario not computable: horizonYears=%d, stressedVolatility=%s"
              .formatted(horizonYears, stressedVolatility));
    }
    return new StressScenario(logReturn, representative.start(), representative.end());
  }

  static double stressedLogReturn(
      double stressedVolatility,
      double skewness,
      double excessKurtosis,
      int observations,
      double z) {
    var n = (double) observations;
    var cornishFisherZ =
        z
            + (z * z - 1) / 6 * skewness / Math.sqrt(n)
            + (z * z * z - 3 * z) / 24 * excessKurtosis / n
            - (2 * z * z * z - 5 * z) / 36 * skewness * skewness / n;
    return stressedVolatility * Math.sqrt(n) * cornishFisherZ
        - 0.5 * stressedVolatility * stressedVolatility * n;
  }

  static int observationsInLast(List<FundValue> dailyCloses, LocalDate periodEnd, int years) {
    var horizonStart = periodEnd.minusYears(years);
    return (int)
        dailyCloses.stream()
            .filter(close -> close.date().isAfter(horizonStart) && !close.date().isAfter(periodEnd))
            .count();
  }

  private static List<FundValue> riskHistory(List<FundValue> dailyCloses, LocalDate periodEnd) {
    var historyStart = periodEnd.minusYears(ANNEX_II_P9_RISK_HISTORY_YEARS);
    return dailyCloses.stream()
        .filter(close -> !close.date().isAfter(historyStart))
        .max(comparing(FundValue::date))
        .map(
            firstReturnsBase ->
                dailyCloses.stream()
                    .filter(close -> !close.date().isBefore(firstReturnsBase.date()))
                    .toList())
        .orElse(dailyCloses);
  }

  private static List<DatedReturn> logReturns(List<FundValue> dailyCloses) {
    var sorted = dailyCloses.stream().sorted(comparing(FundValue::date)).toList();
    return IntStream.range(1, sorted.size())
        .mapToObj(
            i ->
                new DatedReturn(
                    sorted.get(i).date(),
                    Math.log(
                        sorted.get(i).value().doubleValue()
                            / sorted.get(i - 1).value().doubleValue())))
        .toList();
  }

  private static List<WindowVolatility> rollingVolatilities(
      List<DatedReturn> returns, int windowReturns) {
    if (returns.size() < windowReturns) {
      throw new KidCalculationRefusedException(
          "Too few daily returns for one stress window: returns=%d, windowReturns=%d"
              .formatted(returns.size(), windowReturns));
    }
    return IntStream.rangeClosed(windowReturns, returns.size())
        .mapToObj(end -> WindowVolatility.of(returns.subList(end - windowReturns, end)))
        .toList();
  }

  private record StressSetting(int windowReturns, double volatilityPercentile, double z) {

    private static final StressSetting ONE_YEAR_ANNEX_IV_P18_A =
        new StressSetting(21, 0.99, -2.3263478740408408);
    private static final StressSetting LONGER_ANNEX_IV_P18_B =
        new StressSetting(63, 0.95, -1.6448536269514729);

    static StressSetting forHorizon(int horizonYears) {
      return horizonYears == 1 ? ONE_YEAR_ANNEX_IV_P18_A : LONGER_ANNEX_IV_P18_B;
    }
  }

  private record DatedReturn(LocalDate date, double value) {}

  private record WindowVolatility(LocalDate start, LocalDate end, double volatility) {

    static WindowVolatility of(List<DatedReturn> window) {
      var mean = window.stream().mapToDouble(DatedReturn::value).average().orElseThrow();
      var variance =
          window.stream()
              .mapToDouble(r -> (r.value() - mean) * (r.value() - mean))
              .average()
              .orElseThrow();
      return new WindowVolatility(
          window.getFirst().date(), window.getLast().date(), Math.sqrt(variance));
    }
  }

  private record Moments(double skewness, double excessKurtosis) {

    static Moments of(List<DatedReturn> returns) {
      var mean = returns.stream().mapToDouble(DatedReturn::value).average().orElseThrow();
      var variance = centralMoment(returns, mean, 2);
      return new Moments(
          centralMoment(returns, mean, 3) / Math.pow(variance, 1.5),
          centralMoment(returns, mean, 4) / (variance * variance) - 3);
    }

    private static double centralMoment(List<DatedReturn> returns, double mean, int order) {
      return returns.stream()
          .mapToDouble(r -> Math.pow(r.value() - mean, order))
          .average()
          .orElseThrow();
    }
  }
}
