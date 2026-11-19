package ee.tuleva.onboarding.investment.check.rebalance;

import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.Test;

class NavWeightsTest {

  private static final LocalDate NAV_DATE = LocalDate.of(2026, 10, 30);
  private static final BigDecimal FIVE_POINTS = new BigDecimal("0.05");
  private static final RecursiveComparisonConfiguration BIG_DECIMALS_BY_VALUE =
      RecursiveComparisonConfiguration.builder()
          .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
          .build();

  @Test
  void aHeldInstrumentOutsideTheModel_countsAgainstAModelWeightOfZero() {
    var weights = navWeights(Map.of("WORLD", "940000", "REMOVED", "60000"));

    var deviations = weights.outsideTolerance(Map.of("WORLD", new BigDecimal("1.00")), FIVE_POINTS);

    assertThat(deviations)
        .usingRecursiveFieldByFieldElementComparator(BIG_DECIMALS_BY_VALUE)
        .containsExactly(
            new Deviation("REMOVED", ZERO, new BigDecimal("0.06")),
            new Deviation("WORLD", new BigDecimal("1.00"), new BigDecimal("0.94")));
  }

  @Test
  void aModelInstrumentTheFundDoesNotHold_countsAtAnActualWeightOfZero() {
    var weights = navWeights(Map.of("WORLD", "1000000"));

    var deviations =
        weights.outsideTolerance(
            Map.of("WORLD", new BigDecimal("0.80"), "NEW", new BigDecimal("0.20")), FIVE_POINTS);

    assertThat(deviations).extracting(Deviation::isin).containsExactly("NEW", "WORLD");
    assertThat(deviations)
        .extracting(Deviation::percentagePoints)
        .usingElementComparator(BigDecimal::compareTo)
        .containsExactly(new BigDecimal("-20"), new BigDecimal("20"));
  }

  @Test
  void exactlyFivePointsOff_isWithinToleranceAndAnyMoreIsNot() {
    var model = Map.of("WORLD", new BigDecimal("0.20"), "OTHER", new BigDecimal("0.80"));

    assertThat(
            navWeights(Map.of("WORLD", "250000", "OTHER", "750000"))
                .outsideTolerance(model, FIVE_POINTS))
        .isEmpty();
    assertThat(
            navWeights(Map.of("WORLD", "250001", "OTHER", "749999"))
                .outsideTolerance(model, FIVE_POINTS))
        .extracting(Deviation::isin)
        .containsExactly("OTHER", "WORLD");
  }

  @Test
  void deviationsOfEqualSize_areOrderedByIsin() {
    var weights = navWeights(Map.of("B", "600000", "A", "400000"));

    var deviations =
        weights.outsideTolerance(
            Map.of("A", new BigDecimal("0.50"), "B", new BigDecimal("0.50")), FIVE_POINTS);

    assertThat(deviations).extracting(Deviation::isin).containsExactly("A", "B");
  }

  private static NavWeights navWeights(Map<String, String> marketValues) {
    return new NavWeights(
        NAV_DATE,
        new BigDecimal("1000000"),
        marketValues.entrySet().stream()
            .collect(toMap(Map.Entry::getKey, entry -> new BigDecimal(entry.getValue()))));
  }
}
