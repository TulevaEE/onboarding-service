package ee.tuleva.onboarding.investment.transaction;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

final class MaximumWeightAssignment {

  private final long[][] weights;
  private final int rows;
  private final int columns;
  private final int size;
  private final long[] rowPotential;
  private final long[] columnPotential;
  private final int[] rowOfColumn;
  private final int[] previousColumn;

  private MaximumWeightAssignment(long[][] weights, int columns) {
    this.weights = weights;
    this.rows = weights.length;
    this.columns = columns;
    this.size = Math.max(rows, columns);
    this.rowPotential = new long[size + 1];
    this.columnPotential = new long[size + 1];
    this.rowOfColumn = new int[size + 1];
    this.previousColumn = new int[size + 1];
  }

  static Map<Integer, Integer> columnsOfRows(long[][] weights, int columns) {
    var assignment = new MaximumWeightAssignment(weights, columns);
    for (var row = 1; row <= assignment.size; row++) {
      assignment.assign(row);
    }
    return assignment.positiveWeightColumnsOfRows();
  }

  private void assign(int row) {
    rowOfColumn[0] = row;
    var slack = new long[size + 1];
    Arrays.fill(slack, Long.MAX_VALUE);
    var visited = new boolean[size + 1];
    var column = 0;
    do {
      column = growTree(column, slack, visited);
    } while (rowOfColumn[column] != 0);
    flipPathEndingAt(column);
  }

  private int growTree(int column, long[] slack, boolean[] visited) {
    visited[column] = true;
    var row = rowOfColumn[column];
    var nextColumn = 0;
    var delta = Long.MAX_VALUE;
    for (var candidate = 1; candidate <= size; candidate++) {
      if (visited[candidate]) {
        continue;
      }
      var reducedCost = cost(row, candidate) - rowPotential[row] - columnPotential[candidate];
      if (reducedCost < slack[candidate]) {
        slack[candidate] = reducedCost;
        previousColumn[candidate] = column;
      }
      if (slack[candidate] < delta) {
        delta = slack[candidate];
        nextColumn = candidate;
      }
    }
    shiftPotentials(delta, slack, visited);
    return nextColumn;
  }

  private void shiftPotentials(long delta, long[] slack, boolean[] visited) {
    for (var column = 0; column <= size; column++) {
      if (visited[column]) {
        rowPotential[rowOfColumn[column]] += delta;
        columnPotential[column] -= delta;
      } else {
        slack[column] -= delta;
      }
    }
  }

  private void flipPathEndingAt(int column) {
    var current = column;
    while (current != 0) {
      var previous = previousColumn[current];
      rowOfColumn[current] = rowOfColumn[previous];
      current = previous;
    }
  }

  private long cost(int row, int column) {
    return isReal(row, column) ? -weights[row - 1][column - 1] : 0;
  }

  private boolean isReal(int row, int column) {
    return row <= rows && column <= columns;
  }

  private Map<Integer, Integer> positiveWeightColumnsOfRows() {
    var columnOfRow = new HashMap<Integer, Integer>();
    for (var column = 1; column <= size; column++) {
      var row = rowOfColumn[column];
      if (isReal(row, column) && weights[row - 1][column - 1] > 0) {
        columnOfRow.put(row - 1, column - 1);
      }
    }
    return Map.copyOf(columnOfRow);
  }
}
