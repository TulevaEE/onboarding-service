package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.investment.kid.ScenarioKind.FAVOURABLE;
import static ee.tuleva.onboarding.investment.kid.ScenarioKind.MODERATE;
import static ee.tuleva.onboarding.investment.kid.ScenarioKind.STRESS;
import static ee.tuleva.onboarding.investment.kid.ScenarioKind.UNFAVOURABLE;
import static ee.tuleva.onboarding.investment.kid.WindowSource.FULL_LENGTH;
import static ee.tuleva.onboarding.investment.kid.WindowSource.SHORTER;
import static ee.tuleva.onboarding.investment.kid.WindowSource.STRESSED_VOLATILITY;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ScenarioPresentationTest {

  private static final ScenarioWindow UNFAVOURABLE_WINDOW =
      new ScenarioWindow(SHORTER, date("2024-12-31"), date("2025-12-31"), 12, 0.9);
  private static final ScenarioWindow MODERATE_WINDOW =
      new ScenarioWindow(FULL_LENGTH, date("2018-01-31"), date("2023-01-31"), 60, 1.2345);
  private static final ScenarioWindow FAVOURABLE_WINDOW =
      new ScenarioWindow(FULL_LENGTH, date("2016-03-31"), date("2021-03-31"), 60, 2.0);
  private static final PerformanceScenarios PERFORMANCE =
      new PerformanceScenarios(UNFAVOURABLE_WINDOW, MODERATE_WINDOW, FAVOURABLE_WINDOW);

  private final ScenarioPresentation presentation = new ScenarioPresentation();

  @Test
  void showsAStressWorseThanTheUnfavourableScenarioAsItIs() {
    var stress = new StressScenario(Math.log(0.5), date("2020-02-21"), date("2020-05-20"));

    var scenarios = presentation.present(5, PERFORMANCE, stress, 0);

    assertThat(scenarios)
        .containsExactly(
            new KidScenario(
                5,
                STRESS,
                new BigDecimal("5000"),
                new BigDecimal("-0.12944944"),
                date("2020-02-21"),
                date("2020-05-20"),
                STRESSED_VOLATILITY,
                null),
            new KidScenario(
                5,
                UNFAVOURABLE,
                new BigDecimal("9000"),
                new BigDecimal("-0.02085164"),
                date("2024-12-31"),
                date("2025-12-31"),
                SHORTER,
                12),
            new KidScenario(
                5,
                MODERATE,
                new BigDecimal("12350"),
                new BigDecimal("0.04303341"),
                date("2018-01-31"),
                date("2023-01-31"),
                FULL_LENGTH,
                60),
            new KidScenario(
                5,
                FAVOURABLE,
                new BigDecimal("20000"),
                new BigDecimal("0.14869835"),
                date("2016-03-31"),
                date("2021-03-31"),
                FULL_LENGTH,
                60));
  }

  @Test
  void floorsAStressMilderThanTheUnfavourableScenarioAtTheUnfavourableOne() {
    var stress = new StressScenario(Math.log(0.95), date("2020-02-21"), date("2020-05-20"));

    var scenarios = presentation.present(5, PERFORMANCE, stress, 0);

    assertThat(scenarios.getFirst())
        .isEqualTo(
            new KidScenario(
                5,
                STRESS,
                new BigDecimal("9000"),
                new BigDecimal("-0.02085164"),
                date("2024-12-31"),
                date("2025-12-31"),
                SHORTER,
                12));
  }

  @Test
  void showsAStressAsSevereAsTheUnfavourableScenarioAsTheStress() {
    var stress = new StressScenario(Math.log(0.9), date("2020-02-21"), date("2020-05-20"));
    var unfavourableAsSevere =
        new ScenarioWindow(
            SHORTER, date("2024-12-31"), date("2025-12-31"), 12, Math.exp(Math.log(0.9)));

    var scenarios =
        presentation.present(
            5,
            new PerformanceScenarios(unfavourableAsSevere, MODERATE_WINDOW, FAVOURABLE_WINDOW),
            stress,
            0);

    assertThat(scenarios.getFirst().window()).isEqualTo(STRESSED_VOLATILITY);
  }

  @Test
  void chargesAccrueOverEachWindowsOwnLengthAndTheStressOverTheHorizon() {
    var stress = new StressScenario(Math.log(0.5), date("2020-02-21"), date("2020-05-20"));

    var scenarios = presentation.present(5, PERFORMANCE, stress, 0.01);

    assertThat(scenarios.get(0).amountEur()).isEqualTo(new BigDecimal("4750"));
    assertThat(scenarios.get(0).averageReturnPerYear()).isEqualTo(new BigDecimal("-0.13815494"));
    assertThat(scenarios.get(1).amountEur()).isEqualTo(new BigDecimal("8910"));
    assertThat(scenarios.get(1).averageReturnPerYear()).isEqualTo(new BigDecimal("-0.02281781"));
  }

  @Test
  void roundsAmountsToTheNearestTenEurosWithHalfwayUp() {
    var halfway =
        new PerformanceScenarios(
            UNFAVOURABLE_WINDOW,
            MODERATE_WINDOW,
            new ScenarioWindow(FULL_LENGTH, date("2016-03-31"), date("2021-03-31"), 60, 1.23449));
    var stress = new StressScenario(Math.log(0.5), date("2020-02-21"), date("2020-05-20"));

    var scenarios = presentation.present(5, halfway, stress, 0);

    assertThat(scenarios.get(2).amountEur()).isEqualTo(new BigDecimal("12350"));
    assertThat(scenarios.get(3).amountEur()).isEqualTo(new BigDecimal("12340"));
  }

  private static LocalDate date(String date) {
    return LocalDate.parse(date);
  }
}
