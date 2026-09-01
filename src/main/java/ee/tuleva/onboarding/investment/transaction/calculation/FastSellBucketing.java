package ee.tuleva.onboarding.investment.transaction.calculation;

import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.investment.transaction.FundTransactionInput;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;
import org.jspecify.annotations.NullMarked;

@NullMarked
final class FastSellBucketing {

  private FastSellBucketing() {}

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
    List<BigDecimal> raised =
        SellSafetySpill.applySellSafetySpill(sells, input, overweightScores, targetAmount);
    return raised.stream().map(BigDecimal::negate).toList();
  }

  static void distributeSellByOverweight(
      FundTransactionInput input,
      List<Integer> bucketIndices,
      List<BigDecimal> targetValues,
      BigDecimal amount,
      BigDecimal[] results) {
    BigDecimal threshold = input.minTransactionThreshold();
    BigDecimal thresholdTolerance = threshold.subtract(TradeDistribution.MIN_MEANINGFUL_AMOUNT);
    List<Integer> active = new ArrayList<>(bucketIndices);
    Map<Integer, BigDecimal> filled = new HashMap<>();
    BigDecimal remaining = amount;

    for (int iteration = 0;
        iteration < TradeDistribution.MAX_ITERATIONS && !active.isEmpty();
        iteration++) {
      if (remaining.compareTo(TradeDistribution.MIN_MEANINGFUL_AMOUNT) <= 0) {
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
    if (sumScoreValues(scores).compareTo(TradeDistribution.MIN_MEANINGFUL_AMOUNT) < 0) {
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
      BigDecimal allocation =
          score.multiply(remaining).divide(totalScore, TradeDistribution.SCALE, HALF_UP);
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
}
