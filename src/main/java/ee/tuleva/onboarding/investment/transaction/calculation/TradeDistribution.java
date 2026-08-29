package ee.tuleva.onboarding.investment.transaction.calculation;

import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;
import org.jspecify.annotations.NullMarked;

@NullMarked
final class TradeDistribution {

  static final int SCALE = 2;
  static final int MAX_ITERATIONS = 20;
  static final int DISTRIBUTE_CAPPED_GUARD_MARGIN = 5;
  static final BigDecimal MIN_MEANINGFUL_AMOUNT = new BigDecimal("0.01");

  private TradeDistribution() {}

  static List<BigDecimal> distributeAmountWithThreshold(
      List<BigDecimal> scores, BigDecimal amount, BigDecimal threshold) {
    int size = scores.size();
    boolean[] mask = new boolean[size];
    BigDecimal[] allocations = new BigDecimal[size];

    for (int i = 0; i < size; i++) {
      mask[i] = scores.get(i).compareTo(ZERO) > 0;
      allocations[i] = ZERO;
    }

    for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
      BigDecimal currentSum = ZERO;
      for (int i = 0; i < size; i++) {
        if (mask[i]) {
          currentSum = currentSum.add(scores.get(i));
        }
      }

      if (currentSum.compareTo(ZERO) == 0) {
        break;
      }

      BigDecimal[] tempAllocations = new BigDecimal[size];
      BigDecimal minAllocation = null;

      for (int i = 0; i < size; i++) {
        if (mask[i]) {
          BigDecimal allocation = scores.get(i).multiply(amount).divide(currentSum, SCALE, HALF_UP);
          tempAllocations[i] = allocation;
          if (minAllocation == null || allocation.compareTo(minAllocation) < 0) {
            minAllocation = allocation;
          }
        } else {
          tempAllocations[i] = ZERO;
        }
      }

      BigDecimal thresholdTolerance = threshold.subtract(MIN_MEANINGFUL_AMOUNT);
      if (minAllocation != null && minAllocation.compareTo(thresholdTolerance) >= 0) {
        allocations = tempAllocations;
        break;
      }

      int smallestIndex = -1;
      BigDecimal smallestAllocation = null;
      for (int i = 0; i < size; i++) {
        if (mask[i] && tempAllocations[i].compareTo(thresholdTolerance) < 0) {
          if (smallestAllocation == null || tempAllocations[i].compareTo(smallestAllocation) < 0) {
            smallestIndex = i;
            smallestAllocation = tempAllocations[i];
          }
        }
      }

      boolean changed = smallestIndex >= 0;
      if (changed) {
        mask[smallestIndex] = false;
      }

      if (!changed) {
        break;
      }
    }

    return List.of(allocations);
  }

  static BigDecimal[] distributeCapped(
      List<BigDecimal> scores, BigDecimal amount, BigDecimal threshold, List<BigDecimal> caps) {
    int size = scores.size();
    BigDecimal thresholdTolerance = threshold.subtract(MIN_MEANINGFUL_AMOUNT);
    Map<Integer, BigDecimal> fixed = new HashMap<>();
    List<Integer> active = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      if (scores.get(i).compareTo(ZERO) > 0 && caps.get(i).compareTo(thresholdTolerance) >= 0) {
        active.add(i);
      }
    }

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

      if (!over.isEmpty()) {
        over.forEach(index -> fixed.put(index, caps.get(index)));
        active.removeAll(over);
        continue;
      }
      BigDecimal smallestActiveAllocation =
          Objects.requireNonNull(minAllocation, "distributeCapped: active set unexpectedly empty");
      if (smallestActiveAllocation.compareTo(thresholdTolerance) < 0) {
        active.remove(minPosition);
        continue;
      }
      for (int index : active) {
        fixed.put(index, scores.get(index).multiply(remaining).divide(sumScores, SCALE, HALF_UP));
      }
      active.clear();
    }

    return IntStream.range(0, size)
        .mapToObj(i -> fixed.getOrDefault(i, ZERO).setScale(SCALE, HALF_UP))
        .toArray(BigDecimal[]::new);
  }

  static List<BigDecimal> distributeSellWithCap(
      TulevaFund fund,
      List<BigDecimal> marketValues,
      List<BigDecimal> initialScores,
      BigDecimal targetSellAmount,
      BigDecimal threshold) {
    int size = marketValues.size();

    BigDecimal totalSellableMarketValue = marketValues.stream().reduce(ZERO, BigDecimal::add);
    if (targetSellAmount.subtract(totalSellableMarketValue).compareTo(MIN_MEANINGFUL_AMOUNT) > 0) {
      throw new IllegalStateException(
          "Insufficient liquidity to satisfy sell: fund="
              + fund
              + ", targetSellAmount="
              + targetSellAmount
              + ", sellableMarketValue="
              + totalSellableMarketValue);
    }

    BigDecimal[] allocations = new BigDecimal[size];
    boolean[] capped = new boolean[size];
    for (int i = 0; i < size; i++) {
      allocations[i] = ZERO;
    }

    List<BigDecimal> scores = new ArrayList<>(initialScores);
    BigDecimal remainingNeed = targetSellAmount;

    for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
      if (remainingNeed.compareTo(MIN_MEANINGFUL_AMOUNT) <= 0) {
        break;
      }

      List<BigDecimal> roundScores =
          IntStream.range(0, size).mapToObj(i -> capped[i] ? ZERO : scores.get(i)).toList();
      if (roundScores.stream().allMatch(s -> s.compareTo(ZERO) == 0)) {
        break;
      }

      List<BigDecimal> round = distributeAmountWithThreshold(roundScores, remainingNeed, threshold);

      boolean newlyCapped = false;
      for (int i = 0; i < size; i++) {
        if (capped[i]) {
          continue;
        }
        BigDecimal proposed = allocations[i].add(round.get(i));
        BigDecimal marketValue = marketValues.get(i);
        if (proposed.compareTo(marketValue) >= 0) {
          allocations[i] = marketValue;
          capped[i] = true;
          newlyCapped = true;
        } else {
          allocations[i] = proposed;
        }
      }

      BigDecimal allocated =
          IntStream.range(0, size).mapToObj(i -> allocations[i]).reduce(ZERO, BigDecimal::add);
      remainingNeed = targetSellAmount.subtract(allocated);

      if (!newlyCapped) {
        break;
      }
    }

    return List.of(allocations);
  }
}
