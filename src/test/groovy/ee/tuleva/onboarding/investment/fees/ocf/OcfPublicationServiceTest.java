package ee.tuleva.onboarding.investment.fees.ocf;

import static ee.tuleva.onboarding.investment.fees.FeeRateSource.FIXED;
import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;
import static ee.tuleva.onboarding.investment.fees.ocf.OcfPublicationBasis.EX_POST;
import static ee.tuleva.onboarding.investment.fees.ocf.OcfPublicationBasis.MONTHLY_ESTIMATE;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import ee.tuleva.onboarding.investment.fees.FeeRate;
import ee.tuleva.onboarding.investment.fees.FeeRateRepository;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
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
class OcfPublicationServiceTest {

  private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
  private static final LocalDate NOVEMBER_1 = LocalDate.of(2026, 11, 1);
  private static final BigDecimal CALCULATED_OCF = new BigDecimal("0.00460000");
  private static final BigDecimal FEE_IN_THE_CALCULATION = new BigDecimal("0.00200000");
  private static final BigDecimal FEE_ON_THE_PUBLICATION_DATE = new BigDecimal("0.00190000");
  private static final BigDecimal PUBLISHED_OCF = new BigDecimal("0.00450000");
  private static final String COMPLETE_CHECKS = "{\"gaps\":[]}";
  private static final String INCOMPLETE_CHECKS = "{\"gaps\":[\"MONTH_END_NAV_NOT_PUBLISHED\"]}";
  private static final KidFigure KID_BEFORE =
      new KidFigure(
          5L,
          LocalDate.of(2026, 3, 1),
          new BigDecimal("0.00460000"),
          2,
          MONTHLY_ESTIMATE,
          "KID 02.03.2026");
  private static final ExceptionDecision EXCEPTION =
      new ExceptionDecision(
          "fund-manager", "the board decided the fee", Instant.parse("2026-11-01T08:00:00Z"));

  @Mock private OcfSnapshotRepository ocfSnapshotRepository;
  @Mock private FeeRateRepository feeRateRepository;
  @Mock private OcfPublicationRepository publicationRepository;
  @Mock private OcfExceptionRepository exceptionRepository;

  private OcfPublicationService service;

  @BeforeEach
  void setUp() {
    service =
        new OcfPublicationService(
            new OcfCalculationVersions(ocfSnapshotRepository),
            feeRateRepository,
            publicationRepository,
            exceptionRepository);
  }

  private static OcfSnapshot snapshot(boolean complete, @Nullable Instant publishedAt) {
    return snapshot(TUK75, complete ? COMPLETE_CHECKS : INCOMPLETE_CHECKS, publishedAt);
  }

  private static OcfSnapshot snapshot(
      TulevaFund fund, String checks, @Nullable Instant publishedAt) {
    return new OcfSnapshot(
        42L,
        fund.getCode(),
        OCTOBER.atDay(1),
        2,
        OcfMethodology.EX_ANTE_NET_ASSETS_V2,
        FEE_IN_THE_CALCULATION,
        BigDecimal.ZERO,
        new BigDecimal("0.00260000"),
        new BigDecimal("0.00260000"),
        new BigDecimal("0.00260000"),
        RebateBasis.NET,
        BigDecimal.ZERO,
        CALCULATED_OCF,
        COMPLETE_CHECKS.equals(checks),
        checks,
        publishedAt,
        publishedAt == null ? null : "KID 2026",
        new OcfAudit(
            null, null, null, null, null, null, null, null, null, null, null, null, null, null,
            null));
  }

  private void givenTheFeeOnThePublicationDate() {
    givenTheFeeOnThePublicationDate(TUK75);
  }

  private void givenTheFeeOnThePublicationDate(TulevaFund fund) {
    given(feeRateRepository.findValidRate(fund, MANAGEMENT, NOVEMBER_1))
        .willReturn(
            Optional.of(
                new FeeRate(
                    9L, fund, MANAGEMENT, FEE_ON_THE_PUBLICATION_DATE, FIXED, NOVEMBER_1, null)));
  }

  private void givenTheStoredVersion(OcfSnapshot snapshot) {
    given(ocfSnapshotRepository.findVersion("TUK75", OCTOBER.atDay(1), 2))
        .willReturn(Optional.of(snapshot));
  }

  private static OcfPublicationRequest request(OcfSnapshot previewed, BigDecimal publishedOcf) {
    return request(previewed, publishedOcf, "portfolio-manager");
  }

  private static OcfPublicationRequest request(
      OcfSnapshot previewed, BigDecimal publishedOcf, String publishedBy) {
    return new OcfPublicationRequest(
        TUK75,
        OCTOBER,
        2,
        OcfCalculationFingerprint.of(previewed),
        NOVEMBER_1,
        publishedOcf,
        "KID 01.11.2026",
        publishedBy,
        "the monthly estimate");
  }

