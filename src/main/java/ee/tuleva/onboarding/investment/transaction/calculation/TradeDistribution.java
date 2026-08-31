package ee.tuleva.onboarding.investment.transaction.calculation;

import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

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
      BigDecimal currentSum = sumMasked(scores, mask);

      if (currentSum.compareTo(ZERO) == 0) {
        break;
      }

      BigDecimal thresholdTolerance = threshold.subtract(MIN_MEANINGFUL_AMOUNT);
      AllocationRound round = computeRoundAllocations(scores, mask, amount, currentSum);

      if (round.minAllocation() != null
          && round.minAllocation().compareTo(thresholdTolerance) >= 0) {
        allocations = round.allocations();
        break;
      }

      int smallestIndex = findSmallestBelowTolerance(mask, round.allocations(), thresholdTolerance);

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

  private static BigDecimal sumMasked(List<BigDecimal> scores, boolean[] mask) {
    BigDecimal sum = ZERO;
    for (int i = 0; i < mask.length; i++) {
      if (mask[i]) {
        sum = sum.add(scores.get(i));
      }
    }
    return sum;
  }

  private static AllocationRound computeRoundAllocations(
      List<BigDecimal> scores, boolean[] mask, BigDecimal amount, BigDecimal currentSum) {
    BigDecimal[] tempAllocations = new BigDecimal[mask.length];
    BigDecimal minAllocation = null;

    for (int i = 0; i < mask.length; i++) {
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

    return new AllocationRound(tempAllocations, minAllocation);
  }

  private static int findSmallestBelowTolerance(
      boolean[] mask, BigDecimal[] tempAllocations, BigDecimal thresholdTolerance) {
    int smallestIndex = -1;
    BigDecimal smallestAllocation = null;
    for (int i = 0; i < mask.length; i++) {
      if (mask[i] && tempAllocations[i].compareTo(thresholdTolerance) < 0) {
        if (smallestAllocation == null || tempAllocations[i].compareTo(smallestAllocation) < 0) {
          smallestIndex = i;
          smallestAllocation = tempAllocations[i];
        }
      }
    }
    return smallestIndex;
  }

  private record AllocationRound(BigDecimal[] allocations, @Nullable BigDecimal minAllocation) {}

  static List<BigDecimal> distributeSellWithCap(
      TulevaFund fund,
      List<BigDecimal> marketValues,
      List<BigDecimal> initialScores,
      BigDecimal targetSellAmount,
      BigDecimal threshold) {
    int size = marketValues.size();
    validateSellLiquidity(fund, marketValues, targetSellAmount);

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

      List<BigDecimal> roundScores = maskCappedScores(scores, capped, size);
      if (roundScores.stream().allMatch(s -> s.compareTo(ZERO) == 0)) {
        break;
      }

      List<BigDecimal> round = distributeAmountWithThreshold(roundScores, remainingNeed, threshold);
      boolean newlyCapped = applyRoundToAllocations(allocations, capped, marketValues, round, size);

      BigDecimal allocated =
          IntStream.range(0, size).mapToObj(i -> allocations[i]).reduce(ZERO, BigDecimal::add);
      remainingNeed = targetSellAmount.subtract(allocated);

      if (!newlyCapped) {
        break;
      }
    }

    return List.of(allocations);
  }

  private static void validateSellLiquidity(
      TulevaFund fund, List<BigDecimal> marketValues, BigDecimal targetSellAmount) {
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
  }

  private static List<BigDecimal> maskCappedScores(
      List<BigDecimal> scores, boolean[] capped, int size) {
    return IntStream.range(0, size).mapToObj(i -> capped[i] ? ZERO : scores.get(i)).toList();
  }

  private static boolean applyRoundToAllocations(
      BigDecimal[] allocations,
      boolean[] capped,
      List<BigDecimal> marketValues,
      List<BigDecimal> round,
      int size) {
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
    return newlyCapped;
  }
}
