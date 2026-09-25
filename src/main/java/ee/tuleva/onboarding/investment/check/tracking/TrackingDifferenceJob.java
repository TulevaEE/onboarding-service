package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;
import static ee.tuleva.onboarding.investment.JobRunSchedule.TRACKING_DIFFERENCE_GAP_FILL;
import static ee.tuleva.onboarding.investment.JobRunSchedule.TRACKING_DIFFERENCE_SEPTEMBER_NAV_CORRECTION_BACKFILL;
import static ee.tuleva.onboarding.investment.TrackingCheckType.BENCHMARK;

import ee.tuleva.onboarding.investment.event.RunTrackingDifferenceBackfillRequested;
import ee.tuleva.onboarding.investment.event.RunTrackingDifferenceCheckRequested;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile({"production", "staging"})
class TrackingDifferenceJob {

  static final int GAP_LOOKBACK_DAYS = 30;
  static final int DAYS_BACK_TO_BEFORE_THE_SEPTEMBER_NAV_CORRECTION = 30;

  private final TrackingDifferenceService trackingDifferenceService;
  private final TrackingDifferenceNotifier trackingDifferenceNotifier;

  @EventListener
  void onTrackingDifferenceCheckRequested(RunTrackingDifferenceCheckRequested event) {
    log.info("Starting ad-hoc tracking difference check");

    try {
      var results = trackingDifferenceService.runChecksForFunds(List.of(TulevaFund.values()));
      trackingDifferenceNotifier.notify(results);
      log.info("Tracking difference check completed: resultCount={}", results.size());
    } catch (TrackingDifferenceService.IncompletePriceDataException e) {
      trackingDifferenceNotifier.notifyRunIncomplete("TD check", FailureReason.of(e));
      trackingDifferenceNotifier.notify(e.completedResults());
      log.error("Tracking difference check incomplete", e);
    } catch (Exception e) {
      log.error("Tracking difference check failed", e);
      trackingDifferenceNotifier.notifyRunFailed("TD check", FailureReason.of(e));
    }
  }

  @Scheduled(cron = TRACKING_DIFFERENCE_GAP_FILL, zone = TIMEZONE)
  @SchedulerLock(
      name = "TrackingDifferenceDailyGapFill",
      lockAtMostFor = "2h",
      lockAtLeastFor = "1m")
  void fillTrackingDifferenceGaps() {
    log.info("Starting daily tracking difference gap fill");

    try {
      var run = trackingDifferenceService.fillGaps(GAP_LOOKBACK_DAYS);
      reportFailures(run.failures());
      reportGapFill(run);
      log.info(
          "Tracking difference gap fill completed: resultCount={}, failureCount={}",
          run.results().size(),
          run.failures().size());
    } catch (Exception e) {
      log.error("Tracking difference gap fill failed", e);
      trackingDifferenceNotifier.notifyRunFailed("TD daily gap fill", FailureReason.of(e));
    }
  }

  @EventListener
  void onTrackingDifferenceBackfillRequested(RunTrackingDifferenceBackfillRequested event) {
    backfill(event.daysBack());
  }

  @Scheduled(cron = TRACKING_DIFFERENCE_SEPTEMBER_NAV_CORRECTION_BACKFILL, zone = TIMEZONE)
  @SchedulerLock(
      name = "TrackingDifferenceSeptemberNavCorrectionBackfill",
      lockAtMostFor = "2h",
      lockAtLeastFor = "5m")
  void backfillAfterTheSeptemberNavCorrection() {
    backfill(DAYS_BACK_TO_BEFORE_THE_SEPTEMBER_NAV_CORRECTION);
  }

  private void backfill(int daysBack) {
    log.info("Starting tracking difference backfill: daysBack={}", daysBack);

    try {
      var results = trackingDifferenceService.backfillChecks(daysBack);
      trackingDifferenceNotifier.notifyBackfillSummary(daysBack, results);
      log.info("Tracking difference backfill completed: resultCount={}", results.size());
    } catch (TrackingDifferenceService.IncompletePriceDataException e) {
      trackingDifferenceNotifier.notifyRunIncomplete("TD backfill", FailureReason.of(e));
      trackingDifferenceNotifier.notifyIncompleteBackfillSummary(daysBack, e.completedResults());
      log.error("Tracking difference backfill incomplete", e);
    } catch (Exception e) {
      log.error("Tracking difference backfill failed", e);
      trackingDifferenceNotifier.notifyRunFailed("TD backfill", FailureReason.of(e));
    }
  }

  private void reportFailures(List<GapFailure> failures) {
    if (failures.isEmpty()) {
      return;
    }
    trackingDifferenceNotifier.notifyRunIncomplete(
        "TD daily gap fill", GapFailure.report(failures));
  }

  private void reportGapFill(GapFillRun run) {
    if (run.results().isEmpty()) {
      return;
    }
    if (coversMoreThanOneCheckDate(run.results()) || !run.recheckedStaleDates().isEmpty()) {
      trackingDifferenceNotifier.notifyGapFillSummary(run);
      return;
    }
    trackingDifferenceNotifier.notify(run.results());
  }

  private static boolean coversMoreThanOneCheckDate(List<TrackingDifferenceResult> results) {
    return results.stream()
            .filter(result -> result.checkType() != BENCHMARK)
            .map(TrackingDifferenceResult::checkDate)
            .distinct()
            .count()
        > 1;
  }
}
