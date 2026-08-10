package ee.tuleva.onboarding.investment.risk;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record PublishedRiskIndicator(
    TulevaFund fund,
    RiskIndicatorType indicatorType,
    LocalDate evaluationDate,
    @Nullable Integer publishedClass,
    @Nullable Integer rawLatestClass,
    @Nullable Integer previousPublishedClass,
    @Nullable LocalDate publishedSince,
    @Nullable LocalDate rawClassSince,
    int streakReferencePoints,
    int rawStreakReferencePoints,
    int windowReferencePoints,
    int matchingReferencePoints,
    int latestObservationCount,
    @Nullable BigDecimal latestVolatility,
    RiskIndicatorStatus status) {

  static PublishedRiskIndicator insufficientData(
      TulevaFund fund,
      RiskIndicatorType indicatorType,
      LocalDate evaluationDate,
      int latestObservationCount,
      @Nullable BigDecimal latestVolatility) {
    return new PublishedRiskIndicator(
        fund,
        indicatorType,
        evaluationDate,
        null,
        null,
        null,
        null,
        null,
        0,
        0,
        0,
        0,
        latestObservationCount,
        latestVolatility,
        RiskIndicatorStatus.STABLE);
  }

  public boolean hasClass() {
    return publishedClass != null;
  }
}
