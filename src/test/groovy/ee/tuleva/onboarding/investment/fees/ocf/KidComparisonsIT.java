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
  void anIncompleteMonthIsNotComparedSoAReviewKeepingTheKidForItsFigureIsNotShown() {
    var calculation =
        storedCalculation(
            TUK75, "0.00450000", false, "{\"gaps\":[\"NO_PUBLISHED_NAV_CALCULATION\"]}");
    var inForce = publicationRepository.recordGiven(kid(TUK75, "2026-03-01", "0.00460000", 2));
    kidReviewRepository.recordUnlessPublishedOrRecalculated(calculation, inForce, REVIEW);

    var comparison = kidComparisons.atTheMonthEndOf(TUK75, calculation);

    assertThat(comparison)
        .isEqualTo(new KidComparison(new PublishedPrecision(2), inForce, null, false));
    assertThat(comparison.differsFrom(calculation)).isFalse();
  }

  @Test
  void aMonthAlreadyPublishedAsAKidDoesNotDifferFromTheOlderKidInAnyLaterVersion() {
    var firstVersion = storedCalculation(TUK75, "0.00450000");
    var inForce = publicationRepository.recordGiven(kid(TUK75, "2026-03-01", "0.00460000", 2));
    publicationRepository.recordFromCalculation(
        firstVersion,
        new OcfPublication(
            "TUK75",
            LocalDate.of(2026, 5, 1),
            new BigDecimal("0.00450000"),
            2,
            MONTHLY_ESTIMATE,
            "the monthly estimate",
            firstVersion.storedId(),
            new BigDecimal("0.00450000"),
            7L,
            null,
            null,
            "KID 01.05.2026",
            "portfolio-manager"));
    var laterVersion = storedCalculation(TUK75, "0.00450000");

    var comparison = kidComparisons.atTheMonthEndOf(TUK75, laterVersion);

    assertThat(laterVersion.version()).isEqualTo(2);
    assertThat(comparison)
        .isEqualTo(new KidComparison(new PublishedPrecision(2), inForce, null, true));
    assertThat(comparison.differsFrom(laterVersion)).isFalse();
  }

  @Test
  void aFundWithNoKidRecordedComparesWithNothing() {
    var calculation = storedCalculation(TUK75, "0.00450000");

    assertThat(kidComparisons.atTheMonthEndOf(TUK75, calculation))
        .isEqualTo(new KidComparison(new PublishedPrecision(2), null, null, false));
  }

  @Test
  void theKidComparedWithIsTheOneInForceAtTheMonthEndNotOneTakingEffectLater() {
    var calculation = storedCalculation(TUK75, "0.00450000");
    var inForce = publicationRepository.recordGiven(kid(TUK75, "2026-03-01", "0.00460000", 2));
    publicationRepository.recordGiven(kid(TUK75, "2026-05-01", "0.00450000", 2));

    var comparison = kidComparisons.atTheMonthEndOf(TUK75, calculation);

    assertThat(comparison)
        .isEqualTo(new KidComparison(new PublishedPrecision(2), inForce, null, false));
    assertThat(comparison.differsFrom(calculation)).isTrue();
  }

  @Test
  void aReviewThatKeptTheKidForThisFigureClearsTheDifference() {
    var calculation = storedCalculation(TUK75, "0.00450000");
    var inForce = publicationRepository.recordGiven(kid(TUK75, "2026-03-01", "0.00460000", 2));
    kidReviewRepository.recordUnlessPublishedOrRecalculated(calculation, inForce, REVIEW);

    assertThat(kidComparisons.atTheMonthEndOf(TUK75, calculation))
        .isEqualTo(new KidComparison(new PublishedPrecision(2), inForce, REVIEW, false));
  }

  @Test
  void aSavingsFundKidPrintedAtOneDecimalDoesNotDifferFromAMonthRoundingToTheSameTenth() {
    var calculation = storedCalculation(TKF100, "0.00491200");
    var inForce = publicationRepository.recordGiven(kid(TKF100, "2026-01-15", "0.00520000", 1));

    var comparison = kidComparisons.atTheMonthEndOf(TKF100, calculation);

    assertThat(comparison)
        .isEqualTo(new KidComparison(new PublishedPrecision(1), inForce, null, false));
    assertThat(comparison.precision()).isEqualTo(new PublishedPrecision(1));
    assertThat(comparison.differsFrom(calculation)).isFalse();
  }

  @Test
  void theSameSavingsFundMonthDiffersFromAKidPrintedAtTwoDecimals() {
    var calculation = storedCalculation(TKF100, "0.00491200");
    var inForce = publicationRepository.recordGiven(kid(TKF100, "2026-01-15", "0.00520000", 2));

    var comparison = kidComparisons.atTheMonthEndOf(TKF100, calculation);

    assertThat(comparison)
        .isEqualTo(new KidComparison(new PublishedPrecision(1), inForce, null, false));
    assertThat(comparison.precision()).isEqualTo(new PublishedPrecision(2));
    assertThat(comparison.differsFrom(calculation)).isTrue();
  }

  @Test
  void withNoKidInForceTheComparisonFallsBackToTheFundsRuleForANewKid() {
    var calculation = storedCalculation(TKF100, "0.00491200");

    assertThat(kidComparisons.atTheMonthEndOf(TKF100, calculation).precision())
        .isEqualTo(new PublishedPrecision(1));
  }

  private OcfSnapshot storedCalculation(TulevaFund fund, String totalOcf) {
    return storedCalculation(fund, totalOcf, true, "{\"gaps\":[]}");
  }

  private OcfSnapshot storedCalculation(
      TulevaFund fund, String totalOcf, boolean complete, String checks) {
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
            complete,
            checks,
            new OcfAudit(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null)),
        List.of());
    return snapshotRepository.findByFundAndMonth(fund.getCode(), APRIL).orElseThrow();
  }

  private static OcfPublication kid(
      TulevaFund fund, String validFrom, String publishedOcf, int decimals) {
    return OcfPublication.notFromACalculation(
        fund.getCode(),
        LocalDate.parse(validFrom),
        new BigDecimal(publishedOcf),
        decimals,
        MONTHLY_ESTIMATE,
        "the KID already in force",
        null,
        null,
        "KID " + validFrom,
        "portfolio-manager");
  }
}
