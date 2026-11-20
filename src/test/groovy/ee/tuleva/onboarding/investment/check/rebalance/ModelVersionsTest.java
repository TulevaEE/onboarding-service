package ee.tuleva.onboarding.investment.check.rebalance;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class ModelVersionsTest {

  private static final LocalDate PREVIOUS = LocalDate.of(2026, 6, 1);
  private static final LocalDate CURRENT = LocalDate.of(2026, 10, 1);

  @Test
  void aWeightChangeAnAddedInstrumentAndARemovedOne_areAllChangedInstruments() {
    var previous =
        List.of(allocation(PREVIOUS, "WORLD", "0.60"), allocation(PREVIOUS, "EMERGING", "0.40"));
    var current =
        List.of(
            allocation(CURRENT, "EMERGING", "0.30"),
            allocation(CURRENT, "EUROPE", "0.10"),
            allocation(CURRENT, "USA", "0.60"));

    var change = changesIn(history(previous, current)).getFirst();

    assertThat(change.effectiveDate()).isEqualTo(CURRENT);
    assertThat(change.changedIsins())
        .containsExactlyInAnyOrder("WORLD", "EMERGING", "EUROPE", "USA");
  }

  @Test
  void anInstrumentKeptAtZeroInTheNewVersion_isRemovedAndOneAtZeroInBoth_isUnchanged() {
    var previous =
        List.of(allocation(PREVIOUS, "WORLD", "1.00"), allocation(PREVIOUS, "LIQUIDATED", "0.00"));
    var current =
        List.of(
            allocation(CURRENT, "WORLD", "0"),
            allocation(CURRENT, "LIQUIDATED", "0"),
            allocation(CURRENT, "NEW_WORLD", "1.00"));

    var change = changesIn(history(previous, current)).getFirst();

    assertThat(change.changedIsins()).containsExactlyInAnyOrder("WORLD", "NEW_WORLD");
  }

  @Test
  void theSameWeightsAtAnotherScale_areNoChange() {
    var previous = List.of(allocation(PREVIOUS, "WORLD", "1.00"));
    var current = List.of(allocation(CURRENT, "WORLD", "1.00000000"));

    assertThat(changesIn(history(previous, current))).isEmpty();
  }

  @Test
  void aRowWithoutAnIsin_isNotAModelInstrument() {
    var previous =
        List.of(allocation(PREVIOUS, "WORLD", "0.99"), allocation(PREVIOUS, null, "0.01"));
    var current = List.of(allocation(CURRENT, "WORLD", "0.99"), allocation(CURRENT, null, "0.005"));

    assertThat(changesIn(history(previous, current))).isEmpty();
  }

  @Test
  void aLaterVersionWithTheSameWeights_keepsTheEarlierChangeAndItsEffectiveDate() {
    var later = LocalDate.of(2026, 11, 1);
    var history =
        List.of(
            allocation(PREVIOUS, "WORLD", "1.00"),
            allocation(CURRENT, "WORLD", "0"),
            allocation(CURRENT, "NEW_WORLD", "1.00"),
            allocation(later, "NEW_WORLD", "1.000"));

    var change = changesIn(history).getFirst();

    assertThat(change.effectiveDate()).isEqualTo(CURRENT);
    assertThat(change.changedIsins()).containsExactlyInAnyOrder("WORLD", "NEW_WORLD");
  }

  @Test
  void ofTwoChanges_theLatestOneCounts() {
    var later = LocalDate.of(2026, 11, 1);
    var history =
        List.of(
            allocation(later, "EUROPE", "1.00"),
            allocation(PREVIOUS, "WORLD", "1.00"),
            allocation(CURRENT, "NEW_WORLD", "1.00"));

    var change = changesIn(history).getFirst();

    assertThat(change.effectiveDate()).isEqualTo(later);
    assertThat(change.changedIsins()).containsExactlyInAnyOrder("NEW_WORLD", "EUROPE");
  }

  @Test
  void aHistoryOfOneVersionOrOfVersionsWithTheSameWeights_hasNoChange() {
    assertThat(changesIn(List.of(allocation(CURRENT, "WORLD", "1.00")))).isEmpty();
    assertThat(
            changesIn(
                List.of(allocation(PREVIOUS, "WORLD", "1.00"), allocation(CURRENT, "WORLD", "1"))))
        .isEmpty();
    assertThat(changesIn(List.of())).isEmpty();
  }

  @Test
  void allChangesInAHistory_areListedNewestFirstSkippingVersionsWithTheSameWeights() {
    var august = LocalDate.of(2026, 8, 1);
    var november = LocalDate.of(2026, 11, 1);
    var history =
        List.of(
            allocation(PREVIOUS, "WORLD", "1.00"),
            allocation(august, "NEW_WORLD", "1.00"),
            allocation(CURRENT, "NEW_WORLD", "1.0"),
            allocation(november, "EUROPE", "1.00"));

    assertThat(changesIn(history))
        .extracting(ModelChange::effectiveDate)
        .containsExactly(november, august);
  }

  @Test
  void theWeightsOnADate_areThoseOfTheNewestVersionOnOrBeforeItIncludingItsZeroPercentRows() {
    var later = LocalDate.of(2026, 10, 15);
    var versions =
        ModelVersions.of(
            List.of(
                allocation(PREVIOUS, "WORLD", "1.00"),
                allocation(CURRENT, "NEW_WORLD", "1.00"),
                allocation(later, "NEW_WORLD", "1.00"),
                allocation(later, "HELD", "0"),
                allocation(later, null, "0.01")));

    assertThat(versions.weightsOn(CURRENT)).isEqualTo(Map.of("NEW_WORLD", new BigDecimal("1.00")));
    assertThat(versions.weightsOn(later.minusDays(1)))
        .isEqualTo(Map.of("NEW_WORLD", new BigDecimal("1.00")));
    assertThat(versions.weightsOn(later.plusDays(5)))
        .isEqualTo(Map.of("NEW_WORLD", new BigDecimal("1.00"), "HELD", new BigDecimal("0")));
  }

  private static List<ModelChange> changesIn(List<ModelPortfolioAllocation> history) {
    return ModelVersions.of(history).changesNewestFirst();
  }

  private static List<ModelPortfolioAllocation> history(
      List<ModelPortfolioAllocation> previous, List<ModelPortfolioAllocation> current) {
    return Stream.concat(previous.stream(), current.stream()).toList();
  }

  private static ModelPortfolioAllocation allocation(
      LocalDate effectiveDate, @Nullable String isin, String weight) {
    return ModelPortfolioAllocation.builder()
        .fund(TUV100)
        .effectiveDate(effectiveDate)
        .isin(isin)
        .weight(new BigDecimal(weight))
        .build();
  }
}
