package ee.tuleva.onboarding.investment.check.tracking;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class TdAttributionPeriodReconciler {

  private final TrackingDifferenceService trackingDifferenceService;
  private final TrackingDifferenceNotifier notifier;

  void reconcile(TulevaFund fund, LocalDate periodStart, LocalDate periodEnd) {
    var run = trackingDifferenceService.reconcileSince(fund, periodStart);
    if (!run.results().isEmpty()) {
      notifier.notifyGapFillSummary(run);
    }
    var staleInPeriod = staleDatesInPeriod(run.staleDatesNotRechecked(fund), periodEnd);
    if (!staleInPeriod.isEmpty()) {
      notifier.notifyAttributionNotWritten(fund, periodStart, periodEnd, staleInPeriod);
      throw new IllegalStateException(
          ("TD attribution not written, stale tracking-difference events could not be rechecked:"
                  + " fund=%s, periodStart=%s, periodEnd=%s, checkDates=%s")
              .formatted(fund, periodStart, periodEnd, staleInPeriod));
    }
  }

  private static List<LocalDate> staleDatesInPeriod(
      List<LocalDate> staleDates, LocalDate periodEnd) {
    return staleDates.stream().filter(checkDate -> !checkDate.isAfter(periodEnd)).toList();
  }
}
