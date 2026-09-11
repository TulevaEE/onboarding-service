package ee.tuleva.onboarding.investment.transaction.calculation;

import static ee.tuleva.onboarding.investment.transaction.calculation.BuyAllocation.redistributeHardLimitExcess;
import static ee.tuleva.onboarding.investment.transaction.calculation.SellPath.normalizedTargetValues;
import static ee.tuleva.onboarding.investment.transaction.calculation.TradeDistribution.MIN_MEANINGFUL_AMOUNT;
import static ee.tuleva.onboarding.investment.transaction.calculation.TradeDistribution.SCALE;
import static ee.tuleva.onboarding.investment.transaction.calculation.TradeDistribution.distributeAmountWithThreshold;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.investment.transaction.FundTransactionInput;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.jspecify.annotations.NullMarked;

@NullMarked
final class RebalancePath {

  private RebalancePath() {}

  static List<BigDecimal> calculateRebalance(
      FundTransactionInput input, BigDecimal netInvestable, Map<String, BigDecimal> weightMap) {
    if (input.positions().isEmpty()) {
      return List.of();
    }

    RebalanceBuckets buckets = rebalanceBuckets(input, netInvestable, weightMap);
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

  static RebalanceBuckets rebalanceBuckets(
      FundTransactionInput input, BigDecimal netInvestable, Map<String, BigDecimal> weightMap) {
    List<BigDecimal> targetValues = normalizedTargetValues(input, netInvestable, weightMap);
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

  record RebalanceBuckets(List<BigDecimal> buyScores, List<BigDecimal> sellScores) {}

  private static List<BigDecimal> distributeBucketWithThreshold(
      List<BigDecimal> scores, BigDecimal threshold) {
    BigDecimal total = scores.stream().reduce(ZERO, BigDecimal::add);
    if (total.compareTo(MIN_MEANINGFUL_AMOUNT) <= 0) {
      return scores.stream().map(score -> ZERO).toList();
    }
    return distributeAmountWithThreshold(scores, total, threshold);
  }
}
