package ee.tuleva.onboarding.investment.cashbuffer;

import static ee.tuleva.onboarding.investment.cashbuffer.Percentile.interpolatedBetweenClosestRanks;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import java.math.BigDecimal;

record BufferModel(
    BigDecimal outflowPercentile,
    BigDecimal inflowPercentile,
    BigDecimal inflowCredit,
    BigDecimal floor) {

  Recommendation recommend(FlowWindow window, BigDecimal accruedFees) {
    var outflowAtPercentile =
        interpolatedBetweenClosestRanks(window.operatingOutflows(), outflowPercentile);
    var inflowAtPercentile = interpolatedBetweenClosestRanks(window.inflows(), inflowPercentile);
    var outflowNotCoveredByInflow =
        outflowAtPercentile.subtract(inflowCredit.multiply(inflowAtPercentile)).max(ZERO);
    var recommended = floor.add(outflowNotCoveredByInflow).add(accruedFees).setScale(2, HALF_UP);
    return new Recommendation(
        this, outflowAtPercentile, inflowAtPercentile, accruedFees, recommended);
  }
}
