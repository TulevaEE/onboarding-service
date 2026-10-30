package ee.tuleva.onboarding.investment.fees.ocf;

import static ee.tuleva.onboarding.investment.fees.FeeRateSource.FIXED;
import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import ee.tuleva.onboarding.investment.fees.FeeRate;
import ee.tuleva.onboarding.investment.fees.FeeRateRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OcfPublicationServiceTest {

  private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
  private static final LocalDate NOVEMBER_1 = LocalDate.of(2026, 11, 1);
  private static final BigDecimal CALCULATED_OCF = new BigDecimal("0.00280000");
  private static final BigDecimal FEE_IN_THE_CALCULATION = new BigDecimal("0.00200000");
  private static final BigDecimal FEE_ON_THE_PUBLICATION_DATE = new BigDecimal("0.00190000");
  private static final BigDecimal PUBLISHED_OCF = new BigDecimal("0.00270000");
  private static final KidFigure KID_BEFORE =
      new KidFigure(
          LocalDate.of(2026, 2, 1),
          1,
          LocalDate.of(2026, 3, 1),
          new BigDecimal("0.00280000"),
          "KID 02.03.2026");

  @Mock private OcfSnapshotRepository ocfSnapshotRepository;
  @Mock private FeeRateRepository feeRateRepository;

  @InjectMocks private OcfPublicationService service;

  private static OcfSnapshot snapshot(boolean complete, @Nullable Instant publishedAt) {
    return new OcfSnapshot(
        42L,
        "TUK75",
        OCTOBER.atDay(1),
        2,
        OcfMethodology.EX_ANTE_NET_ASSETS_V2,
        FEE_IN_THE_CALCULATION,
        BigDecimal.ZERO,
        new BigDecimal("0.00080000"),
        new BigDecimal("0.00080000"),
        new BigDecimal("0.00080000"),
        RebateBasis.NET,
        BigDecimal.ZERO,
        CALCULATED_OCF,
        complete,
        complete ? "{\"gaps\":[]}" : "{\"gaps\":[\"MONTH_END_NAV_NOT_PUBLISHED\"]}",
        publishedAt,
        publishedAt == null ? null : "KID 2026",
        new OcfAudit(
            null, null, null, null, null, null, null, null, null, null, null, null, null, null,
            null));
  }

  private void givenTheFeeOnThePublicationDate() {
    given(feeRateRepository.findValidRate(TUK75, MANAGEMENT, NOVEMBER_1))
        .willReturn(
            Optional.of(
                new FeeRate(
                    9L, TUK75, MANAGEMENT, FEE_ON_THE_PUBLICATION_DATE, FIXED, NOVEMBER_1, null)));
  }

  private static OcfPublicationRequest request(
      OcfSnapshot previewed,
      BigDecimal publishedOcf,
      @Nullable String approvedBy,
      @Nullable String exceptionReason) {
    return new OcfPublicationRequest(
        TUK75,
        OCTOBER,
        2,
        OcfCalculationFingerprint.of(previewed),
        NOVEMBER_1,
        publishedOcf,
        "KID 01.11.2026",
        "portfolio-manager",
        approvedBy,
        exceptionReason);
  }

  @Test
  void thePreviewPublishesTheCalculationWithTheFeeInForceOnThePublicationDate() {
    given(ocfSnapshotRepository.findByFundAndMonth("TUK75", OCTOBER.atDay(1)))
        .willReturn(Optional.of(snapshot(true, null)));
    givenTheFeeOnThePublicationDate();
    given(ocfSnapshotRepository.findPublishedInForceOn("TUK75", NOVEMBER_1.minusDays(1)))
        .willReturn(Optional.of(KID_BEFORE));

    var preview = service.preview(TUK75, OCTOBER, NOVEMBER_1);

    assertThat(preview)
        .isEqualTo(
            new OcfPublicationPreview(
                "TUK75",
                OCTOBER,
                2,
                true,
                "{\"gaps\":[]}",
                OcfCalculationFingerprint.of(snapshot(true, null)),
                CALCULATED_OCF,
                FEE_IN_THE_CALCULATION,
                NOVEMBER_1,
                FEE_ON_THE_PUBLICATION_DATE,
                PUBLISHED_OCF,
                new BigDecimal("0.27"),
                KID_BEFORE,
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
  void publishingRecordsTheFigureTheFeeAndWhoPublishedItAndAnswersWithThatVersion() {
    var snapshot = snapshot(true, null);
    given(ocfSnapshotRepository.findVersion("TUK75", OCTOBER.atDay(1), 2))
        .willReturn(Optional.of(snapshot), Optional.of(snapshot(true, Instant.EPOCH)));
    givenTheFeeOnThePublicationDate();
    var publication =
        new OcfPublication(
            NOVEMBER_1,
            PUBLISHED_OCF,
            FEE_ON_THE_PUBLICATION_DATE,
            9L,
            "KID 01.11.2026",
            "portfolio-manager",
            null,
            null);
    given(ocfSnapshotRepository.recordPublication(snapshot, publication)).willReturn(true);

    var answer = service.publish(request(snapshot(true, null), PUBLISHED_OCF, null, null));

    then(ocfSnapshotRepository).should().recordPublication(snapshot, publication);
    assertThat(answer)
        .isEqualTo(
            new OcfPublicationPreview(
                "TUK75",
                OCTOBER,
                2,
                true,
                "{\"gaps\":[]}",
                OcfCalculationFingerprint.of(snapshot(true, null)),
                CALCULATED_OCF,
                FEE_IN_THE_CALCULATION,
                NOVEMBER_1,
                FEE_ON_THE_PUBLICATION_DATE,
                PUBLISHED_OCF,
                new BigDecimal("0.27"),
                null,
                true));
    then(ocfSnapshotRepository).should(never()).findByFundAndMonth(any(), any());
  }

  @Test
  void aFigureOtherThanThePreviewedOneIsNotPublished() {
    given(ocfSnapshotRepository.findVersion("TUK75", OCTOBER.atDay(1), 2))
        .willReturn(Optional.of(snapshot(true, null)));
    givenTheFeeOnThePublicationDate();

    assertThatThrownBy(
            () -> service.publish(request(snapshot(true, null), CALCULATED_OCF, null, null)))
        .isInstanceOf(OcfPublicationRefusedException.class);
    then(ocfSnapshotRepository).should(never()).recordPublication(any(), any());
  }

  @Test
  void anExceptionIsNotPublishedOnceTheGapsChangedSinceThePreviewTheApproversSaw() {
    var previewed = snapshot(false, null);
    var recalculated =
        new OcfSnapshot(
            42L,
            "TUK75",
            OCTOBER.atDay(1),
            2,
            OcfMethodology.EX_ANTE_NET_ASSETS_V2,
            FEE_IN_THE_CALCULATION,
            BigDecimal.ZERO,
            new BigDecimal("0.00080000"),
            new BigDecimal("0.00080000"),
            new BigDecimal("0.00080000"),
            RebateBasis.NET,
            BigDecimal.ZERO,
            CALCULATED_OCF,
            false,
            "{\"gaps\":[\"MANAGEMENT_FEE_RATE_MISSING\"]}",
            null,
            null,
            previewed.audit());
    given(ocfSnapshotRepository.findVersion("TUK75", OCTOBER.atDay(1), 2))
        .willReturn(Optional.of(recalculated));
    givenTheFeeOnThePublicationDate();

    assertThatThrownBy(
            () ->
                service.publish(
                    request(previewed, PUBLISHED_OCF, "fund-manager", "board decision")))
        .isInstanceOf(OcfPublicationRefusedException.class);
    then(ocfSnapshotRepository).should(never()).recordPublication(any(), any());
  }

  @Test
  void anAlreadyPublishedVersionIsNotPublishedAgain() {
    given(ocfSnapshotRepository.findVersion("TUK75", OCTOBER.atDay(1), 2))
        .willReturn(Optional.of(snapshot(true, Instant.EPOCH)));
    givenTheFeeOnThePublicationDate();

    assertThatThrownBy(
            () -> service.publish(request(snapshot(true, null), PUBLISHED_OCF, null, null)))
        .isInstanceOf(OcfPublicationRefusedException.class);
    then(ocfSnapshotRepository).should(never()).recordPublication(any(), any());
  }

  @Test
  void anIncompleteCalculationNeedsAnExceptionReasonAndASecondApprover() {
    given(ocfSnapshotRepository.findVersion("TUK75", OCTOBER.atDay(1), 2))
        .willReturn(Optional.of(snapshot(false, null)));
    givenTheFeeOnThePublicationDate();

    assertThatThrownBy(
            () ->
                service.publish(
                    request(snapshot(false, null), PUBLISHED_OCF, null, "board decision")))
        .isInstanceOf(OcfPublicationRefusedException.class);
    assertThatThrownBy(
            () ->
                service.publish(request(snapshot(false, null), PUBLISHED_OCF, "fund-manager", " ")))
        .isInstanceOf(OcfPublicationRefusedException.class);
    then(ocfSnapshotRepository).should(never()).recordPublication(any(), any());
  }

  @Test
  void anIncompleteCalculationWithAReasonAndASecondApproverIsPublishedAsAnException() {
    var snapshot = snapshot(false, null);
    given(ocfSnapshotRepository.findVersion("TUK75", OCTOBER.atDay(1), 2))
        .willReturn(Optional.of(snapshot));
    givenTheFeeOnThePublicationDate();
    given(ocfSnapshotRepository.recordPublication(any(), any())).willReturn(true);

    service.publish(request(snapshot, PUBLISHED_OCF, "fund-manager", "board decision"));

    then(ocfSnapshotRepository)
        .should()
        .recordPublication(
            snapshot,
            new OcfPublication(
                NOVEMBER_1,
                PUBLISHED_OCF,
                FEE_ON_THE_PUBLICATION_DATE,
                9L,
                "KID 01.11.2026",
                "portfolio-manager",
                "fund-manager",
                "board decision"));
  }

  @Test
  void thePublisherCannotBeTheSecondApproverOfAnException() {
    given(ocfSnapshotRepository.findVersion("TUK75", OCTOBER.atDay(1), 2))
        .willReturn(Optional.of(snapshot(false, null)));
    givenTheFeeOnThePublicationDate();

    assertThatThrownBy(
            () ->
                service.publish(
                    request(
                        snapshot(false, null),
                        PUBLISHED_OCF,
                        " Portfolio-Manager ",
                        "board decision")))
        .isInstanceOf(OcfPublicationRefusedException.class);
    then(ocfSnapshotRepository).should(never()).recordPublication(any(), any());
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
                        null,
                        null)))
        .isInstanceOf(OcfPublicationRefusedException.class);
    then(ocfSnapshotRepository).shouldHaveNoInteractions();
  }

  @Test
  void aCompleteCalculationIsNotPublishedAsAnException() {
    given(ocfSnapshotRepository.findVersion("TUK75", OCTOBER.atDay(1), 2))
        .willReturn(Optional.of(snapshot(true, null)));
    givenTheFeeOnThePublicationDate();

    assertThatThrownBy(
            () ->
                service.publish(
                    request(snapshot(true, null), PUBLISHED_OCF, "fund-manager", "board decision")))
        .isInstanceOf(OcfPublicationRefusedException.class);
  }

  @Test
  void aCalculationThatChangedSinceItWasReadIsNotPublished() {
    given(ocfSnapshotRepository.findVersion("TUK75", OCTOBER.atDay(1), 2))
        .willReturn(Optional.of(snapshot(true, null)));
    givenTheFeeOnThePublicationDate();
    given(ocfSnapshotRepository.recordPublication(any(), any())).willReturn(false);

    assertThatThrownBy(
            () -> service.publish(request(snapshot(true, null), PUBLISHED_OCF, null, null)))
        .isInstanceOf(OcfPublicationRefusedException.class);
  }

  @Test
  void anUnknownVersionCannotBePublished() {
    assertThatThrownBy(
            () -> service.publish(request(snapshot(true, null), PUBLISHED_OCF, null, null)))
        .isInstanceOf(OcfPublicationRefusedException.class);
  }
}
