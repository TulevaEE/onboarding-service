package ee.tuleva.onboarding.investment.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MaximumWeightAssignmentTest {

  @Test
  void picksTheAssignmentWithTheLargestTotalWeightOverTheGreedyOne() {
    long[][] weights = {
      {10, 9, 0},
      {9, 0, 0},
      {0, 8, 1}
    };

    assertThat(MaximumWeightAssignment.columnsOfRows(weights, 3))
        .isEqualTo(Map.of(0, 1, 1, 0, 2, 2));
  }

  @Test
  void leavesARowUnassignedWhenOnlyAZeroWeightColumnIsLeftForIt() {
    long[][] weights = {
      {5, 0},
      {4, 0}
    };

    assertThat(MaximumWeightAssignment.columnsOfRows(weights, 2)).isEqualTo(Map.of(0, 0));
  }

  @Test
  void assignsMoreRowsThanColumnsToTheColumnsThereAre() {
    long[][] weights = {{3}, {7}, {5}};

    assertThat(MaximumWeightAssignment.columnsOfRows(weights, 1)).isEqualTo(Map.of(1, 0));
  }

  @Test
  void assignsFewerRowsThanColumnsToTheirBestColumns() {
    long[][] weights = {{1, 0, 6}};

    assertThat(MaximumWeightAssignment.columnsOfRows(weights, 3)).isEqualTo(Map.of(0, 2));
  }

  @Test
  void hasNothingToAssignWithoutRowsOrColumns() {
    assertThat(MaximumWeightAssignment.columnsOfRows(new long[0][], 2)).isEmpty();
    assertThat(MaximumWeightAssignment.columnsOfRows(new long[][] {{}, {}}, 0)).isEmpty();
  }
}
