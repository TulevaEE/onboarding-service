package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.investment.kid.WindowSource.FULL_LENGTH;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CostsOverTimeCalculatorTest {

  private final CostsOverTimeCalculator calculator = new CostsOverTimeCalculator();

  @Test
  void theFirstYearAssumesTheInvestorGetsBackWhatTheyInvestedAfterCosts() {
    assertThat(calculator.firstYearAtZeroNetReturn(0.005))
        .isEqualTo(
            new KidCost(
                1,
                new BigDecimal("50.25"),
                new BigDecimal("0.00502513"),
                new BigDecimal("0.00502513"),
                new BigDecimal("0.00000000")));
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
                new BigDecimal("273.23"),
                new BigDecimal("0.00516621"),
                new BigDecimal("0.03324129"),
                new BigDecimal("0.02807509")));
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

    assertThat(calculator.holdingPeriod(5, moderate, 0.005).totalCostsEur())
        .isEqualTo(new BigDecimal("349.61"));
  }
}
