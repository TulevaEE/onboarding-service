package ee.tuleva.onboarding.investment.risk;

import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.time.DayOfWeek.MONDAY;
import static java.time.temporal.TemporalAdjusters.previousOrSame;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
class SrriCalculator {

  private static final MathContext MC = MathContext.DECIMAL64;
  private static final BigDecimal SQRT_52 = BigDecimal.valueOf(52).sqrt(MC);
  private static final int SAMPLE_PERIOD_YEARS = 5;

  /**
   * CESR Box 1 p3 asks for a sample covering the last five years of the life of the fund, which is
   * a question about how far the history reaches back, not about how many rows survived inside it.
   * Counting answers a different question: five calendar years hold 260 or 261 weekly returns
   * depending on where the dates fall, and every week without a NAV costs two more, so a threshold
   * set at the nominal 260 would blink on and off with the calendar. Coverage is the gate, with a
   * fortnight of slack for that same drift.
   */
  private static final int WINDOW_START_TOLERANCE_WEEKS = 2;

  /**
   * Not the CESR test — a floor beneath which a standard deviation says nothing however wide a
   * period it is spread across, because a series holding one early week and one recent month would
   * otherwise clear the coverage gate on a handful of returns. The digest reports any shortfall
   * against the nominal 260 separately.
   */
  private static final int MINIMUM_OBSERVATIONS = 200;

  private static final int MINIMUM_RETURNS = 2;
  private static final int VOLATILITY_SCALE = 12;

  List<ReferencePoint> calculate(List<FundValue> navValues, LocalDate from, LocalDate to) {
    var weeklyNavs = lastNavOfEachCompleteWeek(navValues);
    if (weeklyNavs.size() < MINIMUM_RETURNS) {
      return List.of();
    }
    var returns = adjacentWeekReturns(weeklyNavs);
    return weeklyNavs.stream()
        .map(WeeklyNav::weekEnd)
        .filter(date -> !date.isBefore(from) && !date.isAfter(to))
        .map(date -> referencePoint(returns, date))
        .filter(java.util.Objects::nonNull)
        .toList();
  }

  private List<WeeklyNav> lastNavOfEachCompleteWeek(List<FundValue> navValues) {
    var byWeek = new TreeMap<LocalDate, FundValue>();
    navValues.stream()
        .filter(value -> value.value().signum() > 0)
        .sorted(Comparator.comparing(FundValue::date))
        .forEach(value -> byWeek.put(value.date().with(previousOrSame(MONDAY)), value));
    if (byWeek.isEmpty()) {
      return List.of();
    }
    var currentWeekStart = byWeek.lastKey();
    return byWeek.headMap(currentWeekStart).entrySet().stream()
        .map(
            entry ->
                new WeeklyNav(
                    entry.getKey(),
                    entry.getValue().date(),
                    entry.getValue().key(),
                    entry.getValue().value()))
        .toList();
  }

  /**
   * A return needs both the previous ISO week and the same instrument. Splicing a proxy series to
   * the fund's own NAV puts two different price scales in one list, and a return across that join
   * would be a fiction rather than a market move.
   */
  private List<WeeklyReturn> adjacentWeekReturns(List<WeeklyNav> weeklyNavs) {
    var returns = new ArrayList<WeeklyReturn>(weeklyNavs.size());
    for (int i = 1; i < weeklyNavs.size(); i++) {
      var previous = weeklyNavs.get(i - 1);
      var current = weeklyNavs.get(i);
      if (previous.weekStart().plusWeeks(1).equals(current.weekStart())
          && previous.key().equals(current.key())) {
        returns.add(
            new WeeklyReturn(
                current.weekEnd(), current.nav().divide(previous.nav(), MC).subtract(ONE)));
      }
    }
    return returns;
  }

  private @Nullable ReferencePoint referencePoint(List<WeeklyReturn> returns, LocalDate evalDate) {
    var windowStart = evalDate.minusYears(SAMPLE_PERIOD_YEARS);
    var window =
        returns.stream()
            .filter(r -> r.weekEnd().isAfter(windowStart) && !r.weekEnd().isAfter(evalDate))
            .toList();
    if (window.size() < MINIMUM_RETURNS) {
      return null;
    }
    var annualisedVolatility = annualise(window.stream().map(WeeklyReturn::value).toList());
    return new ReferencePoint(
        evalDate,
        isPublishable(window, windowStart) ? RiskClassBucket.srriClass(annualisedVolatility) : null,
        window.size(),
        annualisedVolatility,
        Map.of("weeklyReturns", window.size(), "windowStart", windowStart));
  }

  private boolean isPublishable(List<WeeklyReturn> window, LocalDate windowStart) {
    return window.size() >= MINIMUM_OBSERVATIONS
        && !window
            .getFirst()
            .weekEnd()
            .isAfter(windowStart.plusWeeks(WINDOW_START_TOLERANCE_WEEKS));
  }

  private BigDecimal annualise(List<BigDecimal> weeklyReturns) {
    var count = BigDecimal.valueOf(weeklyReturns.size());
    var mean = weeklyReturns.stream().reduce(ZERO, BigDecimal::add).divide(count, MC);
    var sumOfSquares =
        weeklyReturns.stream()
            .map(value -> value.subtract(mean).pow(2))
            .reduce(ZERO, BigDecimal::add);
    var variance = sumOfSquares.divide(count.subtract(ONE), MC);
    return variance.sqrt(MC).multiply(SQRT_52, MC).setScale(VOLATILITY_SCALE, RoundingMode.HALF_UP);
  }

  private record WeeklyNav(LocalDate weekStart, LocalDate weekEnd, String key, BigDecimal nav) {}

  private record WeeklyReturn(LocalDate weekEnd, BigDecimal value) {}
}
