package ee.tuleva.onboarding.investment.transaction.calculation;

import static ee.tuleva.onboarding.investment.transaction.CalculationWarningType.REBALANCE_NET_CASH_MISMATCH;
import static ee.tuleva.onboarding.investment.transaction.CalculationWarningType.REBALANCE_NET_NOT_ACHIEVED;
import static ee.tuleva.onboarding.investment.transaction.LimitStatus.OK;
import static ee.tuleva.onboarding.investment.transaction.calculation.FastSellBucketing.distributeSellByOverweight;
import static ee.tuleva.onboarding.investment.transaction.calculation.FastSellBucketing.fastSellIndices;
import static ee.tuleva.onboarding.investment.transaction.calculation.FastSellBucketing.finalizeSells;
import static ee.tuleva.onboarding.investment.transaction.calculation.FastSellBucketing.sellFastBucket;
import static ee.tuleva.onboarding.investment.transaction.calculation.FastSellBucketing.slowSellIndices;
import static ee.tuleva.onboarding.investment.transaction.calculation.FastSellBucketing.totalMarketValue;
import static ee.tuleva.onboarding.investment.transaction.calculation.HeadroomRedistribution.hardLimitHeadroom;
import static ee.tuleva.onboarding.investment.transaction.calculation.HeadroomRedistribution.runnerScores;
import static ee.tuleva.onboarding.investment.transaction.calculation.HeadroomRedistribution.waterFillExcessAcrossRunners;
import static ee.tuleva.onboarding.investment.transaction.calculation.SellSafetySpill.addInto;
import static ee.tuleva.onboarding.investment.transaction.calculation.SellSafetySpill.applySellSafetySpill;
import static ee.tuleva.onboarding.investment.transaction.calculation.SellSafetySpill.sumArray;
import static ee.tuleva.onboarding.investment.transaction.calculation.TradeDistribution.MIN_MEANINGFUL_AMOUNT;
import static ee.tuleva.onboarding.investment.transaction.calculation.TradeDistribution.SCALE;
import static ee.tuleva.onboarding.investment.transaction.calculation.TradeDistribution.distributeAmountWithThreshold;
import static ee.tuleva.onboarding.investment.transaction.calculation.TradeDistribution.distributeCapped;
import static ee.tuleva.onboarding.investment.transaction.calculation.TradeDistribution.distributeSellWithCap;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.investment.transaction.CalculationWarning;
import ee.tuleva.onboarding.investment.transaction.FundCalculationResult;
import ee.tuleva.onboarding.investment.transaction.FundTransactionInput;
import ee.tuleva.onboarding.investment.transaction.LimitStatus;
import ee.tuleva.onboarding.investment.transaction.ModelWeight;
import ee.tuleva.onboarding.investment.transaction.PositionSnapshot;
import ee.tuleva.onboarding.investment.transaction.TradeCalculation;
import ee.tuleva.onboarding.investment.transaction.TransactionMode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TradeCalculationEngine {

  public FundCalculationResult calculate(FundTransactionInput input, TransactionMode mode) {
    validateInput(input);

    List<BigDecimal> rawTrades =
        switch (mode) {
          case BUY -> calculateBuy(input);
          case SELL -> calculateSell(input);
          case SELL_FAST -> calculateSellFast(input);
          case REBALANCE -> calculateRebalance(input);
        };

    List<TradeCalculation> trades = applyLimits(input, rawTrades);
    return new FundCalculationResult(
        input.fund(),
        mode,
        input,
        trades,
        netInvestable(input),
        noTradeReason(input, mode, trades),
        warnings(input, mode, trades));
  }

  private void validateInput(FundTransactionInput input) {
    input
        .positions()
        .forEach(
            position -> {
              if (position.marketValue().signum() < 0) {
                throw new IllegalArgumentException(
                    "Position market value cannot be negative: fund=%s, isin=%s, marketValue=%s"
                        .formatted(
                            input.fund().name(),
                            position.isin(),
                            position.marketValue().toPlainString()));
              }
            });
    input
        .modelWeights()
        .forEach(
            weight -> {
              if (weight.weight().signum() < 0) {
                throw new IllegalArgumentException(
                    "Model weight cannot be negative: fund=%s, isin=%s, weight=%s"
                        .formatted(
                            input.fund().name(), weight.isin(), weight.weight().toPlainString()));
              }
            });
    if (input.grossPortfolioValue().signum() < 0) {
      throw new IllegalArgumentException(
          "Gross portfolio value cannot be negative: fund=%s, grossPortfolioValue=%s"
              .formatted(input.fund().name(), input.grossPortfolioValue().toPlainString()));
    }
  }

  private List<CalculationWarning> warnings(
      FundTransactionInput input, TransactionMode mode, List<TradeCalculation> trades) {
    if (mode != TransactionMode.REBALANCE || input.positions().isEmpty()) {
      return List.of();
    }
    return rebalanceWarnings(input, trades);
  }

  private List<CalculationWarning> rebalanceWarnings(
      FundTransactionInput input, List<TradeCalculation> trades) {
    RebalanceBuckets buckets = rebalanceBuckets(input);
    BigDecimal intendedNet = sum(buckets.buyScores()).subtract(sum(buckets.sellScores()));
    BigDecimal realizedNet =
        trades.stream().map(TradeCalculation::tradeAmount).reduce(ZERO, BigDecimal::add);
    BigDecimal threshold = input.minTransactionThreshold();

    List<CalculationWarning> warnings = new ArrayList<>();
    BigDecimal cashGap = intendedNet.subtract(input.freeCash());
    if (cashGap.abs().compareTo(threshold) >= 0) {
      warnings.add(
          new CalculationWarning(
              REBALANCE_NET_CASH_MISMATCH,
              ("Rebalance net cash need does not match free cash, check the inputs: fund=%s,"
                      + " intendedNet=%s, freeCash=%s, difference=%s, minTransactionThreshold=%s")
                  .formatted(
                      input.fund().name(),
                      intendedNet.toPlainString(),
                      input.freeCash().toPlainString(),
                      cashGap.toPlainString(),
                      threshold.toPlainString())));
    }

    BigDecimal achievedGap = realizedNet.subtract(intendedNet);
    if (achievedGap.abs().compareTo(threshold) >= 0) {
      warnings.add(
          new CalculationWarning(
              REBALANCE_NET_NOT_ACHIEVED,
              ("Rebalance could not achieve the intended net, a limit or the minimum trade size"
                      + " blocked part of it and a Sell may be needed instead: fund=%s,"
                      + " realizedNet=%s, intendedNet=%s, difference=%s,"
                      + " minTransactionThreshold=%s")
                  .formatted(
                      input.fund().name(),
                      realizedNet.toPlainString(),
                      intendedNet.toPlainString(),
                      achievedGap.toPlainString(),
                      threshold.toPlainString())));
    }

    warnings.forEach(warning -> log.warn(warning.message()));
    return List.copyOf(warnings);
  }

  private static BigDecimal sum(List<BigDecimal> values) {
    return values.stream().reduce(ZERO, BigDecimal::add);
  }

  @Nullable
  private String noTradeReason(
      FundTransactionInput input, TransactionMode mode, List<TradeCalculation> trades) {
    boolean hasTrades = trades.stream().anyMatch(trade -> trade.tradeAmount().compareTo(ZERO) != 0);
    if (hasTrades) {
      return null;
    }
    return switch (mode) {
      case BUY -> buyNoTradeReason(input);
      case SELL, SELL_FAST -> sellNoTradeReason(input, mode);
      case REBALANCE -> "No trades: mode=REBALANCE, reason=noDriftBeyondThreshold";
    };
  }

  private String buyNoTradeReason(FundTransactionInput input) {
    if (input.positions().isEmpty()) {
      return "No trades: mode=BUY, reason=noPositions";
    }
    if (input.freeCash().compareTo(input.minTransactionThreshold()) < 0) {
      return "No trades: mode=BUY, reason=freeCashBelowMinTransactionThreshold, freeCash=%s,"
              .formatted(input.freeCash().toPlainString())
          + " minTransactionThreshold=%s"
              .formatted(input.minTransactionThreshold().toPlainString());
    }
    return "No trades: mode=BUY, reason=noPositionUnderTargetBeyondThreshold";
  }

  private String sellNoTradeReason(FundTransactionInput input, TransactionMode mode) {
    if (input.freeCash().compareTo(new BigDecimal("-0.01")) >= 0) {
      return "No trades: mode=%s, reason=noCashShortfall, freeCash=%s"
          .formatted(mode.name(), input.freeCash().toPlainString());
    }
    return "No trades: mode=%s, reason=shortfallBelowMinTransactionThreshold, freeCash=%s,"
            .formatted(mode.name(), input.freeCash().toPlainString())
        + " minTransactionThreshold=%s".formatted(input.minTransactionThreshold().toPlainString());
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

    List<BigDecimal> distributed =
        anyOverSoftLimit
            ? distributeOverSoftReliefThenSpill(
                input, filteredScores, standardScores, targetSellAmount)
            : distributeSellWithCap(
                input.fund(),
                input.positions().stream().map(PositionSnapshot::marketValue).toList(),
                standardScores,
                targetSellAmount,
                input.minTransactionThreshold());
    List<BigDecimal> raised =
        applySellSafetySpill(distributed, input, standardScores, targetSellAmount);

    return raised.stream()
        .map(value -> value.compareTo(ZERO) == 0 ? ZERO : value.negate())
        .toList();
  }

  private List<BigDecimal> distributeOverSoftReliefThenSpill(
      FundTransactionInput input,
      List<BigDecimal> filteredScores,
      List<BigDecimal> standardScores,
      BigDecimal targetSellAmount) {
    BigDecimal threshold = input.minTransactionThreshold();
    BigDecimal[] relief =
        distributeCapped(filteredScores, targetSellAmount, threshold, standardScores);
    BigDecimal remaining = targetSellAmount.subtract(sumArray(relief));

    if (remaining.compareTo(threshold.subtract(MIN_MEANINGFUL_AMOUNT)) < 0) {
      return List.of(relief);
    }

    List<BigDecimal> spillScores =
        IntStream.range(0, relief.length)
            .mapToObj(i -> relief[i].compareTo(ZERO) > 0 ? ZERO : standardScores.get(i))
            .toList();
    List<BigDecimal> spillCaps =
        IntStream.range(0, relief.length)
            .mapToObj(
                i -> relief[i].compareTo(ZERO) > 0 ? ZERO : input.positions().get(i).marketValue())
            .toList();
    addInto(relief, distributeCapped(spillScores, remaining, threshold, spillCaps));
    return List.of(relief);
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
    List<BigDecimal> targetValues = normalizedTargetValues(input);
    BigDecimal[] results = new BigDecimal[size];
    Arrays.fill(results, ZERO);

    List<Integer> fastIndices = fastSellIndices(input);
    List<Integer> slowIndices = slowSellIndices(input, fastIndices);
    BigDecimal totalFastValue = totalMarketValue(input, fastIndices);

    sellFastBucket(input, fastIndices, targetValues, targetAmount, totalFastValue, results);

    BigDecimal amountFromFast =
        fastIndices.stream().map(i -> results[i].negate()).reduce(ZERO, BigDecimal::add);
    BigDecimal remainingNeed = targetAmount.subtract(amountFromFast);
    if (remainingNeed.compareTo(MIN_MEANINGFUL_AMOUNT) > 0 && !slowIndices.isEmpty()) {
      distributeSellByOverweight(input, slowIndices, targetValues, remainingNeed, results);
    }

    return finalizeSells(input, results, targetValues, targetAmount);
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

    RebalanceBuckets buckets = rebalanceBuckets(input);
    List<BigDecimal> buyScores = buckets.buyScores();
    List<BigDecimal> sellScores = buckets.sellScores();

    List<BigDecimal> buyAllocations =
        redistributeHardLimitExcess(
            input,
            buyScores,
            distributeBucketWithThreshold(buyScores, input.minTransactionThreshold()));
    List<BigDecimal> rawSellAllocations =
        distributeBucketWithThreshold(sellScores, input.minTransactionThreshold());
    List<BigDecimal> sellAllocations =
        IntStream.range(0, rawSellAllocations.size())
            .mapToObj(i -> rawSellAllocations.get(i).min(input.positions().get(i).marketValue()))
            .toList();

    return IntStream.range(0, buyAllocations.size())
        .mapToObj(i -> buyAllocations.get(i).subtract(sellAllocations.get(i)))
        .toList();
  }

  private RebalanceBuckets rebalanceBuckets(FundTransactionInput input) {
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
        IntStream.range(0, rawTrades.size())
            .mapToObj(
                i ->
                    rawTrades.get(i).negate().max(ZERO).min(input.positions().get(i).marketValue()))
            .toList();

    return new RebalanceBuckets(buyScores, sellScores);
  }

  private record RebalanceBuckets(List<BigDecimal> buyScores, List<BigDecimal> sellScores) {}

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
