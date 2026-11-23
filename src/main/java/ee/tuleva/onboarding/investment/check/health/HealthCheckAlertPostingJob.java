package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.JobRunSchedule.HEALTH_CHECK_ALERT_POSTING;
import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;
import static ee.tuleva.onboarding.investment.check.health.SlackPostFailure.NOT_SENT;
import static ee.tuleva.onboarding.investment.check.health.SlackPostFailure.UNCERTAIN;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class HealthCheckAlertPostingJob {

  private static final Duration RETRY_WINDOW = Duration.ofHours(24);
  private static final int SLACK_POSTS_AT_70_SECONDS_EACH_THAT_LEAVE_HALF_THE_10_MINUTE_LOCK = 4;
  private static final Duration FIRST_GAP_AFTER_AN_UNCERTAIN_POST = Duration.ofMinutes(1);
  private static final Duration LONGEST_GAP_SO_AN_UNCERTAIN_ALERT_IS_STILL_TRIED_EVERY_HOUR =
      Duration.ofMinutes(60);
  private static final String NOT_POSTED_WITHIN_THE_RETRY_WINDOW =
      "not posted within 24 hours of its state being found";

  private final OperationsNotificationService notificationService;
  private final HealthCheckAlertRepository alertRepository;
  private final Clock clock;

  @Scheduled(cron = HEALTH_CHECK_ALERT_POSTING, zone = TIMEZONE)
  @SchedulerLock(name = "HealthCheckAlertPostingJob", lockAtMostFor = "10m", lockAtLeastFor = "30s")
  void postAlertsNotYetPosted() {
    var giveUpFoundBefore = clock.instant().minus(RETRY_WINDOW);
    var notPosted = alertRepository.findNotPosted();
    notPosted.stream()
        .filter(alert -> alert.foundBefore(giveUpFoundBefore))
        .forEach(alert -> giveUp(alert, alert.attempts(), NOT_POSTED_WITHIN_THE_RETRY_WINDOW));
    notPosted.stream()
        .filter(alert -> !alert.foundBefore(giveUpFoundBefore))
        .limit(SLACK_POSTS_AT_70_SECONDS_EACH_THAT_LEAVE_HALF_THE_10_MINUTE_LOCK)
        .allMatch(this::postedOrGivenUpIfDue);
  }

  private boolean postedOrGivenUpIfDue(HealthCheckAlert alert) {
    var now = clock.instant();
    return isDue(alert, now) && postedOrGivenUp(alert, now);
  }

  private static boolean isDue(HealthCheckAlert alert, Instant now) {
    var lastAttemptAt = alert.lastAttemptAt();
    return lastAttemptAt == null || !now.isBefore(lastAttemptAt.plus(gapAfter(alert.attempts())));
  }

  private static Duration gapAfter(int attempts) {
    return Stream.iterate(
            FIRST_GAP_AFTER_AN_UNCERTAIN_POST,
            gap ->
                shorterOf(
                    gap.multipliedBy(2),
                    LONGEST_GAP_SO_AN_UNCERTAIN_ALERT_IS_STILL_TRIED_EVERY_HOUR))
        .skip(Math.max(attempts - 1, 0))
        .findFirst()
        .orElseThrow();
  }

  private static Duration shorterOf(Duration one, Duration other) {
    return one.compareTo(other) < 0 ? one : other;
  }

  private boolean postedOrGivenUp(HealthCheckAlert alert, Instant now) {
    if (!claimed(alert, now)) {
      return false;
    }
    try {
      notificationService.sendMessage(alert.messagePostedAt(now), INVESTMENT);
    } catch (RuntimeException e) {
      return afterAFailedPost(alert, e);
    }
    markPosted(alert);
    return true;
  }

  private boolean claimed(HealthCheckAlert alert, Instant now) {
    try {
      return alertRepository.claimAttempt(alert.id(), alert.attempts(), now);
    } catch (RuntimeException e) {
      log.warn(
          "Health check alert attempt not counted, so it is not posted this run: alertId={},"
              + " cause={}",
          alert.id(),
          mostSpecificCause(e));
      return false;
    }
  }

  private boolean afterAFailedPost(HealthCheckAlert alert, RuntimeException e) {
    var failure = SlackPostFailure.of(e);
    return switch (failure) {
      case NOT_SENT -> {
        refundAttempt(alert, e);
        yield false;
      }
      case PAYLOAD_REFUSED -> {
        giveUp(
            alert,
            alert.attempts() + 1,
            "Slack refused its payload: " + SlackPostFailure.describe(e));
        yield true;
      }
      case UNCERTAIN -> {
        log.warn(
            "Health check alert post may or may not have reached Slack, trying it and the alerts"
                + " after it again after a gap: alertId={}, provider={}, reportDate={},"
                + " foundAt={}, attempts={}, gap={}, failure={}, cause={}",
            alert.id(),
            alert.provider(),
            alert.reportDate(),
            alert.foundAt(),
            alert.attempts() + 1,
            gapAfter(alert.attempts() + 1),
            UNCERTAIN,
            SlackPostFailure.describe(e));
        yield false;
      }
    };
  }

  private void refundAttempt(HealthCheckAlert alert, RuntimeException e) {
    log.warn(
        "Health check alert not sent to Slack, trying it and the alerts after it again next run:"
            + " alertId={}, provider={}, reportDate={}, foundAt={}, failure={}, cause={}",
        alert.id(),
        alert.provider(),
        alert.reportDate(),
        alert.foundAt(),
        NOT_SENT,
        SlackPostFailure.describe(e));
    try {
      var earlierAttemptAt = alert.lastAttemptAt();
      if (earlierAttemptAt == null) {
        alertRepository.refundFirstAttempt(alert.id());
      } else {
        alertRepository.refundAttempt(alert.id(), alert.attempts() + 1, earlierAttemptAt);
      }
    } catch (RuntimeException dbError) {
      log.warn(
          "Health check alert attempt not refunded, so its next try waits a gap: alertId={},"
              + " cause={}",
          alert.id(),
          mostSpecificCause(dbError));
    }
  }

  private void markPosted(HealthCheckAlert alert) {
    try {
      alertRepository.markPosted(alert.id(), clock.instant());
    } catch (RuntimeException e) {
      log.warn(
          "Health check alert posted to Slack but not marked as posted, its attempt is counted so"
              + " it is posted again only after its gap: alertId={}, cause={}",
          alert.id(),
          mostSpecificCause(e));
    }
  }

  private void giveUp(HealthCheckAlert alert, int attempts, String why) {
    try {
      if (alertRepository.giveUp(alert.id())) {
        log.error(
            "Health check alert given up, never confirmed posted to Slack: why={}, alertId={},"
                + " provider={}, reportDate={}, foundAt={}, attempts={}",
            why,
            alert.id(),
            alert.provider(),
            alert.reportDate(),
            alert.foundAt(),
            attempts);
      }
    } catch (RuntimeException e) {
      log.warn(
          "Health check alert not marked as given up, trying again next run: alertId={},"
              + " cause={}",
          alert.id(),
          mostSpecificCause(e));
    }
  }

  private static String mostSpecificCause(RuntimeException e) {
    return NestedExceptionUtils.getMostSpecificCause(e).toString();
  }
}
