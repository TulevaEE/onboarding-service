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
      theFirstYearAssumesTheInvestorGetsBackWhatTheyInvestedAfterCostsInWholeEurosAndHundredthsOfAPercent() {
    assertThat(calculator.firstYearAtZeroNetReturn(0.005))
        .isEqualTo(
            new KidCost(
                1,
                new BigDecimal("50"),
                new BigDecimal("0.0050"),
                new BigDecimal("0.0050"),
                new BigDecimal("0.0000")));
  }

  @Test
  void theFirstYearChargesTheGrossValueNotTheAmountInvested() {
    assertThat(calculator.firstYearAtZeroNetReturn(0.04))
        .isEqualTo(
            new KidCost(
                1,
                new BigDecimal("417"),
                new BigDecimal("0.0417"),
                new BigDecimal("0.0417"),
                new BigDecimal("0.0000")));
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
                new BigDecimal("0.0052"),
                new BigDecimal("0.0332"),
                new BigDecimal("0.0281")));
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
                new BigDecimal("0.0056"),
                new BigDecimal("0.1178"),
                new BigDecimal("0.1122")));
  }
}