  private static OcfPublication publicationOf(String publishedBy) {
    return new OcfPublication(
        "TUK75",
        NOVEMBER_1,
        PUBLISHED_OCF,
        2,
        MONTHLY_ESTIMATE,
        "the monthly estimate",
        FEE_ON_THE_PUBLICATION_DATE,
        9L,
        null,
        null,
        "KID 01.11.2026",
        publishedBy);
  }

  private static OcfPublicationPreview previewOf(
      OcfSnapshot snapshot, @Nullable KidFigure kidBefore, @Nullable ExceptionDecision exception) {
    return new OcfPublicationPreview(
        "TUK75",
        OCTOBER,
        2,
        snapshot.complete(),
        snapshot.checks(),
        OcfCalculationFingerprint.of(snapshot),
        CALCULATED_OCF,
        FEE_IN_THE_CALCULATION,
        NOVEMBER_1,
        FEE_ON_THE_PUBLICATION_DATE,
        PUBLISHED_OCF,
        new BigDecimal("0.45"),
        2,
        kidBefore,
        exception,
        snapshot.publishedAt() != null);
  }

  @Test
  void thePreviewPublishesTheCalculationWithTheFeeInForceOnThePublicationDate() {
    given(ocfSnapshotRepository.findByFundAndMonth("TUK75", OCTOBER.atDay(1)))
        .willReturn(Optional.of(snapshot(true, null)));
    givenTheFeeOnThePublicationDate();
    given(publicationRepository.findInForceOn("TUK75", NOVEMBER_1.minusDays(1)))
        .willReturn(Optional.of(KID_BEFORE));

    var preview = service.preview(TUK75, OCTOBER, NOVEMBER_1);

    assertThat(preview).isEqualTo(previewOf(snapshot(true, null), KID_BEFORE, null));
  }

  @Test
  void thePreviewOfAnIncompleteCalculationShowsTheFundManagersException() {
    given(ocfSnapshotRepository.findByFundAndMonth("TUK75", OCTOBER.atDay(1)))
        .willReturn(Optional.of(snapshot(false, null)));
    givenTheFeeOnThePublicationDate();
    given(exceptionRepository.findForTheCurrentCalculation(snapshot(false, null)))
        .willReturn(Optional.of(EXCEPTION));

    var preview = service.preview(TUK75, OCTOBER, NOVEMBER_1);

    assertThat(preview).isEqualTo(previewOf(snapshot(false, null), null, EXCEPTION));
  }

  @Test
  void theSavingsFundPreviewShowsThePublishedFigureAtOneDecimal() {
    given(ocfSnapshotRepository.findByFundAndMonth("TKF100", OCTOBER.atDay(1)))
        .willReturn(Optional.of(snapshot(TKF100, COMPLETE_CHECKS, null)));
    givenTheFeeOnThePublicationDate(TKF100);

    var preview = service.preview(TKF100, OCTOBER, NOVEMBER_1);

    assertThat(preview)
        .isEqualTo(
            new OcfPublicationPreview(
                "TKF100",
                OCTOBER,
                2,
                true,
                COMPLETE_CHECKS,
                OcfCalculationFingerprint.of(snapshot(TKF100, COMPLETE_CHECKS, null)),
                CALCULATED_OCF,
                FEE_IN_THE_CALCULATION,
                NOVEMBER_1,
                FEE_ON_THE_PUBLICATION_DATE,
                PUBLISHED_OCF,
                new BigDecimal("0.5"),
                1,
                null,
                null,
                false));
  }

  @Test
  void aMonthWithoutACalculationCannotBePreviewed() {
    assertThatThrownBy(() -> service.preview(TUK75, OCTOBER, NOVEMBER_1))
        .isInstanceOf(OcfPublicationRefusedException.class);
  }

  @Test
  void aDateWithoutAManagementFeeCannotBePublishedOn() {
    given(ocfSnapshotRepository.findByFundAndMonth("TUK75", OCTOBER.atDay(1)))
        .willReturn(Optional.of(snapshot(true, null)));

    assertThatThrownBy(() -> service.preview(TUK75, OCTOBER, NOVEMBER_1))
        .isInstanceOf(OcfPublicationRefusedException.class);
  }

