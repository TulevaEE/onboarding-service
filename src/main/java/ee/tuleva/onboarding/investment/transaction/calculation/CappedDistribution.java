package ee.tuleva.onboarding.investment.transaction.calculation;

import static ee.tuleva.onboarding.investment.transaction.calculation.TradeDistribution.DISTRIBUTE_CAPPED_GUARD_MARGIN;
import static ee.tuleva.onboarding.investment.transaction.calculation.TradeDistribution.MIN_MEANINGFUL_AMOUNT;
import static ee.tuleva.onboarding.investment.transaction.calculation.TradeDistribution.SCALE;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
final class CappedDistribution {

  private CappedDistribution() {}

  static BigDecimal[] distributeCapped(
      List<BigDecimal> scores, BigDecimal amount, BigDecimal threshold, List<BigDecimal> caps) {
    int size = scores.size();
    BigDecimal thresholdTolerance = threshold.subtract(MIN_MEANINGFUL_AMOUNT);
    Map<Integer, BigDecimal> fixed = new HashMap<>();
    List<Integer> active = initActiveIndices(scores, caps, thresholdTolerance, size);

    for (int guard = 0;
        guard < size + DISTRIBUTE_CAPPED_GUARD_MARGIN && !active.isEmpty();
        guard++) {
      BigDecimal remaining = amount.subtract(fixed.values().stream().reduce(ZERO, BigDecimal::add));
      if (remaining.compareTo(thresholdTolerance) < 0) {
        break;
      }
      BigDecimal sumScores = active.stream().map(scores::get).reduce(ZERO, BigDecimal::add);
      if (sumScores.compareTo(ZERO) == 0) {
        break;
      }

      CapRound round = computeCapRound(scores, caps, active, remaining, sumScores);

      if (!round.over().isEmpty()) {
        applyCapFixups(fixed, active, caps, round.over());
        continue;
      }
      BigDecimal smallestActiveAllocation =
          Objects.requireNonNull(
              round.minAllocation(), "distributeCapped: active set unexpectedly empty");
      if (smallestActiveAllocation.compareTo(thresholdTolerance) < 0) {
        active.remove(round.minPosition());
        continue;
      }
      settleActiveAllocations(fixed, active, scores, remaining, sumScores);
      active.clear();
    }

    return IntStream.range(0, size)
        .mapToObj(i -> fixed.getOrDefault(i, ZERO).setScale(SCALE, HALF_UP))
        .toArray(BigDecimal[]::new);
  }

  private static List<Integer> initActiveIndices(
      List<BigDecimal> scores, List<BigDecimal> caps, BigDecimal thresholdTolerance, int size) {
    List<Integer> active = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      if (scores.get(i).compareTo(ZERO) > 0 && caps.get(i).compareTo(thresholdTolerance) >= 0) {
        active.add(i);
      }
    }
    return active;
  }

  private static CapRound computeCapRound(
      List<BigDecimal> scores,
      List<BigDecimal> caps,
      List<Integer> active,
      BigDecimal remaining,
      BigDecimal sumScores) {
    List<Integer> over = new ArrayList<>();
    BigDecimal minAllocation = null;
    int minPosition = -1;
    for (int position = 0; position < active.size(); position++) {
      int index = active.get(position);
      BigDecimal allocation =
          scores.get(index).multiply(remaining).divide(sumScores, SCALE, HALF_UP);
      if (allocation.compareTo(caps.get(index)) > 0) {
        over.add(index);
      }
      if (minAllocation == null || allocation.compareTo(minAllocation) < 0) {
        minAllocation = allocation;
        minPosition = position;
      }
    }
    return new CapRound(over, minAllocation, minPosition);
  }

  private static void applyCapFixups(
      Map<Integer, BigDecimal> fixed,
      List<Integer> active,
      List<BigDecimal> caps,
      List<Integer> over) {
    over.forEach(index -> fixed.put(index, caps.get(index)));
    active.removeAll(over);
  }

  private static void settleActiveAllocations(
      Map<Integer, BigDecimal> fixed,
      List<Integer> active,
      List<BigDecimal> scores,
      BigDecimal remaining,
      BigDecimal sumScores) {
    for (int index : active) {
      fixed.put(index, scores.get(index).multiply(remaining).divide(sumScores, SCALE, HALF_UP));
    }
  }

  private record CapRound(
      List<Integer> over, @Nullable BigDecimal minAllocation, int minPosition) {}
}
