package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.investment.kid.WindowSource.FULL_LENGTH;
import static ee.tuleva.onboarding.investment.kid.WindowSource.SHORTER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PerformanceScenarioCalculatorTest {

  private final PerformanceScenarioCalculator calculator = new PerformanceScenarioCalculator();

  @Test
  void picksTheWorstMedianAndBestOfTheFullLengthWindowsOnTheMonthEndGrid() {
    var prices =
        monthEndPrices(
            YearMonth.of(2025, 1),
            100,
            110,
            90,
            100,
            100,
            100,
            100,
            100,
            100,
            100,
            100,
            100,
            120,
            99,
            99);

    var scenarios = calculator.scenarios(prices, 1, 0);

    assertThat(scenarios)
        .isEqualTo(
            new PerformanceScenarios(
                fullLength("2025-02-28", "2026-02-28", 12, 99.0 / 110),
                fullLength("2025-03-31", "2026-03-31", 12, 99.0 / 90),
                fullLength("2025-01-31", "2026-01-31", 12, 120.0 / 100)));
  }

  @Test
  void aShorterWindowEndingAtThePeriodEndCountsAsItIsNotCompoundedToTheHoldingPeriod() {
    var prices = new ArrayList<Double>();
    prices.add(100.0);
    for (int month = 1; month < 12; month++) {
      prices.add(100.0 + month);
    }
    prices.add(160.0);
    for (int month = 13; month < 24; month++) {
      prices.add(150.0);
    }
    prices.add(140.0);
    var monthEnds = monthEndPrices(YearMonth.of(2024, 1), prices);

    var scenarios = calculator.scenarios(monthEnds, 2, 0);

    assertThat(scenarios.unfavourable())
        .isEqualTo(new ScenarioWindow(SHORTER, date("2025-01-31"), date("2026-01-31"), 12, 0.875));
  }

  @Test
  void theLongestShorterWindowIsOneMonthShortOfTheHoldingPeriod() {
    var prices = new ArrayList<Double>();
    for (int month = 0; month < 25; month++) {
      prices.add(month == 1 ? 200.0 : 100.0 + month);
    }
    var monthEnds = monthEndPrices(YearMonth.of(2024, 1), prices);

    var scenarios = calculator.scenarios(monthEnds, 2, 0);

    assertThat(scenarios.unfavourable())
        .isEqualTo(new ScenarioWindow(SHORTER, date("2024-02-29"), date("2026-01-31"), 23, 0.62));
  }

  @Test
  void theLatestFullLengthWindowKeepsItsOwnSourceWhenItIsTheWorst() {
    var prices = new ArrayList<Double>();
    for (int month = 0; month < 25; month++) {
      prices.add(month == 0 ? 200.0 : 100.0 + month);
    }
    var monthEnds = monthEndPrices(YearMonth.of(2024, 1), prices);

    var scenarios = calculator.scenarios(monthEnds, 2, 0);

    assertThat(scenarios.unfavourable())
        .isEqualTo(
            new ScenarioWindow(FULL_LENGTH, date("2024-01-31"), date("2026-01-31"), 24, 0.62));
  }

  @Test
  void theWorstWindowDependsOnTheChargesBecauseAShorterWindowCarriesFewerYearsOfThem() {
    var full = List.of(fullLength("2020-01-31", "2022-01-31", 24, 0.96), favourableWindow());
    var shorter =
        List.of(new ScenarioWindow(SHORTER, date("2024-12-31"), date("2025-12-31"), 12, 0.955));

    assertThat(PerformanceScenarioCalculator.pick(full, shorter, 0).unfavourable())
        .isEqualTo(shorter.getFirst());
    assertThat(PerformanceScenarioCalculator.pick(full, shorter, 0.01).unfavourable())
        .isEqualTo(full.getFirst());
  }

  @Test
  void favourableAndModerateComeOnlyFromFullLengthWindows() {
    var full =
        List.of(
            fullLength("2020-01-31", "2022-01-31", 24, 1.0),
            fullLength("2020-02-29", "2022-02-28", 24, 1.25),
            fullLength("2020-03-31", "2022-03-31", 24, 1.5));
    var shorter =
        List.of(new ScenarioWindow(SHORTER, date("2024-12-31"), date("2025-12-31"), 12, 3.0));

    var scenarios = PerformanceScenarioCalculator.pick(full, shorter, 0);

    assertThat(scenarios.moderate()).isEqualTo(full.get(1));
    assertThat(scenarios.favourable()).isEqualTo(full.get(2));
  }

  @Test
  void moderateIsTheObservedWindowClosestToTheMedianAndTheEarlierOneWhenTwoAreAsClose() {
    var full =
        List.of(
            fullLength("2020-04-30", "2022-04-30", 24, 1.75),
            fullLength("2020-03-31", "2022-03-31", 24, 1.5),
            fullLength("2020-01-31", "2022-01-31", 24, 0.75),
            fullLength("2020-02-29", "2022-02-28", 24, 1.0));

    var scenarios = PerformanceScenarioCalculator.pick(full, List.of(), 0);

    assertThat(scenarios.moderate()).isEqualTo(full.get(3));
  }

  @Test
  void equallyBadOrGoodWindowsAreSettledByTheEarlierEnd() {
    var full =
        List.of(
            fullLength("2020-03-31", "2022-03-31", 24, 0.5),
            fullLength("2020-01-31", "2022-01-31", 24, 0.5),
            fullLength("2020-02-29", "2022-02-28", 24, 1.0),
            fullLength("2020-05-31", "2022-05-31", 24, 2.0),
            fullLength("2020-04-30", "2022-04-30", 24, 2.0));

    var scenarios = PerformanceScenarioCalculator.pick(full, List.of(), 0);

    assertThat(scenarios.unfavourable()).isEqualTo(full.get(1));
    assertThat(scenarios.favourable()).isEqualTo(full.get(4));
  }

  @Test
  void refusesASeriesWithoutOneFullLengthWindow() {
    var prices = monthEndPrices(YearMonth.of(2025, 1), 100, 101, 102, 103, 104, 105, 106, 107);

    assertThatThrownBy(() -> calculator.scenarios(prices, 1, 0))
        .isInstanceOf(KidCalculationRefusedException.class);
  }

  private static ScenarioWindow favourableWindow() {
    return fullLength("2021-01-31", "2023-01-31", 24, 1.3);
  }

  private static ScenarioWindow fullLength(String start, String end, int months, double gross) {
    return new ScenarioWindow(FULL_LENGTH, date(start), date(end), months, gross);
  }

  private static LocalDate date(String date) {
    return LocalDate.parse(date);
  }

  private static List<MonthEndPrice> monthEndPrices(YearMonth first, double... prices) {
    var values = new ArrayList<Double>();
    for (double price : prices) {
      values.add(price);
    }
    return monthEndPrices(first, values);
  }

  private static List<MonthEndPrice> monthEndPrices(YearMonth first, List<Double> prices) {
    var monthEnds = new ArrayList<MonthEndPrice>();
    for (int i = 0; i < prices.size(); i++) {
      monthEnds.add(new MonthEndPrice(first.plusMonths(i).atEndOfMonth(), prices.get(i)));
    }
    return monthEnds;
  }
}
