package ee.tuleva.onboarding.investment.transaction.calculation;

import static ee.tuleva.onboarding.investment.transaction.LimitStatus.OK;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.investment.transaction.FundCalculationResult;
import ee.tuleva.onboarding.investment.transaction.FundTransactionInput;
import ee.tuleva.onboarding.investment.transaction.LimitStatus;
import ee.tuleva.onboarding.investment.transaction.ModelWeight;
import ee.tuleva.onboarding.investment.transaction.PositionSnapshot;
import ee.tuleva.onboarding.investment.transaction.TradeCalculation;
import ee.tuleva.onboarding.investment.transaction.TransactionMode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

@Component
public class TradeCalculationEngine {

  private static final int SCALE = 2;
  private static final int MAX_ITERATIONS = 20;
  private static final BigDecimal MIN_MEANINGFUL_AMOUNT = new BigDecimal("0.01");

  public FundCalculationResult calculate(FundTransactionInput input, TransactionMode mode) {
    List<BigDecimal> rawTrades =
        switch (mode) {
          case BUY -> calculateBuy(input);
          case SELL -> calculateSell(input);
          case SELL_FAST -> calculateSellFast(input);
          case REBALANCE -> calculateRebalance(input);
        };

    List<TradeCalculation> trades = applyLimits(input, rawTrades);
    return new FundCalculationResult(input.fund(), mode, input, trades);
  }

  private List<BigDecimal> calculateBuy(FundTransactionInput input) {
    if (input.positions().isEmpty()) {
      return List.of();
    }

    BigDecimal netInvestable = netInvestable(input);
    Map<String, BigDecimal> weightMap = buildWeightMap(input.modelWeights());

    List<BigDecimal> scores =
        input.positions().stream()
            .map(
                position -> {
                  BigDecimal weight = weightMap.getOrDefault(position.isin(), ZERO);
                  return weight.multiply(netInvestable).subtract(position.marketValue()).max(ZERO);
                })
            .toList();

    boolean allZero = scores.stream().allMatch(s -> s.compareTo(ZERO) == 0);
    if (allZero && input.freeCash().compareTo(ZERO) > 0) {
      scores = fallbackBuyScores(input.positions(), weightMap, netInvestable);
    }

    List<BigDecimal> effectiveScores = excludeScoresWithoutHeadroom(input, scores);

    List<BigDecimal> allocations =
        distributeAmountWithThreshold(
            effectiveScores, input.freeCash(), input.minTransactionThreshold());

    return redistributeHardLimitExcess(input, effectiveScores, allocations);
  }

  private List<BigDecimal> redistributeHardLimitExcess(
      FundTransactionInput input, List<BigDecimal> scores, List<BigDecimal> allocations) {
    int size = allocations.size();
    BigDecimal threshold = input.minTransactionThreshold();
    BigDecimal[] capped = new BigDecimal[size];
    BigDecimal totalExcess = ZERO;

    for (int i = 0; i < size; i++) {
      BigDecimal headroom = hardLimitHeadroom(input, input.positions().get(i));
      if (headroom != null && allocations.get(i).compareTo(headroom) > 0) {
        totalExcess = totalExcess.add(allocations.get(i).subtract(headroom));
        capped[i] = headroom.setScale(SCALE, HALF_UP);
      } else {
        capped[i] = allocations.get(i);
      }
    }

    if (totalExcess.compareTo(MIN_MEANINGFUL_AMOUNT) <= 0) {
      return List.of(capped);
    }

    BigDecimal undistributed = waterFillExcessAcrossRunners(input, scores, capped, totalExcess);

    if (undistributed.compareTo(MIN_MEANINGFUL_AMOUNT) > 0
        && input.freeCash().compareTo(threshold) >= 0) {
      topUpBestRunnerToThreshold(
          capped, runnerScores(input, scores, capped), undistributed, threshold);
    }
    return List.of(capped);
  }

  private BigDecimal waterFillExcessAcrossRunners(
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

      boolean newlyCapped = false;
      for (int i = 0; i < size; i++) {
        if (round.get(i).compareTo(ZERO) <= 0) {
          continue;
        }
        BigDecimal headroom = remainingHeadroom(input, capped, i);
        if (headroom != null && round.get(i).compareTo(headroom) >= 0) {
          capped[i] = capped[i].add(headroom);
          remaining = remaining.subtract(headroom);
          newlyCapped = true;
        } else {
          capped[i] = capped[i].add(round.get(i));
          remaining = remaining.subtract(round.get(i));
        }
      }

      if (!newlyCapped) {
        break;
      }
    }

