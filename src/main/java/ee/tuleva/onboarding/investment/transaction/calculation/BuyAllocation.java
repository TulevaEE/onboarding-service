package ee.tuleva.onboarding.investment.transaction.calculation;

import static ee.tuleva.onboarding.investment.transaction.calculation.HeadroomRedistribution.hardLimitHeadroom;
import static ee.tuleva.onboarding.investment.transaction.calculation.HeadroomRedistribution.runnerScores;
import static ee.tuleva.onboarding.investment.transaction.calculation.HeadroomRedistribution.waterFillExcessAcrossRunners;
import static ee.tuleva.onboarding.investment.transaction.calculation.TradeDistribution.MIN_MEANINGFUL_AMOUNT;
import static ee.tuleva.onboarding.investment.transaction.calculation.TradeDistribution.SCALE;
import static ee.tuleva.onboarding.investment.transaction.calculation.TradeDistribution.distributeAmountWithThreshold;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.investment.transaction.FundTransactionInput;
import ee.tuleva.onboarding.investment.transaction.PositionSnapshot;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.jspecify.annotations.NullMarked;

@NullMarked
final class BuyAllocation {

  private BuyAllocation() {}

  static List<BigDecimal> calculateBuy(
      FundTransactionInput input, BigDecimal netInvestable, Map<String, BigDecimal> weightMap) {
    if (input.positions().isEmpty()) {
      return List.of();
    }

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

  static List<BigDecimal> redistributeHardLimitExcess(
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

  private static void topUpBestRunnerToThreshold(
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

  private static List<BigDecimal> excludeScoresWithoutHeadroom(
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

  private static List<BigDecimal> fallbackBuyScores(
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
}
