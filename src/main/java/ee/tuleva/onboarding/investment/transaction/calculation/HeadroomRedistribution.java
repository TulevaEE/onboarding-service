package ee.tuleva.onboarding.investment.transaction.calculation;

import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.investment.transaction.FundTransactionInput;
import ee.tuleva.onboarding.investment.transaction.PositionSnapshot;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
final class HeadroomRedistribution {

  private HeadroomRedistribution() {}

  static BigDecimal waterFillExcessAcrossRunners(
      FundTransactionInput input, List<BigDecimal> scores, BigDecimal[] capped, BigDecimal excess) {
    int size = capped.length;
    BigDecimal threshold = input.minTransactionThreshold();
    BigDecimal remaining = excess;

    for (int iteration = 0; iteration < TradeDistribution.MAX_ITERATIONS; iteration++) {
      if (remaining.compareTo(TradeDistribution.MIN_MEANINGFUL_AMOUNT) <= 0) {
        break;
      }

      List<BigDecimal> roundScores = runnerScores(input, scores, capped);
      if (roundScores.stream().allMatch(score -> score.compareTo(ZERO) == 0)) {
        break;
      }

      List<BigDecimal> round =
          TradeDistribution.distributeAmountWithThreshold(roundScores, remaining, threshold);

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
}
