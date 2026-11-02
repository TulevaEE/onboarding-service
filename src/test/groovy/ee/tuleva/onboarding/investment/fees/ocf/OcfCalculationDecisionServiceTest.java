package ee.tuleva.onboarding.investment.fees.ocf;

import static ee.tuleva.onboarding.investment.fees.ocf.OcfPublicationBasis.MONTHLY_ESTIMATE;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OcfCalculationDecisionServiceTest {

  private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
  private static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");
  private static final String COMPLETE_CHECKS = "{\"gaps\":[]}";
  private static final String INCOMPLETE_CHECKS = "{\"gaps\":[\"MANAGEMENT_FEE_RATE_MISSING\"]}";
  private static final PublishedPrecision TWO_DECIMALS = new PublishedPrecision(2);
  private static final KidFigure KID =
      new KidFigure(
          5L,
          LocalDate.of(2026, 3, 1),
          new BigDecimal("0.00460000"),
          2,
          MONTHLY_ESTIMATE,
          "KID 2026");

  private static final KidFigure ONE_DECIMAL_KID =
      new KidFigure(
          6L,
          LocalDate.of(2026, 3, 1),
          new BigDecimal("0.00460000"),
          1,
          MONTHLY_ESTIMATE,
          "KID 2026 one decimal");

  @Mock private OcfSnapshotRepository ocfSnapshotRepository;
  @Mock private KidComparisons kidComparisons;
  @Mock private OcfExceptionRepository exceptionRepository;
  @Mock private OcfKidReviewRepository kidReviewRepository;

  private OcfCalculationDecisionService service;

  @BeforeEach
  void setUp() {
    service =
        new OcfCalculationDecisionService(
            new OcfCalculationVersions(ocfSnapshotRepository),
            kidComparisons,
            exceptionRepository,
            kidReviewRepository,
            Clock.fixed(NOW, UTC));
  }

  private static OcfSnapshot snapshot(
      String totalOcf, String checks, @Nullable Instant publishedAt) {
    return new OcfSnapshot(
        42L,
        "TUK75",
        OCTOBER.atDay(1),
        2,
        OcfMethodology.EX_ANTE_NET_ASSETS_V2,
        new BigDecimal("0.00200000"),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        RebateBasis.NET,
        BigDecimal.ZERO,
        new BigDecimal(totalOcf),
        COMPLETE_CHECKS.equals(checks),
        checks,
        publishedAt,
        publishedAt == null ? null : "KID 2026",
        new OcfAudit(
            null, null, null, null, null, null, null, null, null, null, null, null, null, null,
            null));
  }

  private void givenTheVersion(OcfSnapshot snapshot) {
    given(ocfSnapshotRepository.findVersion("TUK75", OCTOBER.atDay(1), 2))
        .willReturn(Optional.of(snapshot));
  }

  private void givenTheKid(
      OcfSnapshot snapshot, @Nullable KidFigure kid, @Nullable KidReview keptOnReview) {
    given(kidComparisons.atTheMonthEndOf(TUK75, snapshot))
        .willReturn(new KidComparison(TWO_DECIMALS, kid, keptOnReview));
  }

  private static OcfCalculationPreview previewOf(
      OcfSnapshot snapshot,
      @Nullable KidFigure kid,
      boolean differs,
      @Nullable ExceptionDecision exception,
      @Nullable KidReview keptOnReview) {
    return previewOf(snapshot, kid, 2, differs, exception, keptOnReview);
  }

  private static OcfCalculationPreview previewOf(
      OcfSnapshot snapshot,
      @Nullable KidFigure kid,
      int publishedDecimals,
      boolean differs,
      @Nullable ExceptionDecision exception,
      @Nullable KidReview keptOnReview) {
    return new OcfCalculationPreview(
        "TUK75",
        OCTOBER,
        2,
        snapshot.complete(),
        snapshot.checks(),
        OcfCalculationFingerprint.of(snapshot),
        snapshot.totalOcf(),
        new BigDecimal("0.00200000"),
        publishedDecimals,
        kid,
        differs,
        exception,
        keptOnReview,
        snapshot.publishedAt() != null);
  }

  private static OcfExceptionRequest exceptionFor(OcfSnapshot previewed) {
    return new OcfExceptionRequest(
        TUK75,
        OCTOBER,
        2,
        OcfCalculationFingerprint.of(previewed),
        "the board decided the fee",
        "fund-manager");
  }

  private static OcfKidReviewRequest reviewOf(OcfSnapshot previewed) {
    return reviewOf(previewed, KID.publicationId());
  }

  private static OcfKidReviewRequest reviewOf(OcfSnapshot previewed, long publicationId) {
    return new OcfKidReviewRequest(
        TUK75,
        OCTOBER,
        2,
        OcfCalculationFingerprint.of(previewed),
        publicationId,
        "a one-off rebate, the KID stays",
        "portfolio-manager");
  }

  @Test
  void aCompleteCalculationRoundingAwayFromTheKidInForceAtTheMonthEndDiffersFromIt() {
    var snapshot = snapshot("0.00450000", COMPLETE_CHECKS, null);
    given(ocfSnapshotRepository.findByFundAndMonth("TUK75", OCTOBER.atDay(1)))
        .willReturn(Optional.of(snapshot));
    givenTheKid(snapshot, KID, null);

    assertThat(service.preview(TUK75, OCTOBER))
        .isEqualTo(previewOf(snapshot, KID, true, null, null));
  }

  @Test
  void aCalculationRoundingToTheKidAtThePublishedPrecisionDoesNotDifferFromIt() {
    var snapshot = snapshot("0.00464000", COMPLETE_CHECKS, null);
    given(ocfSnapshotRepository.findByFundAndMonth("TUK75", OCTOBER.atDay(1)))
        .willReturn(Optional.of(snapshot));
    givenTheKid(snapshot, KID, null);

    assertThat(service.preview(TUK75, OCTOBER))
        .isEqualTo(previewOf(snapshot, KID, false, null, null));
  }

  @Test
  void aKidPrintedAtOneDecimalIsComparedAndShownAtOneDecimal() {
    var snapshot = snapshot("0.00450000", COMPLETE_CHECKS, null);
    given(ocfSnapshotRepository.findByFundAndMonth("TUK75", OCTOBER.atDay(1)))
        .willReturn(Optional.of(snapshot));
    givenTheKid(snapshot, ONE_DECIMAL_KID, null);

    assertThat(service.preview(TUK75, OCTOBER))
        .isEqualTo(previewOf(snapshot, ONE_DECIMAL_KID, 1, false, null, null));
  }

  @Test
  void aMonthRoundingToAOneDecimalKidHasNothingToReview() {
    var snapshot = snapshot("0.00450000", COMPLETE_CHECKS, null);
    givenTheVersion(snapshot);
    givenTheKid(snapshot, ONE_DECIMAL_KID, null);

    assertThatThrownBy(
            () -> service.recordReview(reviewOf(snapshot, ONE_DECIMAL_KID.publicationId())))
        .isInstanceOf(OcfPublicationRefusedException.class);
    then(kidReviewRepository).should(never()).record(any(), any(), any());
  }

  @Test
  void anIncompleteCalculationIsNotComparedWithTheKid() {
    var snapshot = snapshot("0.00450000", INCOMPLETE_CHECKS, null);
    given(ocfSnapshotRepository.findByFundAndMonth("TUK75", OCTOBER.atDay(1)))
        .willReturn(Optional.of(snapshot));
    givenTheKid(snapshot, KID, null);

    assertThat(service.preview(TUK75, OCTOBER))
        .isEqualTo(previewOf(snapshot, KID, false, null, null));
  }

  @Test
  void aMonthWithoutACalculationCannotBePreviewed() {
    assertThatThrownBy(() -> service.preview(TUK75, OCTOBER))
        .isInstanceOf(OcfPublicationRefusedException.class);
  }

  @Test
  void theFundManagersExceptionIsRecordedForTheCalculationAsItStandsAndShownInTheAnswer() {
    var snapshot = snapshot("0.00450000", INCOMPLETE_CHECKS, null);
    var decision = new ExceptionDecision("fund-manager", "the board decided the fee", NOW);
    givenTheVersion(snapshot);
    givenTheKid(snapshot, null, null);
    given(exceptionRepository.findForTheCurrentCalculation(snapshot))
        .willReturn(Optional.empty(), Optional.of(decision));

    var answer = service.recordException(exceptionFor(snapshot));

    then(exceptionRepository).should().record(snapshot, decision);
    assertThat(answer).isEqualTo(previewOf(snapshot, null, false, decision, null));
  }

  @Test
  void aCompleteCalculationNeedsNoException() {
    var snapshot = snapshot("0.00450000", COMPLETE_CHECKS, null);
    givenTheVersion(snapshot);

    assertThatThrownBy(() -> service.recordException(exceptionFor(snapshot)))
        .isInstanceOf(OcfPublicationRefusedException.class);
    then(exceptionRepository).should(never()).record(any(), any());
  }

  @Test
  void aPublishedCalculationIsNotExceptedAfterwards() {
    var snapshot = snapshot("0.00450000", INCOMPLETE_CHECKS, Instant.EPOCH);
    givenTheVersion(snapshot);

    assertThatThrownBy(() -> service.recordException(exceptionFor(snapshot)))
        .isInstanceOf(OcfPublicationRefusedException.class);
    then(exceptionRepository).should(never()).record(any(), any());
  }

  @Test
  void aCalculationWhoseGapsChangedSinceThePreviewIsNotExcepted() {
    givenTheVersion(snapshot("0.00450000", "{\"gaps\":[\"NO_PUBLISHED_NAV_CALCULATION\"]}", null));

    assertThatThrownBy(
            () ->
                service.recordException(
                    exceptionFor(snapshot("0.00450000", INCOMPLETE_CHECKS, null))))
        .isInstanceOf(OcfPublicationRefusedException.class);
    then(exceptionRepository).should(never()).record(any(), any());
  }

  @Test
  void anUnknownVersionCannotBeExcepted() {
    assertThatThrownBy(
            () ->
                service.recordException(
                    exceptionFor(snapshot("0.00450000", INCOMPLETE_CHECKS, null))))
        .isInstanceOf(OcfPublicationRefusedException.class);
  }

  @Test
  void aCalculationAlreadyExceptedIsNotExceptedAgain() {
    var snapshot = snapshot("0.00450000", INCOMPLETE_CHECKS, null);
    givenTheVersion(snapshot);
    given(exceptionRepository.findForTheCurrentCalculation(snapshot))
        .willReturn(Optional.of(new ExceptionDecision("fund-manager", "earlier", NOW)));

    assertThatThrownBy(() -> service.recordException(exceptionFor(snapshot)))
        .isInstanceOf(OcfPublicationRefusedException.class);
    then(exceptionRepository).should(never()).record(any(), any());
  }

  @Test
  void aReviewKeepingTheKidIsRecordedAgainstTheKidInForceAndClearsTheDifference() {
    var snapshot = snapshot("0.00450000", COMPLETE_CHECKS, null);
    var review = new KidReview("portfolio-manager", "a one-off rebate, the KID stays", NOW);
    givenTheVersion(snapshot);
    given(kidComparisons.atTheMonthEndOf(TUK75, snapshot))
        .willReturn(
            new KidComparison(TWO_DECIMALS, KID, null),
            new KidComparison(TWO_DECIMALS, KID, review));

    var answer = service.recordReview(reviewOf(snapshot));

    then(kidReviewRepository).should().record(snapshot, KID, review);
    assertThat(answer).isEqualTo(previewOf(snapshot, KID, true, null, review));
  }

  @Test
  void withNoKidInForceAtTheMonthEndThereIsNothingToReview() {
    var snapshot = snapshot("0.00450000", COMPLETE_CHECKS, null);
    givenTheVersion(snapshot);
    givenTheKid(snapshot, null, null);

    assertThatThrownBy(() -> service.recordReview(reviewOf(snapshot)))
        .isInstanceOf(OcfPublicationRefusedException.class);
    then(kidReviewRepository).should(never()).record(any(), any(), any());
  }

  @Test
  void aReviewOfAKidNoLongerInForceAtTheMonthEndIsNotRecorded() {
    var snapshot = snapshot("0.00450000", COMPLETE_CHECKS, null);
    givenTheVersion(snapshot);
    givenTheKid(snapshot, KID, null);

    assertThatThrownBy(() -> service.recordReview(reviewOf(snapshot, KID.publicationId() - 1)))
        .isInstanceOf(OcfPublicationRefusedException.class);
    then(kidReviewRepository).should(never()).record(any(), any(), any());
  }

  @Test
  void aCalculationRoundingToTheKidAtThePublishedPrecisionHasNothingToReview() {
    var snapshot = snapshot("0.00464000", COMPLETE_CHECKS, null);
    givenTheVersion(snapshot);
    givenTheKid(snapshot, KID, null);

    assertThatThrownBy(() -> service.recordReview(reviewOf(snapshot)))
        .isInstanceOf(OcfPublicationRefusedException.class);
    then(kidReviewRepository).should(never()).record(any(), any(), any());
  }

  @Test
  void anIncompleteCalculationIsNotReviewedAgainstTheKid() {
    var snapshot = snapshot("0.00450000", INCOMPLETE_CHECKS, null);
    givenTheVersion(snapshot);
    givenTheKid(snapshot, KID, null);

    assertThatThrownBy(() -> service.recordReview(reviewOf(snapshot)))
        .isInstanceOf(OcfPublicationRefusedException.class);
    then(kidReviewRepository).should(never()).record(any(), any(), any());
  }

  @Test
  void aCalculationRecalculatedSinceThePreviewIsNotReviewed() {
    givenTheVersion(snapshot("0.00440000", COMPLETE_CHECKS, null));

    assertThatThrownBy(
            () -> service.recordReview(reviewOf(snapshot("0.00450000", COMPLETE_CHECKS, null))))
        .isInstanceOf(OcfPublicationRefusedException.class);
    then(kidReviewRepository).should(never()).record(any(), any(), any());
  }
}
