package ee.tuleva.onboarding.comparisons.returns;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.ToDoubleFunction;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class FundPriceTiming {

  static final String CACHE = "fundPriceTiming";
  static final double SAME_DAY = 1.0;

  private static final int ESTIMATION_MONTHS = 6;
  private static final int CALENDAR_DAYS_BEFORE_THE_FIRST_INDEX_CLOSE_NEEDED = 10;
  private static final int MINIMUM_DAILY_MOVES = 60;
  private static final double MINIMUM_SHARE_OF_INDEX_MOVES_THE_FUND_FOLLOWS = 0.15;

  private final FundValueProvider fundValueProvider;

  @Cacheable(
      cacheNames = CACHE,
      key = "#isin + ':' + #index + ':' + #month",
      unless = "#result == null")
  public @Nullable Double sameDayWeight(String isin, String index, YearMonth month) {
    var to = month.atDay(1).minusDays(1);
    var from = month.minusMonths(ESTIMATION_MONTHS).atDay(1);
    var fundValues = fundValueProvider.findValuesBetweenDates(isin, from, to);
    var indexCloses =
        closes(
            fundValueProvider.findValuesBetweenDates(
                index, from.minusDays(CALENDAR_DAYS_BEFORE_THE_FIRST_INDEX_CLOSE_NEEDED), to));
    var moves = dailyMoves(fundValues, indexCloses);
    if (moves.size() < MINIMUM_DAILY_MOVES) {
      return null;
    }
    var regression = Regression.of(moves);
    var sharesFollowed = regression.sameDay() + regression.previousDay();
    if (!Double.isFinite(sharesFollowed)
        || sharesFollowed < MINIMUM_SHARE_OF_INDEX_MOVES_THE_FUND_FOLLOWS) {
      return SAME_DAY;
    }
    return Math.clamp(regression.sameDay() / sharesFollowed, 0.0, 1.0);
  }

  private static NavigableMap<LocalDate, Double> closes(List<FundValue> values) {
    var closes = new TreeMap<LocalDate, Double>();
    values.forEach(value -> closes.put(value.date(), value.value().doubleValue()));
    return closes;
  }

  private static List<DailyMove> dailyMoves(
      List<FundValue> fundValues, NavigableMap<LocalDate, Double> indexCloses) {
    return IntStream.range(1, fundValues.size())
        .mapToObj(day -> dailyMove(fundValues.get(day - 1), fundValues.get(day), indexCloses))
        .flatMap(Optional::stream)
        .toList();
  }

  private static Optional<DailyMove> dailyMove(
      FundValue before, FundValue after, NavigableMap<LocalDate, Double> indexCloses) {
    var indexBefore = indexCloses.floorEntry(before.date());
    var indexAfter = indexCloses.floorEntry(after.date());
    if (indexBefore == null || indexAfter == null) {
      return Optional.empty();
    }
    var indexBeforeThat = indexCloses.lowerEntry(indexBefore.getKey());
    var indexAfterThat = indexCloses.lowerEntry(indexAfter.getKey());
    if (indexBeforeThat == null || indexAfterThat == null) {
      return Optional.empty();
    }
    return Optional.of(
        new DailyMove(
            Math.log(after.value().doubleValue() / before.value().doubleValue()),
            Math.log(indexAfter.getValue() / indexBefore.getValue()),
            Math.log(indexAfterThat.getValue() / indexBeforeThat.getValue())));
  }

  private record DailyMove(double fund, double indexSameDay, double indexPreviousDay) {}

  private record Regression(double sameDay, double previousDay) {

    static Regression of(List<DailyMove> moves) {
      var fundMean = mean(moves, DailyMove::fund);
      var sameMean = mean(moves, DailyMove::indexSameDay);
      var previousMean = mean(moves, DailyMove::indexPreviousDay);
      ToDoubleFunction<DailyMove> fund = move -> move.fund() - fundMean;
      ToDoubleFunction<DailyMove> same = move -> move.indexSameDay() - sameMean;
      ToDoubleFunction<DailyMove> previous = move -> move.indexPreviousDay() - previousMean;
      var sameSquares = sumOfProducts(moves, same, same);
      var previousSquares = sumOfProducts(moves, previous, previous);
      var crossSquares = sumOfProducts(moves, same, previous);
      var sameWithFund = sumOfProducts(moves, same, fund);
      var previousWithFund = sumOfProducts(moves, previous, fund);
      var determinant = sameSquares * previousSquares - crossSquares * crossSquares;
      return new Regression(
          (sameWithFund * previousSquares - previousWithFund * crossSquares) / determinant,
          (previousWithFund * sameSquares - sameWithFund * crossSquares) / determinant);
    }

    private static double mean(List<DailyMove> moves, ToDoubleFunction<DailyMove> value) {
      return moves.stream().mapToDouble(value).average().orElseThrow();
    }

    private static double sumOfProducts(
        List<DailyMove> moves,
        ToDoubleFunction<DailyMove> left,
        ToDoubleFunction<DailyMove> right) {
      return moves.stream()
          .mapToDouble(move -> left.applyAsDouble(move) * right.applyAsDouble(move))
          .sum();
    }
  }
}
