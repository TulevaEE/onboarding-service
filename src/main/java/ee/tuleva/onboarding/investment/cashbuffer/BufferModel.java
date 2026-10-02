package ee.tuleva.onboarding.investment.cashbuffer;

import static ee.tuleva.onboarding.investment.cashbuffer.Percentile.interpolatedBetweenClosestRanks;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import java.math.BigDecimal;

record BufferModel(
    BigDecimal outflowPercentile,
    BigDecimal inflowPercentile,
    BigDecimal inflowCredit,
    int settlementHorizonDays) {

  Recommendation recommend(
      FlowWindow window, BusinessDayOutflows businessDayOutflows, BigDecimal accruedFees) {
    var outflowAtPercentile =
        interpolatedBetweenClosestRanks(window.operatingOutflows(), outflowPercentile);
    var inflowAtPercentile = interpolatedBetweenClosestRanks(window.inflows(), inflowPercentile);
    var horizonOutflowAtPercentile =
        interpolatedBetweenClosestRanks(
            businessDayOutflows.forwardRollingTotals(settlementHorizonDays), outflowPercentile);
    var recommendedHard = horizonOutflowAtPercentile.add(accruedFees).setScale(2, HALF_UP);
    var outflowNotCoveredByInflow =
        outflowAtPercentile.subtract(inflowCredit.multiply(inflowAtPercentile)).max(ZERO);
    var recommendedSoft =
        outflowNotCoveredByInflow.add(accruedFees).setScale(2, HALF_UP).max(recommendedHard);
    return new Recommendation(
        this,
        outflowAtPercentile,
        inflowAtPercentile,
        horizonOutflowAtPercentile,
        accruedFees,
        recommendedSoft,
        recommendedHard);
  }
}
