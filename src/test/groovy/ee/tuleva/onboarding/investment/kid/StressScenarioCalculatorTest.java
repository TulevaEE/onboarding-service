package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.comparisons.fundvalue.FundValueFixture.aFundValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class StressScenarioCalculatorTest {

  private static final String KEY = "MSCI_ACWI";
  private static final ScenarioPeriod PERIOD =
      new ScenarioPeriod(LocalDate.of(2016, 9, 30), LocalDate.of(2026, 9, 30));
  private static final double Z_AT_FIVE_PERCENT = -1.6448536269514729;
  private static final LocalDate FIVE_YEARS_BEFORE_THE_PERIOD_END = LocalDate.of(2021, 9, 30);

  private final StressScenarioCalculator calculator = new StressScenarioCalculator();

  @Test
  void theStressedLogReturnFollowsTheCornishFisherFormulaOfAnnexFourPointNineteen() {
    var logReturn =
        StressScenarioCalculator.stressedLogReturn(0.0123, -0.62, 7.4, 1305, Z_AT_FIVE_PERCENT);

    assertThat(logReturn).isCloseTo(-0.831696938070616, within(1e-12));
  }

  @Test
  void theOneYearStressIsRepresentedByATwentyOneDayWindow() {
    var closes = PERIOD.dailyCloses(SyntheticIndexSeries.weekdayCloses(KEY));

    var stress = calculator.stress(closes, PERIOD.end(), 1);

    assertThat(returnsBetween(closes, stress)).isEqualTo(21);
  }

  @Test
  void theHoldingPeriodStressIsRepresentedByASixtyThreeDayWindow() {
    var closes = PERIOD.dailyCloses(SyntheticIndexSeries.weekdayCloses(KEY));

    var stress = calculator.stress(closes, PERIOD.end(), 5);

    assertThat(returnsBetween(closes, stress)).isEqualTo(63);
  }

  @Test
  void pricesBeforeTheLastFiveYearsChangeNeitherStressHorizon() {
    var closes = PERIOD.dailyCloses(SyntheticIndexSeries.weekdayCloses(KEY));
    var turbulentBeforeTheLastFiveYears =
        closes.stream()
            .map(
                close ->
                    close.date().isBefore(FIVE_YEARS_BEFORE_THE_PERIOD_END)
                        ? aFundValue(KEY, close.date(), swing(close))
                        : close)
            .toList();

    assertThat(calculator.stress(turbulentBeforeTheLastFiveYears, PERIOD.end(), 1))
        .isEqualTo(calculator.stress(closes, PERIOD.end(), 1));
    assertThat(calculator.stress(turbulentBeforeTheLastFiveYears, PERIOD.end(), 5))
        .isEqualTo(calculator.stress(closes, PERIOD.end(), 5));
  }

  @Test
  void countsTheClosesObservedAfterTheStartOfTheLastHorizon() {
    var closes =
        List.of(close("2025-09-30", 100), close("2025-10-01", 101), close("2026-09-30", 102));

    assertThat(StressScenarioCalculator.observationsInLast(closes, PERIOD.end(), 1)).isEqualTo(2);
  }

  @Test
  void refusesASeriesTooShortForOneFullVolatilityWindow() {
    var closes = SyntheticIndexSeries.weekdayCloses(KEY).subList(0, 60);

    assertThatThrownBy(() -> calculator.stress(closes, PERIOD.end(), 5))
        .isInstanceOf(KidCalculationRefusedException.class);
  }

  @Test
  void refusesASeriesWhoseStressCannotBeComputed() {
    var flat =
        Stream.iterate(LocalDate.of(2026, 1, 5), day -> day.plusDays(1))
            .filter(day -> day.getDayOfWeek().getValue() <= 5)
            .limit(100)
            .map(day -> aFundValue(KEY, day, 100.0))
            .toList();

    assertThatThrownBy(() -> calculator.stress(flat, LocalDate.of(2026, 5, 22), 5))
        .isInstanceOf(KidCalculationRefusedException.class);
  }

  private static long returnsBetween(List<FundValue> closes, StressScenario stress) {
    return closes.stream()
        .filter(close -> !close.date().isBefore(stress.start()))
        .filter(close -> !close.date().isAfter(stress.end()))
        .count();
  }

  private static BigDecimal swing(FundValue close) {
    return close
        .value()
        .multiply(new BigDecimal(close.date().getDayOfMonth() % 2 == 0 ? "1.05" : "0.95"));
  }

  private static FundValue close(String date, double value) {
    return aFundValue(KEY, LocalDate.parse(date), value);
  }
}
