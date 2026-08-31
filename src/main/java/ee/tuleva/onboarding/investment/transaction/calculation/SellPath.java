package ee.tuleva.onboarding.investment.transaction.calculation;

import static ee.tuleva.onboarding.investment.transaction.calculation.FastSellBucketing.distributeSellByOverweight;
import static ee.tuleva.onboarding.investment.transaction.calculation.FastSellBucketing.fastSellIndices;
import static ee.tuleva.onboarding.investment.transaction.calculation.FastSellBucketing.finalizeSells;
import static ee.tuleva.onboarding.investment.transaction.calculation.FastSellBucketing.sellFastBucket;
import static ee.tuleva.onboarding.investment.transaction.calculation.FastSellBucketing.slowSellIndices;
import static ee.tuleva.onboarding.investment.transaction.calculation.FastSellBucketing.totalMarketValue;
import static ee.tuleva.onboarding.investment.transaction.calculation.SellSafetySpill.addInto;
import static ee.tuleva.onboarding.investment.transaction.calculation.SellSafetySpill.applySellSafetySpill;
import static ee.tuleva.onboarding.investment.transaction.calculation.SellSafetySpill.sumArray;
import static ee.tuleva.onboarding.investment.transaction.calculation.TradeDistribution.MIN_MEANINGFUL_AMOUNT;
import static ee.tuleva.onboarding.investment.transaction.calculation.TradeDistribution.distributeCapped;
import static ee.tuleva.onboarding.investment.transaction.calculation.TradeDistribution.distributeSellWithCap;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.investment.transaction.FundTransactionInput;
import ee.tuleva.onboarding.investment.transaction.ModelWeight;
import ee.tuleva.onboarding.investment.transaction.PositionSnapshot;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.jspecify.annotations.NullMarked;

@NullMarked
final class SellPath {

  private SellPath() {}

  static List<BigDecimal> calculateSell(
      FundTransactionInput input, BigDecimal targetNet, Map<String, BigDecimal> weightMap) {
    int size = input.positions().size();
    if (size == 0) {
      return List.of();
    }

    if (input.freeCash().compareTo(new BigDecimal("-0.01")) >= 0) {
      return zeroList(size);
    }

    BigDecimal targetSellAmount = input.freeCash().abs();

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

  private static List<BigDecimal> distributeOverSoftReliefThenSpill(
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

  static List<BigDecimal> calculateSellFast(
      FundTransactionInput input, BigDecimal netInvestable, Map<String, BigDecimal> weightMap) {
    int size = input.positions().size();
    if (size == 0) {
      return List.of();
    }

    if (input.freeCash().compareTo(new BigDecimal("-0.01")) >= 0) {
      return zeroList(size);
    }

    BigDecimal targetAmount = input.freeCash().abs();
    List<BigDecimal> targetValues = normalizedTargetValues(input, netInvestable, weightMap);
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

  static List<BigDecimal> normalizedTargetValues(
      FundTransactionInput input, BigDecimal netInvestable, Map<String, BigDecimal> weightMap) {
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

  private static boolean isOverSoftLimit(PositionSnapshot position, FundTransactionInput input) {
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

  private static List<BigDecimal> zeroList(int size) {
    return IntStream.range(0, size).mapToObj(i -> ZERO).toList();
  }
}
