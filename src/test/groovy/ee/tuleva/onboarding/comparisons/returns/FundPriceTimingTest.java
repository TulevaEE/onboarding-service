package ee.tuleva.onboarding.comparisons.returns;

import static ee.tuleva.onboarding.comparisons.fundvalue.FundValueFixture.aFundValue;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FundPriceTimingTest {

  private static final String INDEX = "WORLD_INDEX";
  private static final YearMonth AUGUST_2025 = YearMonth.of(2025, 8);

  private final FundValueProvider fundValueProvider = mock(FundValueProvider.class);
  private final FundPriceTiming timing = new FundPriceTiming(fundValueProvider);
  private final Map<String, List<FundValue>> series = new HashMap<>();

  private List<LocalDate> tradingDays;
  private double[] indexMoves;

  @BeforeEach
  void setUp() {
    tradingDays =
        LocalDate.of(2025, 1, 1)
            .datesUntil(LocalDate.of(2025, 8, 1))
            .filter(day -> day.getDayOfWeek() != SATURDAY && day.getDayOfWeek() != SUNDAY)
            .toList();
    var random = new Random(42);
    indexMoves = random.doubles(tradingDays.size(), -0.02, 0.02).toArray();
    series.put(INDEX, levels(INDEX, indexMoves));
    given(fundValueProvider.findValuesBetweenDates(anyString(), any(), any()))
        .willAnswer(
            invocation -> {
              LocalDate from = invocation.getArgument(1);
              LocalDate to = invocation.getArgument(2);
              return series.getOrDefault(invocation.getArgument(0), List.of()).stream()
                  .filter(value -> !value.date().isBefore(from) && !value.date().isAfter(to))
                  .toList();
            });
  }

  @Test
  void aFundThatMovesWithTheIndexOnTheSameDayPricesTheSameDay() {
    series.put("SAME_DAY", levels("SAME_DAY", blendedMoves(1.0)));

    assertThat(timing.sameDayWeight("SAME_DAY", INDEX, AUGUST_2025)).isCloseTo(1.0, within(0.02));
  }

  @Test
  void aFundThatFollowsTheIndexADayLatePricesAtThePreviousDaysClose() {
    series.put("DAY_LATE", levels("DAY_LATE", blendedMoves(0.0)));

    assertThat(timing.sameDayWeight("DAY_LATE", INDEX, AUGUST_2025)).isCloseTo(0.0, within(0.02));
  }

  @Test
  void aFundThatHoldsSomeOfEachKindOfInstrumentGetsTheMatchingShare() {
    series.put("MIXED", levels("MIXED", blendedMoves(0.3)));

    assertThat(timing.sameDayWeight("MIXED", INDEX, AUGUST_2025)).isCloseTo(0.3, within(0.03));
  }

  @Test
  void onlyTheSixMonthsBeforeTheMonthCountSoAPortfolioChangeShowsUpWithin() {
    var movesThatSwitchTiming = new double[tradingDays.size()];
    for (var day = 0; day < tradingDays.size(); day++) {
      var sameDayWeight = tradingDays.get(day).isBefore(LocalDate.of(2025, 2, 1)) ? 1.0 : 0.0;
      movesThatSwitchTiming[day] = blendedMoves(sameDayWeight)[day];
    }
    series.put("SWITCHED", levels("SWITCHED", movesThatSwitchTiming));

    assertThat(timing.sameDayWeight("SWITCHED", INDEX, AUGUST_2025)).isCloseTo(0.0, within(0.02));
  }

  @Test
  void aFundThatBarelyMovesWithStocksPricesTheSameDay() {
    var bondLikeMoves = new Random(7).doubles(tradingDays.size(), -0.001, 0.001).toArray();
    series.put("BONDS", levels("BONDS", bondLikeMoves));

    assertThat(timing.sameDayWeight("BONDS", INDEX, AUGUST_2025)).isEqualTo(1.0);
  }

  @Test
  void hasNoEstimateForAFundWithTooLittleHistorySoItIsNotKeptOnceTheHistoryArrives() {
    var lastMonth =
        levels("YOUNG", blendedMoves(0.0)).stream()
            .filter(value -> value.date().isAfter(LocalDate.of(2025, 7, 1)))
            .toList();
    series.put("YOUNG", lastMonth);

    assertThat(timing.sameDayWeight("YOUNG", INDEX, AUGUST_2025)).isNull();
  }

  private double[] blendedMoves(double sameDayWeight) {
    var moves = new double[tradingDays.size()];
    for (var day = 1; day < tradingDays.size(); day++) {
      moves[day] = sameDayWeight * indexMoves[day] + (1 - sameDayWeight) * indexMoves[day - 1];
    }
    return moves;
  }

  private List<FundValue> levels(String key, double[] moves) {
    var values = new ArrayList<FundValue>();
    var level = 100.0;
    for (var day = 0; day < tradingDays.size(); day++) {
      level *= Math.exp(moves[day]);
      values.add(aFundValue(key, tradingDays.get(day), level));
    }
    return values;
  }
}
