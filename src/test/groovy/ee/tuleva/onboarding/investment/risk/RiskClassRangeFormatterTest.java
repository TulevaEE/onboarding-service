package ee.tuleva.onboarding.investment.risk;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static ee.tuleva.onboarding.investment.risk.RiskIndicatorStatus.STABLE;
import static ee.tuleva.onboarding.investment.risk.RiskIndicatorType.SRI;
import static ee.tuleva.onboarding.investment.risk.RiskIndicatorType.SRRI;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class RiskClassRangeFormatterTest {

  private static final LocalDate EVALUATION_DATE = LocalDate.of(2026, 7, 31);

  private final RiskClassRangeFormatter rangeFormatter = new RiskClassRangeFormatter();

  @Test
  void aSriClassWithVolatilityReportsItsDistanceToTheNearestBound() {
    assertThat(rangeFormatter.rangeLine(stableSri()))
        .isEqualTo("VEV 0,1540 (klassi 4 vahemik 0,1200–0,2000); lähim piir on 0,0340 kaugusel.");
  }

  @Test
  void aClassWithoutAVolatilityOnlyPrintsItsRange() {
    assertThat(rangeFormatter.rangeLine(withVolatility(stableSri(), null)))
        .isEqualTo("Klassi 4 vahemik 0,1200–0,2000.");
  }

  @Test
  void theOpenEndedTopClassPrintsItsRangeWithoutAnUpperBound() {
    assertThat(rangeFormatter.rangeLine(topClassSrri()))
        .isEqualTo(
            "Aastane volatiilsus 30,00% (klassi 7 vahemik 25,00%–∞); lähim piir on 5,00%"
                + " kaugusel.");
  }

  @Test
  void volatilityLabelDiffersBetweenSriAndSrri() {
    assertThat(rangeFormatter.volatilityLabel(stableSri())).isEqualTo("VEV");
    assertThat(rangeFormatter.volatilityLabel(topClassSrri())).isEqualTo("Aastane volatiilsus");
  }

  @Test
  void volatilityIsPrintedAsDecimalsForSriAndAsPercentForSrri() {
    assertThat(rangeFormatter.volatility(stableSri())).isEqualTo("0,1540");
    assertThat(rangeFormatter.volatility(topClassSrri())).isEqualTo("30,00%");
  }

  @Test
  void aMissingVolatilityIsPrintedAsADash() {
    assertThat(rangeFormatter.volatility(withVolatility(stableSri(), null))).isEqualTo("—");
  }

  private PublishedRiskIndicator withVolatility(
      PublishedRiskIndicator indicator, @Nullable BigDecimal volatility) {
    return new PublishedRiskIndicator(
        indicator.fund(),
        indicator.indicatorType(),
        indicator.evaluationDate(),
        indicator.publishedClass(),
        indicator.rawLatestClass(),
        indicator.previousPublishedClass(),
        indicator.publishedSince(),
        indicator.publishedSinceIsTruncated(),
        indicator.rawClassSince(),
        indicator.streakReferencePoints(),
        indicator.rawStreakReferencePoints(),
        indicator.windowReferencePoints(),
        indicator.matchingReferencePoints(),
        indicator.latestObservationCount(),
        volatility,
        indicator.status());
  }

  private PublishedRiskIndicator stableSri() {
    return new PublishedRiskIndicator(
        TKF100,
        SRI,
        EVALUATION_DATE,
        4,
        4,
        null,
        LocalDate.of(2026, 3, 14),
        false,
        LocalDate.of(2026, 3, 14),
        85,
        85,
        85,
        85,
        1305,
        new BigDecimal("0.154000000000"),
        STABLE);
  }

  private PublishedRiskIndicator topClassSrri() {
    return new PublishedRiskIndicator(
        TUV100,
        SRRI,
        EVALUATION_DATE,
        7,
        7,
        6,
        LocalDate.of(2025, 5, 5),
        false,
        LocalDate.of(2025, 5, 5),
        60,
        60,
        17,
        17,
        260,
        new BigDecimal("0.300000000000"),
        STABLE);
  }
}