  @Test
  void publishingRecordsTheFigureItsBasisTheFeeAndWhoPublishedItAndAnswersWithThatVersion() {
    var snapshot = snapshot(true, null);
    var published = snapshot(true, Instant.EPOCH);
    given(ocfSnapshotRepository.findVersion("TUK75", OCTOBER.atDay(1), 2))
        .willReturn(Optional.of(snapshot), Optional.of(published));
    givenTheFeeOnThePublicationDate();
    given(publicationRepository.recordFromCalculation(snapshot, publicationOf("portfolio-manager")))
        .willReturn(true);

    var answer = service.publish(request(snapshot, PUBLISHED_OCF));

    then(publicationRepository)
        .should()
        .recordFromCalculation(snapshot, publicationOf("portfolio-manager"));
    assertThat(answer).isEqualTo(previewOf(published, null, null));
    then(ocfSnapshotRepository).should(never()).findByFundAndMonth(any(), any());
  }

  @Test
  void aFigureOtherThanThePreviewedOneIsNotPublished() {
    givenTheStoredVersion(snapshot(true, null));
    givenTheFeeOnThePublicationDate();

    assertThatThrownBy(() -> service.publish(request(snapshot(true, null), CALCULATED_OCF)))
        .isInstanceOf(OcfPublicationRefusedException.class);
    then(publicationRepository).should(never()).recordFromCalculation(any(), any());
  }

  @Test
  void aCalculationRecalculatedSinceItWasPreviewedIsNotPublished() {
    var previewed = snapshot(false, null);
    givenTheStoredVersion(snapshot(TUK75, "{\"gaps\":[\"MANAGEMENT_FEE_RATE_MISSING\"]}", null));

    assertThatThrownBy(() -> service.publish(request(previewed, PUBLISHED_OCF)))
        .isInstanceOf(OcfPublicationRefusedException.class);
    then(publicationRepository).should(never()).recordFromCalculation(any(), any());
  }

  @Test
  void anAlreadyPublishedVersionIsNotPublishedAgain() {
    givenTheStoredVersion(snapshot(true, Instant.EPOCH));
    givenTheFeeOnThePublicationDate();

    assertThatThrownBy(() -> service.publish(request(snapshot(true, null), PUBLISHED_OCF)))
        .isInstanceOf(OcfPublicationRefusedException.class);
    then(publicationRepository).should(never()).recordFromCalculation(any(), any());
  }

  @Test
  void anIncompleteCalculationIsNotPublishedUntilTheFundManagerDecidesAnException() {
    givenTheStoredVersion(snapshot(false, null));
    givenTheFeeOnThePublicationDate();

    assertThatThrownBy(() -> service.publish(request(snapshot(false, null), PUBLISHED_OCF)))
        .isInstanceOf(OcfPublicationRefusedException.class);
    then(publicationRepository).should(never()).recordFromCalculation(any(), any());
  }

  @Test
  void anIncompleteCalculationTheFundManagerExceptedIsPublishedBySomeoneElse() {
    var snapshot = snapshot(false, null);
    givenTheStoredVersion(snapshot);
    givenTheFeeOnThePublicationDate();
    given(exceptionRepository.findForTheCurrentCalculation(snapshot))
        .willReturn(Optional.of(EXCEPTION));
    given(publicationRepository.recordFromCalculation(snapshot, publicationOf("portfolio-manager")))
        .willReturn(true);

    var answer = service.publish(request(snapshot, PUBLISHED_OCF));

    assertThat(answer.exception()).isEqualTo(EXCEPTION);
    then(publicationRepository)
        .should()
        .recordFromCalculation(snapshot, publicationOf("portfolio-manager"));
  }

  @Test
  void whoDecidedTheExceptionCannotAlsoPublishTheFigure() {
    var snapshot = snapshot(false, null);
    givenTheStoredVersion(snapshot);
    givenTheFeeOnThePublicationDate();
    given(exceptionRepository.findForTheCurrentCalculation(snapshot))
        .willReturn(Optional.of(EXCEPTION));

    assertThatThrownBy(() -> service.publish(request(snapshot, PUBLISHED_OCF, " Fund-Manager ")))
        .isInstanceOf(OcfPublicationRefusedException.class);
    then(publicationRepository).should(never()).recordFromCalculation(any(), any());
  }

  @Test
  void aKidAlreadyRecordedIsNotPublishedAgainFromACalculation() {
    givenTheStoredVersion(snapshot(true, null));
    givenTheFeeOnThePublicationDate();
    given(publicationRepository.isRecorded("TUK75", NOVEMBER_1, "KID 01.11.2026")).willReturn(true);

    assertThatThrownBy(() -> service.publish(request(snapshot(true, null), PUBLISHED_OCF)))
        .isInstanceOf(OcfPublicationRefusedException.class);
    then(publicationRepository).should(never()).recordFromCalculation(any(), any());
  }

