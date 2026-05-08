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
  private static final BigDecimal TODAY_NAV = new BigDecimal("1.50000");

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

    given(trackingDifferenceService.checkFund(TUK75, NAV_DATE, TODAY_NAV))
        .willReturn(List.of(result));

    assertThat(gate.check(TUK75, NAV_DATE, TODAY_NAV)).isEmpty();

    then(trackingDifferenceNotifier).should().notify(List.of(result));
  }

  @Test
  void fails_whenBreachDetected() {
    var result =
        TrackingDifferenceResult.builder()
            .fund(TUK75)
            .checkDate(NAV_DATE)
            .checkType(TrackingCheckType.MODEL_PORTFOLIO)
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

    given(trackingDifferenceService.checkFund(TUK75, NAV_DATE, TODAY_NAV))
        .willReturn(List.of(result));

    var failure = gate.check(TUK75, NAV_DATE, TODAY_NAV);
    assertThat(failure).isPresent();
    assertThat(failure.get()).contains("TD breach").contains("TUK75");

    then(trackingDifferenceNotifier).should().notify(List.of(result));
  }

  @Test
  void passes_whenNoResults() {
    given(trackingDifferenceService.checkFund(TUK75, NAV_DATE, TODAY_NAV)).willReturn(List.of());

    assertThat(gate.check(TUK75, NAV_DATE, TODAY_NAV)).isEmpty();

    then(trackingDifferenceNotifier).should().notify(List.of());
  }

  @Test
  void passes_andNotifiesPartialResults_whenIncompletePriceData() {
    var partialResults = List.<TrackingDifferenceResult>of();
    given(trackingDifferenceService.checkFund(TUK75, NAV_DATE, TODAY_NAV))
        .willThrow(
            new TrackingDifferenceService.IncompletePriceDataException(
                "missing prices", partialResults));

    assertThat(gate.check(TUK75, NAV_DATE, TODAY_NAV)).isEmpty();

    then(trackingDifferenceNotifier).should().notify(partialResults);
  }

  @Test
  void passes_whenUnexpectedException() {
    given(trackingDifferenceService.checkFund(TUK75, NAV_DATE, TODAY_NAV))
        .willThrow(new RuntimeException("unexpected"));

    assertThat(gate.check(TUK75, NAV_DATE, TODAY_NAV)).isEmpty();
  }
}