    return remaining;
  }

  private List<BigDecimal> runnerScores(
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

  private BigDecimal remainingHeadroom(FundTransactionInput input, BigDecimal[] capped, int index) {
    BigDecimal headroom = hardLimitHeadroom(input, input.positions().get(index));
    return headroom == null ? null : headroom.subtract(capped[index]).max(ZERO);
  }

  private void topUpBestRunnerToThreshold(
      BigDecimal[] capped, List<BigDecimal> runnerScores, BigDecimal excess, BigDecimal threshold) {
    int runnerIndex = -1;
    BigDecimal runnerScore = ZERO;
    for (int i = 0; i < runnerScores.size(); i++) {
      if (runnerScores.get(i).compareTo(runnerScore) > 0) {
        runnerScore = runnerScores.get(i);
        runnerIndex = i;
      }
    }
    if (runnerIndex < 0) {
      return;
    }

    BigDecimal totalForRunner = capped[runnerIndex].add(excess);
    if (totalForRunner.compareTo(threshold) >= 0) {
      capped[runnerIndex] = totalForRunner;
      return;
    }

    BigDecimal additionalNeeded = threshold.subtract(totalForRunner);
    int donorIndex = -1;
    BigDecimal donorAllocation = ZERO;
    for (int i = 0; i < capped.length; i++) {
      if (i != runnerIndex && capped[i].compareTo(donorAllocation) > 0) {
        donorAllocation = capped[i];
        donorIndex = i;
      }
    }
    boolean donorStaysAboveThreshold =
        donorIndex >= 0
            && donorAllocation.compareTo(additionalNeeded) >= 0
            && donorAllocation.subtract(additionalNeeded).compareTo(threshold) >= 0;
    if (donorStaysAboveThreshold) {
      capped[donorIndex] = donorAllocation.subtract(additionalNeeded);
      capped[runnerIndex] = threshold;
    }
  }

  private List<BigDecimal> excludeScoresWithoutHeadroom(
      FundTransactionInput input, List<BigDecimal> scores) {
    return IntStream.range(0, scores.size())
        .mapToObj(
            i -> {
              BigDecimal headroom = hardLimitHeadroom(input, input.positions().get(i));
              return headroom == null || headroom.compareTo(input.minTransactionThreshold()) >= 0
                  ? scores.get(i)
                  : ZERO;
            })
        .toList();
  }

  private BigDecimal hardLimitHeadroom(FundTransactionInput input, PositionSnapshot position) {
    var limits = input.positionLimits().get(position.isin());
    if (limits == null) {
      return null;
    }
    BigDecimal maxAllowedMarketValue =
        input.grossPortfolioValue().multiply(limits.hardLimit().subtract(new BigDecimal("0.0001")));
    return maxAllowedMarketValue.subtract(position.marketValue()).max(ZERO);
  }

  private List<BigDecimal> calculateSell(FundTransactionInput input) {
    int size = input.positions().size();
    if (size == 0) {
      return List.of();
    }

    if (input.freeCash().compareTo(new BigDecimal("-0.01")) >= 0) {
      return zeroList(size);
    }

    BigDecimal targetSellAmount = input.freeCash().abs();
    BigDecimal targetNet = netInvestable(input);
    Map<String, BigDecimal> weightMap = buildWeightMap(input.modelWeights());

    List<BigDecimal> standardScores =
        input.positions().stream()
            .map(
                position -> {
                  BigDecimal weight = weightMap.getOrDefault(position.isin(), ZERO);
                  BigDecimal idealOverweight =
                      position.marketValue().subtract(weight.multiply(targetNet));
                  return position.marketValue().min(idealOverweight.max(ZERO));
                })
            .toList();

    List<BigDecimal> filteredScores =
        IntStream.range(0, input.positions().size())
            .mapToObj(
                i -> {
                  var position = input.positions().get(i);
                  BigDecimal score = standardScores.get(i);
                  return isOverSoftLimit(position, input) && score.compareTo(ZERO) > 0
                      ? score
                      : ZERO;
                })
            .toList();

    boolean anyOverSoftLimit = filteredScores.stream().anyMatch(score -> score.compareTo(ZERO) > 0);
    List<BigDecimal> finalScores = anyOverSoftLimit ? filteredScores : standardScores;

    List<BigDecimal> distributed = distributeSellWithCap(input, finalScores, targetSellAmount);

    return distributed.stream()
        .map(value -> value.compareTo(ZERO) == 0 ? ZERO : value.negate())
        .toList();
  }

  private List<BigDecimal> distributeSellWithCap(
      FundTransactionInput input, List<BigDecimal> initialScores, BigDecimal targetSellAmount) {
    int size = input.positions().size();
    BigDecimal threshold = input.minTransactionThreshold();

    BigDecimal totalSellableMarketValue =
        input.positions().stream().map(PositionSnapshot::marketValue).reduce(ZERO, BigDecimal::add);
    if (targetSellAmount.subtract(totalSellableMarketValue).compareTo(MIN_MEANINGFUL_AMOUNT) > 0) {
      throw new IllegalStateException(
          "Insufficient liquidity to satisfy sell: fund="
              + input.fund()
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
      BigDecimal roundThreshold = threshold;
      if (roundScores.stream().allMatch(s -> s.compareTo(ZERO) == 0)) {
        roundScores = remainingMarketValueScores(input, allocations, capped);
        roundThreshold = ZERO;
        if (roundScores.stream().allMatch(s -> s.compareTo(ZERO) == 0)) {
          break;
        }
      }

      List<BigDecimal> round =
          distributeAmountWithThreshold(roundScores, remainingNeed, roundThreshold);

      boolean newlyCapped = false;
      for (int i = 0; i < size; i++) {
        if (capped[i]) {
          continue;
        }
        BigDecimal proposed = allocations[i].add(round.get(i));
        BigDecimal marketValue = input.positions().get(i).marketValue();
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

  private List<BigDecimal> remainingMarketValueScores(
      FundTransactionInput input, BigDecimal[] allocations, boolean[] capped) {
    return IntStream.range(0, input.positions().size())
        .mapToObj(
            i ->
                capped[i]
                    ? ZERO
                    : input.positions().get(i).marketValue().subtract(allocations[i]).max(ZERO))
        .toList();
  }

  private List<BigDecimal> calculateSellFast(FundTransactionInput input) {
    int size = input.positions().size();
    if (size == 0) {
      return List.of();
    }

    if (input.freeCash().compareTo(new BigDecimal("-0.01")) >= 0) {
      return zeroList(size);
    }

    BigDecimal targetAmount = input.freeCash().abs();
    Set<String> fastIsins = input.fastSellIsins();
    BigDecimal[] results = new BigDecimal[size];
    List<BigDecimal> targetValues = normalizedTargetValues(input);

    BigDecimal totalFastValue = ZERO;
    List<Integer> fastIndices = new ArrayList<>();
    List<Integer> slowIndices = new ArrayList<>();

    for (int i = 0; i < size; i++) {
      results[i] = ZERO;
      var position = input.positions().get(i);
      if (fastIsins.contains(position.isin())) {
        fastIndices.add(i);
        totalFastValue = totalFastValue.add(position.marketValue());
      } else {
        slowIndices.add(i);
      }
    }

    BigDecimal amountFromFast = ZERO;
    if (!fastIndices.isEmpty()) {
      if (targetAmount.compareTo(totalFastValue) >= 0) {
        amountFromFast = totalFastValue;
        for (int i : fastIndices) {
          results[i] = input.positions().get(i).marketValue().negate();
        }
      } else {
        amountFromFast = targetAmount;
        distributeSellByOverweight(input, fastIndices, targetValues, amountFromFast, results);
      }
    }

    BigDecimal remainingNeed = targetAmount.subtract(amountFromFast);
    if (remainingNeed.compareTo(MIN_MEANINGFUL_AMOUNT) > 0 && !slowIndices.isEmpty()) {
      distributeSellByOverweight(input, slowIndices, targetValues, remainingNeed, results);
    }

    return List.of(results);
  }

  private void distributeSellByOverweight(
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

      Map<Integer, BigDecimal> scores = new HashMap<>();
      BigDecimal totalScore = ZERO;
      for (int i : active) {
        BigDecimal overweight =
            input.positions().get(i).marketValue().subtract(targetValues.get(i)).max(ZERO);
        scores.put(i, overweight);
        totalScore = totalScore.add(overweight);
      }

      if (totalScore.compareTo(MIN_MEANINGFUL_AMOUNT) < 0) {
        totalScore = ZERO;
        for (int i : active) {
          BigDecimal headroom = headroom(input, filled, i);
          scores.put(i, headroom);
          totalScore = totalScore.add(headroom);
        }
      }
      if (totalScore.compareTo(ZERO) == 0) {
        break;
      }

      Map<Integer, BigDecimal> allocations = new HashMap<>();
      List<Integer> cappedIndices = new ArrayList<>();
      for (int i : active) {
        BigDecimal headroom = headroom(input, filled, i);
        BigDecimal allocation =
            scores.get(i).multiply(remaining).divide(totalScore, SCALE, HALF_UP);
        if (allocation.compareTo(headroom) >= 0) {
          allocation = headroom;
          cappedIndices.add(i);
        }
        allocations.put(i, allocation);
      }

      if (!cappedIndices.isEmpty()) {
        for (int i : cappedIndices) {
          filled.merge(i, allocations.get(i), BigDecimal::add);
          remaining = remaining.subtract(allocations.get(i));
          active.remove(Integer.valueOf(i));
        }
        continue;
      }

      BigDecimal minAllocation = null;
      int minIndex = -1;
      for (int i : active) {
        BigDecimal total = filled.getOrDefault(i, ZERO).add(allocations.get(i));
        if (minAllocation == null || total.compareTo(minAllocation) < 0) {
          minAllocation = total;
          minIndex = i;
        }
      }

      if (minAllocation != null && minAllocation.compareTo(thresholdTolerance) >= 0) {
        for (int i : active) {
          filled.merge(i, allocations.get(i), BigDecimal::add);
        }
        break;
      }
      active.remove(Integer.valueOf(minIndex));
    }

    filled.forEach((i, value) -> results[i] = value.negate());
  }

  private BigDecimal headroom(
      FundTransactionInput input, Map<Integer, BigDecimal> filled, int index) {
    return input
        .positions()
        .get(index)
        .marketValue()
        .subtract(filled.getOrDefault(index, ZERO))
        .max(ZERO);
  }

  private List<BigDecimal> normalizedTargetValues(FundTransactionInput input) {
    BigDecimal netInvestable = netInvestable(input);
    Map<String, BigDecimal> weightMap = buildWeightMap(input.modelWeights());

    BigDecimal totalModelWeight =
        input.modelWeights().stream().map(ModelWeight::weight).reduce(ZERO, BigDecimal::add);
    BigDecimal normalizer =
        totalModelWeight.compareTo(ZERO) == 0 ? BigDecimal.ONE : totalModelWeight;

    return input.positions().stream()
        .map(
            position -> {
              BigDecimal rawWeight = weightMap.getOrDefault(position.isin(), ZERO);
              return rawWeight.divide(normalizer, 10, HALF_UP).multiply(netInvestable);
            })
        .toList();
  }

  private List<BigDecimal> calculateRebalance(FundTransactionInput input) {
    if (input.positions().isEmpty()) {
      return List.of();
    }

    List<BigDecimal> targetValues = normalizedTargetValues(input);
    List<BigDecimal> rawTrades =
        IntStream.range(0, input.positions().size())
            .mapToObj(
                i ->
                    targetValues
                        .get(i)
                        .subtract(input.positions().get(i).marketValue())
                        .setScale(SCALE, HALF_UP))
            .toList();

    List<BigDecimal> buyScores = rawTrades.stream().map(trade -> trade.max(ZERO)).toList();
    List<BigDecimal> sellScores =
        rawTrades.stream().map(trade -> trade.negate().max(ZERO)).toList();

    List<BigDecimal> buyAllocations =
        distributeBucketWithThreshold(buyScores, input.minTransactionThreshold());
    List<BigDecimal> sellAllocations =
        distributeBucketWithThreshold(sellScores, input.minTransactionThreshold());

    return IntStream.range(0, rawTrades.size())
        .mapToObj(i -> buyAllocations.get(i).subtract(sellAllocations.get(i)))
        .toList();
  }

  private List<BigDecimal> distributeBucketWithThreshold(
      List<BigDecimal> scores, BigDecimal threshold) {
    BigDecimal total = scores.stream().reduce(ZERO, BigDecimal::add);
    if (total.compareTo(MIN_MEANINGFUL_AMOUNT) <= 0) {
      return scores.stream().map(score -> ZERO).toList();
    }
    return distributeAmountWithThreshold(scores, total, threshold);
  }

  private List<TradeCalculation> applyLimits(
      FundTransactionInput input, List<BigDecimal> rawTrades) {
    return IntStream.range(0, rawTrades.size())
        .mapToObj(i -> applyLimitToTrade(input, input.positions().get(i), rawTrades.get(i)))
        .toList();
  }

  private TradeCalculation applyLimitToTrade(
      FundTransactionInput input, PositionSnapshot position, BigDecimal tradeAmount) {
    BigDecimal projectedMarketValue = position.marketValue().add(tradeAmount);
    BigDecimal projectedWeight =
        input.grossPortfolioValue().compareTo(ZERO) > 0
            ? projectedMarketValue.divide(input.grossPortfolioValue(), 6, HALF_UP)
            : ZERO;

    LimitStatus limitStatus = OK;
    var limits = input.positionLimits().get(position.isin());

    if (limits != null) {
      if (projectedWeight.compareTo(limits.hardLimit()) > 0) {
        if (tradeAmount.compareTo(ZERO) > 0) {
          BigDecimal maxAllowedMarketValue =
              input
                  .grossPortfolioValue()
                  .multiply(limits.hardLimit().subtract(new BigDecimal("0.0001")));
          BigDecimal maxTrade = maxAllowedMarketValue.subtract(position.marketValue());
          tradeAmount = maxTrade.max(ZERO).setScale(SCALE, HALF_UP);

          projectedMarketValue = position.marketValue().add(tradeAmount);
          projectedWeight = projectedMarketValue.divide(input.grossPortfolioValue(), 6, HALF_UP);
        }
        limitStatus = LimitStatus.HARD_LIMIT_EXCEEDED;
      } else if (projectedWeight.compareTo(limits.softLimit()) > 0) {
        limitStatus = LimitStatus.SOFT_LIMIT_EXCEEDED;
      }
    }

    return new TradeCalculation(position.isin(), tradeAmount, projectedWeight, limitStatus);
  }

  List<BigDecimal> distributeAmountWithThreshold(
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

  private List<BigDecimal> fallbackBuyScores(
      List<PositionSnapshot> positions,
      Map<String, BigDecimal> weightMap,
      BigDecimal netInvestable) {
    List<BigDecimal> surpluses =
        positions.stream()
            .map(
                position -> {
                  BigDecimal weight = weightMap.getOrDefault(position.isin(), ZERO);
                  BigDecimal target = weight.multiply(netInvestable);
                  return position.marketValue().subtract(target);
                })
            .toList();

    BigDecimal maxSurplus = surpluses.stream().reduce(surpluses.getFirst(), BigDecimal::max);

    return surpluses.stream()
        .map(surplus -> maxSurplus.subtract(surplus).add(BigDecimal.ONE).max(ZERO))
        .toList();
  }

  private boolean isOverSoftLimit(PositionSnapshot position, FundTransactionInput input) {
    var limits = input.positionLimits().get(position.isin());
    if (limits == null) {
      return true;
    }
    BigDecimal currentWeight =
        input.grossPortfolioValue().compareTo(ZERO) > 0
            ? position.marketValue().divide(input.grossPortfolioValue(), 6, HALF_UP)
            : ZERO;
    return currentWeight.compareTo(limits.softLimit()) > 0;
  }

  private BigDecimal netInvestable(FundTransactionInput input) {
    return input
        .grossPortfolioValue()
        .subtract(input.cashBuffer())
        .subtract(input.liabilities())
        .add(input.receivables());
  }

  private Map<String, BigDecimal> buildWeightMap(List<ModelWeight> modelWeights) {
    return modelWeights.stream()
        .collect(toMap(ModelWeight::isin, ModelWeight::weight, (a, b) -> b));
  }

  private List<BigDecimal> zeroList(int size) {
    return IntStream.range(0, size).mapToObj(i -> ZERO).toList();
  }
}
