package ee.tuleva.onboarding.investment.fees.ocf;

import static ee.tuleva.onboarding.investment.fees.ocf.OcfPublicationBasis.MONTHLY_ESTIMATE;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({
  KidComparisons.class,
  OcfKidReviewRepository.class,
  OcfPublicationRepository.class,
  OcfSnapshotRepository.class
})
class KidComparisonsIT {

  private static final LocalDate APRIL = LocalDate.of(2026, 4, 1);
  private static final KidReview REVIEW =
      new KidReview("portfolio-manager", "a one-off rebate", Instant.parse("2026-05-05T08:00:00Z"));

  @Autowired private KidComparisons kidComparisons;
  @Autowired private OcfKidReviewRepository kidReviewRepository;
  @Autowired private OcfPublicationRepository publicationRepository;
  @Autowired private OcfSnapshotRepository snapshotRepository;

  @Test
  void aFundWithNoKidRecordedComparesWithNothing() {
    var calculation = storedCalculation(TUK75, "0.00450000");

    assertThat(kidComparisons.atTheMonthEndOf(TUK75, calculation))
        .isEqualTo(new KidComparison(new PublishedPrecision(2), null, null));
  }

  @Test
  void theKidComparedWithIsTheOneInForceAtTheMonthEndNotOneTakingEffectLater() {
    var calculation = storedCalculation(TUK75, "0.00450000");
    var inForce = publicationRepository.recordGiven(kid(TUK75, "2026-03-01", "0.00460000", 2));
    publicationRepository.recordGiven(kid(TUK75, "2026-05-01", "0.00450000", 2));

    var comparison = kidComparisons.atTheMonthEndOf(TUK75, calculation);

    assertThat(comparison).isEqualTo(new KidComparison(new PublishedPrecision(2), inForce, null));
    assertThat(comparison.differsFrom(calculation.totalOcf())).isTrue();
  }

  @Test
  void aReviewThatKeptTheKidForThisFigureClearsTheDifference() {
    var calculation = storedCalculation(TUK75, "0.00450000");
    var inForce = publicationRepository.recordGiven(kid(TUK75, "2026-03-01", "0.00460000", 2));
    kidReviewRepository.record(calculation, inForce, REVIEW);

    assertThat(kidComparisons.atTheMonthEndOf(TUK75, calculation))
        .isEqualTo(new KidComparison(new PublishedPrecision(2), inForce, REVIEW));
  }

  @Test
  void aSavingsFundKidPrintedAtOneDecimalDoesNotDifferFromAMonthRoundingToTheSameTenth() {
    var calculation = storedCalculation(TKF100, "0.00491200");
    var inForce = publicationRepository.recordGiven(kid(TKF100, "2026-01-15", "0.00520000", 1));

    var comparison = kidComparisons.atTheMonthEndOf(TKF100, calculation);

    assertThat(comparison).isEqualTo(new KidComparison(new PublishedPrecision(1), inForce, null));
    assertThat(comparison.precision()).isEqualTo(new PublishedPrecision(1));
    assertThat(comparison.differsFrom(calculation.totalOcf())).isFalse();
  }

  @Test
  void theSameSavingsFundMonthDiffersFromAKidPrintedAtTwoDecimals() {
    var calculation = storedCalculation(TKF100, "0.00491200");
    var inForce = publicationRepository.recordGiven(kid(TKF100, "2026-01-15", "0.00520000", 2));

    var comparison = kidComparisons.atTheMonthEndOf(TKF100, calculation);

    assertThat(comparison).isEqualTo(new KidComparison(new PublishedPrecision(1), inForce, null));
    assertThat(comparison.precision()).isEqualTo(new PublishedPrecision(2));
    assertThat(comparison.differsFrom(calculation.totalOcf())).isTrue();
  }

  @Test
  void withNoKidInForceTheComparisonFallsBackToTheFundsRuleForANewKid() {
    var calculation = storedCalculation(TKF100, "0.00491200");

    assertThat(kidComparisons.atTheMonthEndOf(TKF100, calculation).precision())
        .isEqualTo(new PublishedPrecision(1));
  }

  private OcfSnapshot storedCalculation(TulevaFund fund, String totalOcf) {
    snapshotRepository.save(
        OcfSnapshot.computed(
            fund.getCode(),
            APRIL,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal(totalOcf),
            RebateBasis.NET,
            BigDecimal.ZERO,
            new BigDecimal(totalOcf),
            true,
            "{\"gaps\":[]}",
            new OcfAudit(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null)),
        List.of());
    return snapshotRepository.findByFundAndMonth(fund.getCode(), APRIL).orElseThrow();
  }

  private static OcfPublication kid(
      TulevaFund fund, String validFrom, String publishedOcf, int decimals) {
    return new OcfPublication(
        fund.getCode(),
        LocalDate.parse(validFrom),
        new BigDecimal(publishedOcf),
        decimals,
        MONTHLY_ESTIMATE,
        "the KID already in force",
        null,
        null,
        null,
        null,
        "KID " + validFrom,
        "portfolio-manager");
  }
}
