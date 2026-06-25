package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import ee.tuleva.onboarding.investment.event.PipelineTracker;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NavTrackingDifferenceGateTest {

  @Mock private TrackingDifferenceService trackingDifferenceService;
  @Mock private TrackingDifferenceNotifier trackingDifferenceNotifier;
  @Mock private PipelineTracker pipelineTracker;

  @InjectMocks private NavTrackingDifferenceGate gate;

  private static final LocalDate NAV_DATE = LocalDate.of(2026, 3, 14);

  @Test
  void passes_whenNoBreaches() {
    var result =
        TrackingDifferenceResult.builder()
            .fund(TUK75)
            .checkDate(NAV_DATE)
            .checkType(TrackingCheckType.MODEL_PORTFOLIO)
            .trackingDifference(new BigDecimal("0.001"))
            .fundReturn(new BigDecimal("0.01"))
            .benchmarkReturn(new BigDecimal("0.009"))
            .breach(false)
            .consecutiveBreachDays(0)
            .consecutiveNetTd(ZERO)
            .securityAttributions(List.of())
            .cashDrag(ZERO)
            .feeDrag(ZERO)
            .residual(ZERO)
            .build();

    given(trackingDifferenceService.checkFund(TUK75, NAV_DATE)).willReturn(List.of(result));

    assertThat(gate.check(TUK75, NAV_DATE)).isEmpty();

    then(trackingDifferenceNotifier).should().notify(List.of(result));
  }

  @Test
  void fails_whenNavResidualBreachDetected() {
    var result =
        TrackingDifferenceResult.builder()
            .fund(TUK75)
            .checkDate(NAV_DATE)
            .checkType(TrackingCheckType.MODEL_PORTFOLIO)
            .trackingDifference(new BigDecimal("0.015"))
            .fundReturn(new BigDecimal("0.02"))
            .benchmarkReturn(new BigDecimal("0.005"))
            .breach(true)
            .impliedFundReturn(new BigDecimal("0.005"))
            .navResidual(new BigDecimal("0.015"))
            .navResidualBreach(true)
            .consecutiveBreachDays(1)
            .consecutiveNetTd(new BigDecimal("0.015"))
            .securityAttributions(List.of())
            .cashDrag(ZERO)
            .feeDrag(ZERO)
            .residual(ZERO)
            .build();

    given(trackingDifferenceService.checkFund(TUK75, NAV_DATE)).willReturn(List.of(result));

    var failure = gate.check(TUK75, NAV_DATE);
    assertThat(failure).isPresent();
    assertThat(failure.get()).contains("TD breach").contains("TUK75").contains("navResidual");

    then(trackingDifferenceNotifier).should().notify(List.of(result));
  }

  @Test
  void passes_whenModelTdBreachButNavResidualWithinLimits() {
    // Trade / model-switch day: fund-vs-model TD breaches, but the NAV matches the begin-of-day
    // holdings the fund actually held intraday, so navResidual is ~0 and the NAV report is not
    // blocked.
    var result =
        TrackingDifferenceResult.builder()
            .fund(TUK75)
            .checkDate(NAV_DATE)
            .checkType(TrackingCheckType.MODEL_PORTFOLIO)
            .trackingDifference(new BigDecimal("-0.0021"))
            .fundReturn(new BigDecimal("0.0023"))
            .benchmarkReturn(new BigDecimal("0.0044"))
            .breach(true)
            .impliedFundReturn(new BigDecimal("0.0023"))
            .navResidual(new BigDecimal("0.00001"))
            .navResidualBreach(false)
            .consecutiveBreachDays(1)
            .consecutiveNetTd(new BigDecimal("-0.0021"))
            .securityAttributions(List.of())
            .cashDrag(ZERO)
            .feeDrag(ZERO)
            .residual(ZERO)
            .build();

    given(trackingDifferenceService.checkFund(TUK75, NAV_DATE)).willReturn(List.of(result));

    assertThat(gate.check(TUK75, NAV_DATE)).isEmpty();

    then(trackingDifferenceNotifier).should().notify(List.of(result));
  }

  @Test
  void passes_whenOnlyBenchmarkBreach() {
    var result = breachResult(TrackingCheckType.BENCHMARK);

    given(trackingDifferenceService.checkFund(TUK75, NAV_DATE)).willReturn(List.of(result));

    assertThat(gate.check(TUK75, NAV_DATE)).isEmpty();

    then(trackingDifferenceNotifier).should().notify(List.of(result));
  }

  @Test
  void passes_whenOnlyBenchmarkModelBreach() {
    var result = breachResult(TrackingCheckType.BENCHMARK_MODEL);

    given(trackingDifferenceService.checkFund(TUK75, NAV_DATE)).willReturn(List.of(result));

    assertThat(gate.check(TUK75, NAV_DATE)).isEmpty();

    then(trackingDifferenceNotifier).should().notify(List.of(result));
  }

  private static TrackingDifferenceResult breachResult(TrackingCheckType checkType) {
    return TrackingDifferenceResult.builder()
        .fund(TUK75)
        .checkDate(NAV_DATE)
        .checkType(checkType)
        .trackingDifference(new BigDecimal("0.015"))
        .fundReturn(new BigDecimal("0.02"))
        .benchmarkReturn(new BigDecimal("0.005"))
        .breach(true)
        .consecutiveBreachDays(1)
        .consecutiveNetTd(new BigDecimal("0.015"))
        .securityAttributions(List.of())
        .cashDrag(ZERO)
        .feeDrag(ZERO)
        .residual(ZERO)
        .build();
  }

  @Test
  void passes_butAlertsCheckCouldNotRun_whenNoResults() {
    given(trackingDifferenceService.checkFund(TUK75, NAV_DATE)).willReturn(List.of());

    assertThat(gate.check(TUK75, NAV_DATE)).isEmpty();

    then(trackingDifferenceNotifier).should().notifyCheckCouldNotRun(TUK75, NAV_DATE);
    then(trackingDifferenceNotifier).should(org.mockito.Mockito.never()).notify(List.of());
  }

  @Test
  void passes_andAlertsCheckCouldNotRun_whenIncompletePriceDataWithNoResults() {
    given(trackingDifferenceService.checkFund(TUK75, NAV_DATE))
        .willThrow(
            new TrackingDifferenceService.IncompletePriceDataException(
                "missing prices", List.of()));

    assertThat(gate.check(TUK75, NAV_DATE)).isEmpty();

    then(trackingDifferenceNotifier).should().notifyCheckCouldNotRun(TUK75, NAV_DATE);
  }

  @Test
  void passes_andNotifiesPartialResults_whenIncompletePriceDataWithResults() {
    var partial =
        List.of(
            TrackingDifferenceResult.builder()
                .fund(TUK75)
                .checkDate(NAV_DATE)
                .checkType(TrackingCheckType.MODEL_PORTFOLIO)
                .trackingDifference(new BigDecimal("0.001"))
                .fundReturn(new BigDecimal("0.01"))
                .benchmarkReturn(new BigDecimal("0.009"))
                .breach(false)
                .consecutiveBreachDays(0)
                .consecutiveNetTd(ZERO)
                .securityAttributions(List.of())
                .cashDrag(ZERO)
                .feeDrag(ZERO)
                .residual(ZERO)
                .build());
    given(trackingDifferenceService.checkFund(TUK75, NAV_DATE))
        .willThrow(
            new TrackingDifferenceService.IncompletePriceDataException("missing prices", partial));

    assertThat(gate.check(TUK75, NAV_DATE)).isEmpty();

    then(trackingDifferenceNotifier).should().notify(partial);
  }

  @Test
  void passes_whenUnexpectedException() {
    given(trackingDifferenceService.checkFund(TUK75, NAV_DATE))
        .willThrow(new RuntimeException("unexpected"));

    assertThat(gate.check(TUK75, NAV_DATE)).isEmpty();
  }
}
