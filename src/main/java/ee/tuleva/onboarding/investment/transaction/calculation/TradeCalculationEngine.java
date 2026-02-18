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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

@Component
public class TradeCalculationEngine {

  private static final int SCALE = 2;
  private static final int MAX_ITERATIONS = 20;

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

    return distributeAmountWithThreshold(scores, input.freeCash(), input.minTransactionThreshold());
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
    BigDecimal reducedNet = netInvestable(input).subtract(targetSellAmount);
    Map<String, BigDecimal> weightMap = buildWeightMap(input.modelWeights());

    List<BigDecimal> standardScores =
        input.positions().stream()
            .map(
                position -> {
                  BigDecimal weight = weightMap.getOrDefault(position.isin(), ZERO);
                  BigDecimal idealOverweight =
                      position.marketValue().subtract(weight.multiply(reducedNet));
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
    List<BigDecimal> distributed =
        distributeAmountWithThreshold(
            finalScores, targetSellAmount, input.minTransactionThreshold());

    return distributed.stream()
        .map(value -> value.compareTo(ZERO) == 0 ? ZERO : value.negate())
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
        for (int i : fastIndices) {
          BigDecimal share =
              input.positions().get(i).marketValue().divide(totalFastValue, 10, HALF_UP);
          results[i] = amountFromFast.multiply(share).setScale(SCALE, HALF_UP).negate();
        }
      }
    }

    BigDecimal remainingNeed = targetAmount.subtract(amountFromFast);
    if (remainingNeed.compareTo(new BigDecimal("0.01")) > 0 && !slowIndices.isEmpty()) {
      BigDecimal totalSlowValue = ZERO;
      for (int i : slowIndices) {
        totalSlowValue = totalSlowValue.add(input.positions().get(i).marketValue());
      }
      for (int i : slowIndices) {
        BigDecimal share =
            input.positions().get(i).marketValue().divide(totalSlowValue, 10, HALF_UP);
        BigDecimal sellAmount =
            input
                .positions()
                .get(i)
                .marketValue()
                .min(remainingNeed.multiply(share).setScale(SCALE, HALF_UP));
        results[i] = sellAmount.negate();
      }
    }

    return List.of(results);
  }

  private List<BigDecimal> calculateRebalance(FundTransactionInput input) {
    if (input.positions().isEmpty()) {
      return List.of();
    }

    BigDecimal netInvestable = netInvestable(input);
    Map<String, BigDecimal> weightMap = buildWeightMap(input.modelWeights());

    BigDecimal totalModelWeight =
        input.modelWeights().stream().map(ModelWeight::weight).reduce(ZERO, BigDecimal::add);
    if (totalModelWeight.compareTo(ZERO) == 0) {
      totalModelWeight = BigDecimal.ONE;
    }

    BigDecimal finalTotalModelWeight = totalModelWeight;
    return input.positions().stream()
        .map(
            position -> {
              BigDecimal rawWeight = weightMap.getOrDefault(position.isin(), ZERO);
              BigDecimal normalizedWeight = rawWeight.divide(finalTotalModelWeight, 10, HALF_UP);
              BigDecimal targetValue = normalizedWeight.multiply(netInvestable);
              return targetValue.subtract(position.marketValue()).setScale(SCALE, HALF_UP);
            })
        .toList();
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

      BigDecimal thresholdTolerance = threshold.subtract(new BigDecimal("0.01"));
      if (minAllocation != null && minAllocation.compareTo(thresholdTolerance) >= 0) {
        allocations = tempAllocations;
        break;
      }

      boolean changed = false;
      for (int i = 0; i < size; i++) {
        if (mask[i] && tempAllocations[i].compareTo(thresholdTolerance) < 0) {
          mask[i] = false;
          changed = true;
        }
      }

      if (!changed) {
        break;
      }
    }

    return List.of(allocations);
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
        .subtract(input.liabilities().abs());
  }

  private Map<String, BigDecimal> buildWeightMap(List<ModelWeight> modelWeights) {
    return modelWeights.stream()
        .collect(toMap(ModelWeight::isin, ModelWeight::weight, (a, b) -> b));
  }

  private List<BigDecimal> zeroList(int size) {
    return IntStream.range(0, size).mapToObj(i -> ZERO).toList();
  }
}
