package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;
import static ee.tuleva.onboarding.investment.JobRunSchedule.TRACKING_DIFFERENCE_CHECK;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile({"production", "staging"})
public class TrackingDifferenceJob {

  private final TrackingDifferenceService trackingDifferenceService;
  private final TrackingDifferenceNotifier trackingDifferenceNotifier;

  @Scheduled(cron = TRACKING_DIFFERENCE_CHECK, zone = TIMEZONE)
  @SchedulerLock(name = "TrackingDifferenceJob", lockAtMostFor = "30m", lockAtLeastFor = "5m")
  void runTrackingDifferenceChecks() {
    log.info("Starting tracking difference check");

    try {
      var results = trackingDifferenceService.runChecks();
      trackingDifferenceNotifier.notify(results);

      log.info("Tracking difference check completed: resultCount={}", results.size());
    } catch (Exception e) {
      log.error("Tracking difference check failed", e);
    }
  }
}
