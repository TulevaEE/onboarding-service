package ee.tuleva.onboarding.investment.cashbuffer;

import java.math.BigDecimal;

record Recommendation(
    BufferModel model,
    BigDecimal outflowAtPercentile,
    BigDecimal inflowAtPercentile,
    BigDecimal horizonOutflowAtPercentile,
    BigDecimal accruedFees,
    BigDecimal recommendedSoft,
    BigDecimal recommendedHard) {}
