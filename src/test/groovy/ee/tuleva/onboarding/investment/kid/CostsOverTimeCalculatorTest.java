package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.investment.kid.WindowSource.FULL_LENGTH;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CostsOverTimeCalculatorTest {

  private final CostsOverTimeCalculator calculator = new CostsOverTimeCalculator();

  @Test
  void
      theFirstYearAssumesTheInvestorGetsBackWhatTheyInvestedAfterCostsInWholeEurosAndTenthsOfAPercent() {
    assertThat(calculator.firstYearAtZeroNetReturn(0.005))
        .isEqualTo(
            new KidCost(
                1,
                new BigDecimal("50"),
                new BigDecimal("0.005"),
                new BigDecimal("0.005"),
                new BigDecimal("0.000")));
  }

  @Test
  void theFirstYearChargesTheGrossValueNotTheAmountInvested() {
    assertThat(calculator.firstYearAtZeroNetReturn(0.04))
        .isEqualTo(
            new KidCost(
                1,
                new BigDecimal("417"),
                new BigDecimal("0.042"),
                new BigDecimal("0.042"),
                new BigDecimal("0.000")));
  }

  @Test
  void theHoldingPeriodFollowsTheModerateWindowsOwnGrowthBeforeCosts() {
    var moderate =
        new ScenarioWindow(
            FULL_LENGTH,
            LocalDate.of(2019, 1, 31),
            LocalDate.of(2024, 1, 31),
            60,
            1.1776297577478219);

    assertThat(calculator.holdingPeriod(5, moderate, 0.005))
        .isEqualTo(
            new KidCost(
                5,
                new BigDecimal("273"),
                new BigDecimal("0.005"),
                new BigDecimal("0.033"),
                new BigDecimal("0.028")));
  }

  @Test
  void totalCostsAreEachYearsCostsAddedUpNotTheGapBetweenTheTwoEndValues() {
    var moderate =
        new ScenarioWindow(
            FULL_LENGTH,
            LocalDate.of(2019, 3, 31),
            LocalDate.of(2024, 3, 31),
            60,
            Math.pow(1.1178, 5));

    assertThat(calculator.holdingPeriod(5, moderate, 0.005))
        .isEqualTo(
            new KidCost(
                5,
                new BigDecimal("350"),
                new BigDecimal("0.006"),
                new BigDecimal("0.118"),
                new BigDecimal("0.112")));
  }
}
