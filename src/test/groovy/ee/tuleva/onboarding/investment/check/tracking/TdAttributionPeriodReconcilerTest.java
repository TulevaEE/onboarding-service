package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.TrackingCheckType.MODEL_PORTFOLIO;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TdAttributionPeriodReconcilerTest {

  private static final LocalDate PERIOD_START = LocalDate.of(2026, 4, 1);
  private static final LocalDate PERIOD_END = LocalDate.of(2026, 4, 30);
  private static final LocalDate FRIDAY = LocalDate.of(2026, 4, 10);
  private static final LocalDate MONDAY = LocalDate.of(2026, 4, 13);
  private static final LocalDate AFTER_THE_PERIOD = LocalDate.of(2026, 5, 5);

  @Mock TrackingDifferenceService trackingDifferenceService;
  @Mock TrackingDifferenceNotifier notifier;

  @InjectMocks TdAttributionPeriodReconciler reconciler;

  @Test
  void aPeriodWithNothingStaleOrUnfilledPassesWithoutAMessage() {
    given(trackingDifferenceService.reconcileSince(TUK75, PERIOD_START))
        .willReturn(GapFillRun.NOTHING_TO_FILL);

    assertThatCode(() -> reconciler.reconcile(TUK75, PERIOD_START, PERIOD_END))
        .doesNotThrowAnyException();

    then(notifier).shouldHaveNoInteractions();
  }

  @Test
  void datesRewrittenBeforeTheAttributionAreReportedInOneSummary() {
    var run =
        new GapFillRun(
            List.of(result(FRIDAY), result(MONDAY)),
            List.of(),
            Map.of(TUK75, List.of(FRIDAY, MONDAY)));
    given(trackingDifferenceService.reconcileSince(TUK75, PERIOD_START)).willReturn(run);

    reconciler.reconcile(TUK75, PERIOD_START, PERIOD_END);

    then(notifier).should().notifyGapFillSummary(run);
    then(notifier).should(never()).notifyAttributionNotWritten(any(), any(), any(), any());
  }

  @Test
  void aStaleDateInThePeriodThatCouldNotBeRecheckedStopsTheAttributionAndNamesTheDates() {
    var gap = new GapFailure(FRIDAY, "fund=TUK75, missingIsins=[IE00MISSING1]", 20, MONDAY);
    given(trackingDifferenceService.reconcileSince(TUK75, PERIOD_START))
        .willReturn(
            new GapFillRun(List.of(), List.of(gap), Map.of(TUK75, List.of(FRIDAY, MONDAY))));

    assertThatThrownBy(() -> reconciler.reconcile(TUK75, PERIOD_START, PERIOD_END))
        .isInstanceOf(IllegalStateException.class);

    then(notifier)
        .should()
        .notifyAttributionNotWritten(TUK75, PERIOD_START, PERIOD_END, List.of(FRIDAY, MONDAY));
    then(notifier).should(never()).notifyGapFillSummary(any());
  }

  @Test
  void onlyTheStaleDatesLeftUncheckedStopTheAttributionAndTheRewrittenOnesAreStillReported() {
    var run =
        new GapFillRun(List.of(result(FRIDAY)), List.of(), Map.of(TUK75, List.of(FRIDAY, MONDAY)));
    given(trackingDifferenceService.reconcileSince(TUK75, PERIOD_START)).willReturn(run);

    assertThatThrownBy(() -> reconciler.reconcile(TUK75, PERIOD_START, PERIOD_END))
        .isInstanceOf(IllegalStateException.class);

    then(notifier).should().notifyGapFillSummary(run);
    then(notifier)
        .should()
        .notifyAttributionNotWritten(TUK75, PERIOD_START, PERIOD_END, List.of(MONDAY));
  }

  @Test
  void aStaleDateAfterThePeriodDoesNotStopItsAttribution() {
    given(trackingDifferenceService.reconcileSince(TUK75, PERIOD_START))
        .willReturn(new GapFillRun(List.of(), List.of(), Map.of(TUK75, List.of(AFTER_THE_PERIOD))));

    assertThatCode(() -> reconciler.reconcile(TUK75, PERIOD_START, PERIOD_END))
        .doesNotThrowAnyException();

    then(notifier).shouldHaveNoInteractions();
  }

  private static TrackingDifferenceResult result(LocalDate checkDate) {
    return TrackingDifferenceResult.builder()
        .fund(TUK75)
        .checkDate(checkDate)
        .checkType(MODEL_PORTFOLIO)
        .trackingDifference(ZERO)
        .fundReturn(ZERO)
        .benchmarkReturn(ZERO)
        .breach(false)
        .build();
  }
}
