package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;
import static ee.tuleva.onboarding.investment.JobRunSchedule.TRACKING_DIFFERENCE_DAILY;

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
      trackingDifferenceNotifier.notifyRunIncomplete("TD check", reasonOf(e));
      trackingDifferenceNotifier.notify(e.completedResults());
      log.error("Tracking difference check incomplete", e);
    } catch (Exception e) {
      log.error("Tracking difference check failed", e);
      trackingDifferenceNotifier.notifyRunFailed("TD check", reasonOf(e));
    }
  }

  @Scheduled(cron = TRACKING_DIFFERENCE_DAILY, zone = TIMEZONE)
  @SchedulerLock(
      name = "TrackingDifferenceDailyGapFill",
      lockAtMostFor = "30m",
      lockAtLeastFor = "1m")
  void fillTrackingDifferenceGaps() {
    log.info("Starting daily tracking difference gap fill");

    try {
      var results = trackingDifferenceService.fillGaps(GAP_LOOKBACK_DAYS);
      reportGapFill(results);
      log.info("Tracking difference gap fill completed: resultCount={}", results.size());
    } catch (TrackingDifferenceService.IncompletePriceDataException e) {
      trackingDifferenceNotifier.notifyRunIncomplete("TD daily gap fill", reasonOf(e));
      reportGapFill(e.completedResults());
      log.error("Tracking difference gap fill incomplete", e);
    } catch (Exception e) {
      log.error("Tracking difference gap fill failed", e);
      trackingDifferenceNotifier.notifyRunFailed("TD daily gap fill", reasonOf(e));
    }
  }

  @EventListener
  void onTrackingDifferenceBackfillRequested(RunTrackingDifferenceBackfillRequested event) {
    log.info("Starting tracking difference backfill: daysBack={}", event.daysBack());

    try {
      var results = trackingDifferenceService.backfillChecks(event.daysBack());
      trackingDifferenceNotifier.notifyBackfillSummary(event.daysBack(), results);
      log.info("Tracking difference backfill completed: resultCount={}", results.size());
    } catch (TrackingDifferenceService.IncompletePriceDataException e) {
      trackingDifferenceNotifier.notifyRunIncomplete("TD backfill", reasonOf(e));
      trackingDifferenceNotifier.notifyBackfillSummary(event.daysBack(), e.completedResults());
      log.error("Tracking difference backfill incomplete", e);
    } catch (Exception e) {
      log.error("Tracking difference backfill failed", e);
      trackingDifferenceNotifier.notifyRunFailed("TD backfill", reasonOf(e));
    }
  }

  // An evening with no gap to fill is the normal outcome, not a run that checked nothing - the
  // per-day notifier reports an empty list as "nothing actionable was checked", which on any day
  // the NAV publication already wrote the events is a false alarm. A fill covering more than one
  // date is a stretch of past days, and the per-day breach message would post each of them as if
  // it were today's.
  private void reportGapFill(List<TrackingDifferenceResult> results) {
    if (results.isEmpty()) {
      return;
    }
    if (results.stream().map(TrackingDifferenceResult::checkDate).distinct().count() > 1) {
      trackingDifferenceNotifier.notifyGapFillSummary(results);
      return;
    }
    trackingDifferenceNotifier.notify(results);
  }

  private static String reasonOf(Exception e) {
    return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
  }
}
