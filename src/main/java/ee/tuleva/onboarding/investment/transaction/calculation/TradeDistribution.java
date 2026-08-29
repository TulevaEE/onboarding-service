package ee.tuleva.onboarding.investment.transaction.calculation;

import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.transaction.FundTransactionInput;
import ee.tuleva.onboarding.investment.transaction.PositionSnapshot;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

  static BigDecimal waterFillExcessAcrossRunners(
      FundTransactionInput input, List<BigDecimal> scores, BigDecimal[] capped, BigDecimal excess) {
    int size = capped.length;
    BigDecimal threshold = input.minTransactionThreshold();
    BigDecimal remaining = excess;

    for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
      if (remaining.compareTo(MIN_MEANINGFUL_AMOUNT) <= 0) {
        break;
      }

      List<BigDecimal> roundScores = runnerScores(input, scores, capped);
      if (roundScores.stream().allMatch(score -> score.compareTo(ZERO) == 0)) {
        break;
      }

      List<BigDecimal> round = distributeAmountWithThreshold(roundScores, remaining, threshold);

      boolean newlyCapped = anyIndexExceedsHeadroom(input, capped, round);
      remaining = applyRoundToCapped(input, capped, round, remaining);

      if (!newlyCapped) {
        break;
      }
    }

    return remaining;
  }

  private static boolean anyIndexExceedsHeadroom(
      FundTransactionInput input, BigDecimal[] capped, List<BigDecimal> round) {
    for (int i = 0; i < capped.length; i++) {
      if (round.get(i).compareTo(ZERO) <= 0) {
        continue;
      }
      BigDecimal headroom = remainingHeadroom(input, capped, i);
      if (headroom != null && round.get(i).compareTo(headroom) >= 0) {
        return true;
      }
    }
    return false;
  }

  private static BigDecimal applyRoundToCapped(
      FundTransactionInput input,
      BigDecimal[] capped,
      List<BigDecimal> round,
      BigDecimal remaining) {
    for (int i = 0; i < capped.length; i++) {
      if (round.get(i).compareTo(ZERO) <= 0) {
        continue;
      }
      BigDecimal headroom = remainingHeadroom(input, capped, i);
      if (headroom != null && round.get(i).compareTo(headroom) >= 0) {
        capped[i] = capped[i].add(headroom);
        remaining = remaining.subtract(headroom);
      } else {
        capped[i] = capped[i].add(round.get(i));
        remaining = remaining.subtract(round.get(i));
      }
    }
    return remaining;
  }

  static List<BigDecimal> runnerScores(
      FundTransactionInput input, List<BigDecimal> scores, BigDecimal[] capped) {
    BigDecimal threshold = input.minTransactionThreshold();
    return IntStream.range(0, capped.length)
        .mapToObj(
            i -> {
              BigDecimal remaining = remainingHeadroom(input, capped, i);
              boolean eligible =
                  scores.get(i).compareTo(ZERO) > 0
                      && (remaining == null || remaining.compareTo(threshold) >= 0);
              return eligible ? scores.get(i) : ZERO;
            })
        .toList();
  }

  private static @Nullable BigDecimal remainingHeadroom(
      FundTransactionInput input, BigDecimal[] capped, int index) {
    BigDecimal headroom = hardLimitHeadroom(input, input.positions().get(index));
    return headroom == null ? null : headroom.subtract(capped[index]).max(ZERO);
  }

  static @Nullable BigDecimal hardLimitHeadroom(
      FundTransactionInput input, PositionSnapshot position) {
    var limits = input.positionLimits().get(position.isin());
    if (limits == null) {
      return null;
    }
    BigDecimal maxAllowedMarketValue =
        input.grossPortfolioValue().multiply(limits.hardLimit().subtract(new BigDecimal("0.0001")));
    return maxAllowedMarketValue.subtract(position.marketValue()).max(ZERO);
  }

  static List<Integer> fastSellIndices(FundTransactionInput input) {
    Set<String> fastIsins = input.fastSellIsins();
    return IntStream.range(0, input.positions().size())
        .filter(i -> fastIsins.contains(input.positions().get(i).isin()))
        .boxed()
        .toList();
  }

  static List<Integer> slowSellIndices(FundTransactionInput input, List<Integer> fastIndices) {
    return IntStream.range(0, input.positions().size())
        .filter(i -> !fastIndices.contains(i))
        .boxed()
        .toList();
  }

  static BigDecimal totalMarketValue(FundTransactionInput input, List<Integer> indices) {
    return indices.stream()
        .map(i -> input.positions().get(i).marketValue())
        .reduce(ZERO, BigDecimal::add);
  }

  static void sellFastBucket(
      FundTransactionInput input,
      List<Integer> fastIndices,
      List<BigDecimal> targetValues,
      BigDecimal targetAmount,
      BigDecimal totalFastValue,
      BigDecimal[] results) {
    if (fastIndices.isEmpty()) {
      return;
    }
    if (targetAmount.compareTo(totalFastValue) >= 0) {
      for (int i : fastIndices) {
        results[i] = input.positions().get(i).marketValue().negate();
      }
    } else {
      distributeSellByOverweight(input, fastIndices, targetValues, targetAmount, results);
    }
  }

  static List<BigDecimal> finalizeSells(
      FundTransactionInput input,
      BigDecimal[] results,
      List<BigDecimal> targetValues,
      BigDecimal targetAmount) {
    int size = results.length;
    List<BigDecimal> sells = IntStream.range(0, size).mapToObj(i -> results[i].negate()).toList();
    List<BigDecimal> overweightScores =
        IntStream.range(0, size)
            .mapToObj(
                i -> input.positions().get(i).marketValue().subtract(targetValues.get(i)).max(ZERO))
            .toList();
    List<BigDecimal> raised = applySellSafetySpill(sells, input, overweightScores, targetAmount);
    return raised.stream().map(BigDecimal::negate).toList();
  }

  static void distributeSellByOverweight(
      FundTransactionInput input,
      List<Integer> bucketIndices,
      List<BigDecimal> targetValues,
      BigDecimal amount,
      BigDecimal[] results) {
    BigDecimal threshold = input.minTransactionThreshold();
    BigDecimal thresholdTolerance = threshold.subtract(MIN_MEANINGFUL_AMOUNT);
    List<Integer> active = new ArrayList<>(bucketIndices);
    Map<Integer, BigDecimal> filled = new HashMap<>();
    BigDecimal remaining = amount;

    for (int iteration = 0; iteration < MAX_ITERATIONS && !active.isEmpty(); iteration++) {
      if (remaining.compareTo(MIN_MEANINGFUL_AMOUNT) <= 0) {
        break;
      }

      Map<Integer, BigDecimal> scores = resolveScores(input, filled, active, targetValues);
      BigDecimal totalScore = sumScoreValues(scores);
      if (totalScore.compareTo(ZERO) == 0) {
        break;
      }

      Map<Integer, BigDecimal> allocations =
          computeCappedAllocations(input, filled, active, scores, totalScore, remaining);
      List<Integer> cappedIndices = cappedIndices(input, filled, active, allocations);

      if (!cappedIndices.isEmpty()) {
        remaining = applyCappedAllocations(filled, active, cappedIndices, allocations, remaining);
        continue;
      }

      if (settleMinAllocation(filled, active, allocations, thresholdTolerance)) {
        break;
      }
    }

    filled.forEach((i, value) -> results[i] = value.negate());
  }

  private static Map<Integer, BigDecimal> computeOverweightScores(
      FundTransactionInput input, List<Integer> active, List<BigDecimal> targetValues) {
    Map<Integer, BigDecimal> scores = new HashMap<>();
    for (int i : active) {
      BigDecimal overweight =
          input.positions().get(i).marketValue().subtract(targetValues.get(i)).max(ZERO);
      scores.put(i, overweight);
    }
    return scores;
  }

  private static Map<Integer, BigDecimal> computeHeadroomScores(
      FundTransactionInput input, Map<Integer, BigDecimal> filled, List<Integer> active) {
    Map<Integer, BigDecimal> scores = new HashMap<>();
    for (int i : active) {
      scores.put(i, headroom(input, filled, i));
    }
    return scores;
  }

  private static BigDecimal sumScoreValues(Map<Integer, BigDecimal> scores) {
    return scores.values().stream().reduce(ZERO, BigDecimal::add);
  }

  private static Map<Integer, BigDecimal> resolveScores(
      FundTransactionInput input,
      Map<Integer, BigDecimal> filled,
      List<Integer> active,
      List<BigDecimal> targetValues) {
    Map<Integer, BigDecimal> scores = computeOverweightScores(input, active, targetValues);
    if (sumScoreValues(scores).compareTo(MIN_MEANINGFUL_AMOUNT) < 0) {
      return computeHeadroomScores(input, filled, active);
    }
    return scores;
  }

  private static Map<Integer, BigDecimal> computeCappedAllocations(
      FundTransactionInput input,
      Map<Integer, BigDecimal> filled,
      List<Integer> active,
      Map<Integer, BigDecimal> scores,
      BigDecimal totalScore,
      BigDecimal remaining) {
    Map<Integer, BigDecimal> allocations = new HashMap<>();
    for (int i : active) {
      BigDecimal headroom = headroom(input, filled, i);
      BigDecimal score = Objects.requireNonNull(scores.get(i), "score missing for index=" + i);
      BigDecimal allocation = score.multiply(remaining).divide(totalScore, SCALE, HALF_UP);
      allocations.put(i, allocation.compareTo(headroom) >= 0 ? headroom : allocation);
    }
    return allocations;
  }

  private static BigDecimal headroom(
      FundTransactionInput input, Map<Integer, BigDecimal> filled, int index) {
    return input
        .positions()
        .get(index)
        .marketValue()
        .subtract(filled.getOrDefault(index, ZERO))
        .max(ZERO);
  }

  private static List<Integer> cappedIndices(
      FundTransactionInput input,
      Map<Integer, BigDecimal> filled,
      List<Integer> active,
      Map<Integer, BigDecimal> allocations) {
    List<Integer> capped = new ArrayList<>();
    for (int i : active) {
      BigDecimal allocation =
          Objects.requireNonNull(allocations.get(i), "allocation missing for index=" + i);
      if (allocation.compareTo(headroom(input, filled, i)) >= 0) {
        capped.add(i);
      }
    }
    return capped;
  }

  private static BigDecimal applyCappedAllocations(
      Map<Integer, BigDecimal> filled,
      List<Integer> active,
      List<Integer> cappedIndices,
      Map<Integer, BigDecimal> allocations,
      BigDecimal remaining) {
    for (int i : cappedIndices) {
      filled.merge(i, allocations.get(i), BigDecimal::add);
      remaining = remaining.subtract(allocations.get(i));
      active.remove(Integer.valueOf(i));
    }
    return remaining;
  }

  private static int minAllocationIndex(
      Map<Integer, BigDecimal> filled, List<Integer> active, Map<Integer, BigDecimal> allocations) {
    BigDecimal minAllocation = null;
    int minIndex = -1;
    for (int i : active) {
      BigDecimal total = filled.getOrDefault(i, ZERO).add(allocations.get(i));
      if (minAllocation == null || total.compareTo(minAllocation) < 0) {
        minAllocation = total;
        minIndex = i;
      }
    }
    return minIndex;
  }

  private static void applyAllAllocations(
      Map<Integer, BigDecimal> filled, List<Integer> active, Map<Integer, BigDecimal> allocations) {
    for (int i : active) {
      filled.merge(i, allocations.get(i), BigDecimal::add);
    }
  }

  private static boolean settleMinAllocation(
      Map<Integer, BigDecimal> filled,
      List<Integer> active,
      Map<Integer, BigDecimal> allocations,
      BigDecimal thresholdTolerance) {
    int minIndex = minAllocationIndex(filled, active, allocations);
    BigDecimal minAllocation =
        minIndex < 0 ? null : filled.getOrDefault(minIndex, ZERO).add(allocations.get(minIndex));

    if (minAllocation != null && minAllocation.compareTo(thresholdTolerance) >= 0) {
      applyAllAllocations(filled, active, allocations);
      return true;
    }
    active.remove(Integer.valueOf(minIndex));
    return false;
  }

  static List<BigDecimal> applySellSafetySpill(
      List<BigDecimal> sells,
      FundTransactionInput input,
      List<BigDecimal> scores,
      BigDecimal targetSellAmount) {
    List<BigDecimal> marketValues =
        input.positions().stream().map(PositionSnapshot::marketValue).toList();
    BigDecimal threshold = input.minTransactionThreshold();
    BigDecimal[] raisedSells = sells.toArray(new BigDecimal[0]);
    BigDecimal residual = targetSellAmount.subtract(sumArray(raisedSells));

    residual = spillOpenNewSells(raisedSells, marketValues, targetSellAmount, threshold, residual);
    residual = spillTopUpSellingPositions(raisedSells, marketValues, targetSellAmount, residual);
    spillLiquidateTrappedOddLots(raisedSells, marketValues, scores, threshold, residual);

    return List.of(raisedSells);
  }

  private static BigDecimal spillOpenNewSells(
      BigDecimal[] sells,
      List<BigDecimal> marketValues,
      BigDecimal targetSellAmount,
      BigDecimal threshold,
      BigDecimal residual) {
    if (residual.compareTo(threshold.subtract(MIN_MEANINGFUL_AMOUNT)) < 0) {
      return residual;
    }
    List<BigDecimal> sellHeadroom = remainingSellHeadroom(sells, marketValues);
    if (sellHeadroom.stream().noneMatch(v -> v.compareTo(MIN_MEANINGFUL_AMOUNT) > 0)) {
      return residual;
    }
    addInto(sells, distributeCapped(sellHeadroom, residual, threshold, sellHeadroom));
    return targetSellAmount.subtract(sumArray(sells));
  }

  private static BigDecimal spillTopUpSellingPositions(
      BigDecimal[] sells,
      List<BigDecimal> marketValues,
      BigDecimal targetSellAmount,
      BigDecimal residual) {
    if (residual.compareTo(MIN_MEANINGFUL_AMOUNT) <= 0) {
      return residual;
    }
    List<BigDecimal> topUpHeadroom =
        IntStream.range(0, sells.length)
            .mapToObj(
                i ->
                    sells[i].compareTo(MIN_MEANINGFUL_AMOUNT) > 0
                        ? marketValues.get(i).subtract(sells[i]).max(ZERO)
                        : ZERO)
            .toList();
    if (topUpHeadroom.stream().noneMatch(v -> v.compareTo(MIN_MEANINGFUL_AMOUNT) > 0)) {
      return residual;
    }
    addInto(sells, distributeCapped(topUpHeadroom, residual, ZERO, topUpHeadroom));
    return targetSellAmount.subtract(sumArray(sells));
  }

  private static void spillLiquidateTrappedOddLots(
      BigDecimal[] sells,
      List<BigDecimal> marketValues,
      List<BigDecimal> scores,
      BigDecimal threshold,
      BigDecimal residual) {
    if (residual.compareTo(MIN_MEANINGFUL_AMOUNT) <= 0) {
      return;
    }
    BigDecimal thresholdTolerance = threshold.subtract(MIN_MEANINGFUL_AMOUNT);
    List<Integer> oddLots = new ArrayList<>();
    for (int i = 0; i < sells.length; i++) {
      BigDecimal remaining = marketValues.get(i).subtract(sells[i]);
      if (remaining.compareTo(MIN_MEANINGFUL_AMOUNT) > 0
          && remaining.compareTo(thresholdTolerance) < 0) {
        oddLots.add(i);
      }
    }
    oddLots.sort(
        (indexA, indexB) -> {
          int byScore = scores.get(indexB).compareTo(scores.get(indexA));
          return byScore != 0
              ? byScore
              : marketValues
                  .get(indexB)
                  .subtract(sells[indexB])
                  .compareTo(marketValues.get(indexA).subtract(sells[indexA]));
        });
    BigDecimal remaining = residual;
    for (int i : oddLots) {
      if (remaining.compareTo(MIN_MEANINGFUL_AMOUNT) <= 0) {
        break;
      }
      BigDecimal oddLot = marketValues.get(i).subtract(sells[i]);
      sells[i] = sells[i].add(oddLot);
      remaining = remaining.subtract(oddLot);
    }
  }

  private static List<BigDecimal> remainingSellHeadroom(
      BigDecimal[] sells, List<BigDecimal> marketValues) {
    return IntStream.range(0, sells.length)
        .mapToObj(i -> marketValues.get(i).subtract(sells[i]).max(ZERO))
        .toList();
  }

  static void addInto(BigDecimal[] out, BigDecimal[] delta) {
    for (int i = 0; i < out.length; i++) {
      out[i] = out[i].add(delta[i]);
    }
  }

  static BigDecimal sumArray(BigDecimal[] values) {
    BigDecimal total = ZERO;
    for (BigDecimal value : values) {
      total = total.add(value);
    }
    return total;
  }
}