  @Test
  void aFigureCannotTakeEffectInsideTheMonthItWasCalculatedFor() {
    var lastDayOfTheMonth = OCTOBER.atEndOfMonth();

    assertThatThrownBy(() -> service.preview(TUK75, OCTOBER, lastDayOfTheMonth))
        .isInstanceOf(OcfPublicationRefusedException.class);
    assertThatThrownBy(
            () ->
                service.publish(
                    new OcfPublicationRequest(
                        TUK75,
                        OCTOBER,
                        2,
                        OcfCalculationFingerprint.of(snapshot(true, null)),
                        lastDayOfTheMonth,
                        PUBLISHED_OCF,
                        "KID 31.10.2026",
                        "portfolio-manager",
                        "the monthly estimate")))
        .isInstanceOf(OcfPublicationRefusedException.class);
    then(ocfSnapshotRepository).shouldHaveNoInteractions();
  }

  @Test
  void aCalculationThatChangedSinceItWasReadIsNotPublished() {
    givenTheStoredVersion(snapshot(true, null));
    givenTheFeeOnThePublicationDate();
    given(publicationRepository.recordFromCalculation(any(), any())).willReturn(false);

    assertThatThrownBy(() -> service.publish(request(snapshot(true, null), PUBLISHED_OCF)))
        .isInstanceOf(OcfPublicationRefusedException.class);
  }

  @Test
  void anUnknownVersionCannotBePublished() {
    assertThatThrownBy(() -> service.publish(request(snapshot(true, null), PUBLISHED_OCF)))
        .isInstanceOf(OcfPublicationRefusedException.class);
  }

  @Test
  void aGivenExPostFigureIsRecordedWithItsPeriod() {
    var request =
        givenFigure(
            EX_POST, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), "KID 01.11.2026");
    var recorded =
        new KidFigure(7L, NOVEMBER_1, new BigDecimal("0.004638"), 2, EX_POST, "KID 01.11.2026");
    given(publicationRepository.recordGiven(OcfPublication.given(request))).willReturn(recorded);

    assertThat(service.publishGiven(request)).isEqualTo(recorded);
  }

  @Test
  void aKidAlreadyInForceIsRecordedAsAGivenEstimateWithoutAPeriod() {
    var request = givenFigure(MONTHLY_ESTIMATE, null, null, "KID 01.11.2026");
    var recorded =
        new KidFigure(
            7L, NOVEMBER_1, new BigDecimal("0.004638"), 2, MONTHLY_ESTIMATE, "KID 01.11.2026");
    given(publicationRepository.recordGiven(OcfPublication.given(request))).willReturn(recorded);

    assertThat(service.publishGiven(request)).isEqualTo(recorded);
  }

  @Test
  void anExPostFigureWithoutItsWholePeriodIsNotRecorded() {
    assertThatThrownBy(() -> service.publishGiven(givenFigure(EX_POST, null, null, "KID")))
        .isInstanceOf(OcfPublicationRefusedException.class);
    assertThatThrownBy(
            () -> service.publishGiven(givenFigure(EX_POST, LocalDate.of(2025, 1, 1), null, "KID")))
        .isInstanceOf(OcfPublicationRefusedException.class);
    then(publicationRepository).shouldHaveNoInteractions();
  }

  @Test
  void anEstimateNamingAnExPostPeriodIsNotRecorded() {
    assertThatThrownBy(
            () ->
                service.publishGiven(
                    givenFigure(MONTHLY_ESTIMATE, null, LocalDate.of(2025, 12, 31), "KID")))
        .isInstanceOf(OcfPublicationRefusedException.class);
    then(publicationRepository).shouldHaveNoInteractions();
  }

  @Test
  void anExPostPeriodEndingBeforeItStartsIsNotRecorded() {
    assertThatThrownBy(
            () ->
                service.publishGiven(
                    givenFigure(
                        EX_POST, LocalDate.of(2025, 12, 31), LocalDate.of(2025, 1, 1), "KID")))
        .isInstanceOf(OcfPublicationRefusedException.class);
    then(publicationRepository).shouldHaveNoInteractions();
  }

  @Test
  void aGivenKidAlreadyRecordedIsNotRecordedAgain() {
    given(publicationRepository.isRecorded("TUK75", NOVEMBER_1, "KID 01.11.2026")).willReturn(true);

    assertThatThrownBy(
            () -> service.publishGiven(givenFigure(MONTHLY_ESTIMATE, null, null, "KID 01.11.2026")))
        .isInstanceOf(OcfPublicationRefusedException.class);
    then(publicationRepository).should(never()).recordGiven(any());
  }

  private static OcfGivenPublicationRequest givenFigure(
      OcfPublicationBasis basis,
      @Nullable LocalDate periodStart,
      @Nullable LocalDate periodEnd,
      String publishedIn) {
    return new OcfGivenPublicationRequest(
        TUK75,
        NOVEMBER_1,
        new BigDecimal("0.004638"),
        2,
        basis,
        periodStart,
        periodEnd,
        publishedIn,
        "portfolio-manager",
        "the ex-post result differs from the estimate at the published precision");
  }
}
