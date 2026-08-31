package ee.tuleva.onboarding.investment.transaction.calculation;

import ee.tuleva.onboarding.investment.transaction.FundTransactionInput;
import ee.tuleva.onboarding.investment.transaction.PositionSnapshot;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.jspecify.annotations.NullMarked;

@NullMarked
final class SellSafetySpill {

  private SellSafetySpill() {}

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
    if (residual.compareTo(threshold.subtract(TradeDistribution.MIN_MEANINGFUL_AMOUNT)) < 0) {
      return residual;
    }
    List<BigDecimal> sellHeadroom = remainingSellHeadroom(sells, marketValues);
    if (sellHeadroom.stream()
        .noneMatch(v -> v.compareTo(TradeDistribution.MIN_MEANINGFUL_AMOUNT) > 0)) {
      return residual;
    }
    addInto(
        sells,
        CappedDistribution.distributeCapped(sellHeadroom, residual, threshold, sellHeadroom));
    return targetSellAmount.subtract(sumArray(sells));
  }

  private static BigDecimal spillTopUpSellingPositions(
      BigDecimal[] sells,
      List<BigDecimal> marketValues,
      BigDecimal targetSellAmount,
      BigDecimal residual) {
    if (residual.compareTo(TradeDistribution.MIN_MEANINGFUL_AMOUNT) <= 0) {
      return residual;
    }
    List<BigDecimal> topUpHeadroom =
        IntStream.range(0, sells.length)
            .mapToObj(
                i ->
                    sells[i].compareTo(TradeDistribution.MIN_MEANINGFUL_AMOUNT) > 0
                        ? marketValues.get(i).subtract(sells[i]).max(BigDecimal.ZERO)
                        : BigDecimal.ZERO)
            .toList();
    if (topUpHeadroom.stream()
        .noneMatch(v -> v.compareTo(TradeDistribution.MIN_MEANINGFUL_AMOUNT) > 0)) {
      return residual;
    }
    addInto(
        sells,
        CappedDistribution.distributeCapped(
            topUpHeadroom, residual, BigDecimal.ZERO, topUpHeadroom));
    return targetSellAmount.subtract(sumArray(sells));
  }

  private static void spillLiquidateTrappedOddLots(
      BigDecimal[] sells,
      List<BigDecimal> marketValues,
      List<BigDecimal> scores,
      BigDecimal threshold,
      BigDecimal residual) {
    if (residual.compareTo(TradeDistribution.MIN_MEANINGFUL_AMOUNT) <= 0) {
      return;
    }
    BigDecimal thresholdTolerance = threshold.subtract(TradeDistribution.MIN_MEANINGFUL_AMOUNT);
    List<Integer> oddLots = new ArrayList<>();
    for (int i = 0; i < sells.length; i++) {
      BigDecimal remaining = marketValues.get(i).subtract(sells[i]);
      if (remaining.compareTo(TradeDistribution.MIN_MEANINGFUL_AMOUNT) > 0
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
      if (remaining.compareTo(TradeDistribution.MIN_MEANINGFUL_AMOUNT) <= 0) {
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
        .mapToObj(i -> marketValues.get(i).subtract(sells[i]).max(BigDecimal.ZERO))
        .toList();
  }

  static void addInto(BigDecimal[] out, BigDecimal[] delta) {
    for (int i = 0; i < out.length; i++) {
      out[i] = out[i].add(delta[i]);
    }
  }

  static BigDecimal sumArray(BigDecimal[] values) {
    BigDecimal total = BigDecimal.ZERO;
    for (BigDecimal value : values) {
      total = total.add(value);
    }
    return total;
  }
}
