package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.JobRunSchedule.HEALTH_CHECK_ALERT_RETRY;
import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;
import static ee.tuleva.onboarding.investment.check.health.SlackPostFailure.NO_ANSWER;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.Duration;
import java.util.function.LongPredicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class HealthCheckAlertRetryJob {

  private static final Duration RETRY_WINDOW = Duration.ofHours(24);
  private static final int SLACK_POSTS_AT_70_SECONDS_EACH_THAT_LEAVE_HALF_THE_10_MINUTE_LOCK = 4;
  private static final int UNANSWERED_POSTS_AFTER_WHICH_IT_MAY_ALREADY_BE_IN_THE_CHANNEL = 2;
  private static final String NOT_POSTED_WITHIN_THE_RETRY_WINDOW =
      "not posted within 24 hours of its state being found";
  private static final String SLACK_REFUSED_ITS_PAYLOAD = "Slack refused its payload";
  private static final String SLACK_GAVE_NO_ANSWER_TWICE =
      "Slack gave no answer twice, so it may already be in the channel";

  private final OperationsNotificationService notificationService;
  private final HealthCheckAlertRepository alertRepository;
  private final Clock clock;

  @Scheduled(cron = HEALTH_CHECK_ALERT_RETRY, zone = TIMEZONE)
  @SchedulerLock(name = "HealthCheckAlertRetryJob", lockAtMostFor = "10m", lockAtLeastFor = "30s")
  void postAlertsNotYetPosted() {
    var giveUpFoundBefore = clock.instant().minus(RETRY_WINDOW);
    var notPosted = alertRepository.findNotPosted();
    notPosted.stream()
        .filter(alert -> alert.foundBefore(giveUpFoundBefore))
        .forEach(
            alert -> giveUp(alert, NOT_POSTED_WITHIN_THE_RETRY_WINDOW, alertRepository::giveUp));
    notPosted.stream()
        .filter(alert -> !alert.foundBefore(giveUpFoundBefore))
        .limit(SLACK_POSTS_AT_70_SECONDS_EACH_THAT_LEAVE_HALF_THE_10_MINUTE_LOCK)
        .allMatch(this::postedOrGivenUp);
  }

  private boolean postedOrGivenUp(HealthCheckAlert alert) {
    try {
      notificationService.sendMessage(alert.lateMessage(), INVESTMENT);
    } catch (RuntimeException e) {
      var failure = SlackPostFailure.of(e);
      return switch (failure) {
        case PAYLOAD_REFUSED -> {
          giveUp(alert, SLACK_REFUSED_ITS_PAYLOAD + ": " + e, alertRepository::giveUp);
          yield true;
        }
        case NO_ANSWER -> {
          slackGaveNoAnswer(alert, e);
          yield false;
        }
        case NOT_TAKEN -> {
          keepForTheNextRun(alert, failure, e);
          yield false;
        }
      };
    }
    markPosted(alert);
    return true;
  }

  private void slackGaveNoAnswer(HealthCheckAlert alert, RuntimeException e) {
    if (alert.unansweredPosts() + 1
        >= UNANSWERED_POSTS_AFTER_WHICH_IT_MAY_ALREADY_BE_IN_THE_CHANNEL) {
      giveUp(alert, SLACK_GAVE_NO_ANSWER_TWICE, alertRepository::giveUpAfterUnansweredPost);
      return;
    }
    keepForTheNextRun(alert, NO_ANSWER, e);
    try {
      alertRepository.recordUnansweredPost(alert.id());
    } catch (RuntimeException dbError) {
      log.warn(
          "Health check alert Slack gave no answer to not counted, it may be posted more than"
              + " twice: alertId={}, error={}",
          alert.id(),
          dbError.toString());
    }
  }

  private static void keepForTheNextRun(
      HealthCheckAlert alert, SlackPostFailure failure, RuntimeException e) {
    log.warn(
        "Health check alert not posted to Slack, trying it and the alerts after it again next run:"
            + " alertId={}, provider={}, reportDate={}, foundAt={}, failure={}, error={}",
        alert.id(),
        alert.provider(),
        alert.reportDate(),
        alert.foundAt(),
        failure,
        e.toString());
  }

  private void markPosted(HealthCheckAlert alert) {
    try {
      alertRepository.markPosted(alert.id(), clock.instant());
    } catch (RuntimeException e) {
      log.warn(
          "Health check alert posted to Slack but not marked as posted, the next run may post it"
              + " again: alertId={}, error={}",
          alert.id(),
          e.toString());
    }
  }

  private static void giveUp(HealthCheckAlert alert, String why, LongPredicate giveUpRow) {
    try {
      if (giveUpRow.test(alert.id())) {
        log.error(
            "Health check alert given up, never confirmed posted to Slack: why={}, alertId={},"
                + " provider={}, reportDate={}, foundAt={}",
            why,
            alert.id(),
            alert.provider(),
            alert.reportDate(),
            alert.foundAt());
      }
    } catch (RuntimeException e) {
      log.warn(
          "Health check alert not marked as given up, trying again next run: alertId={}, error={}",
          alert.id(),
          e.toString());
    }
  }
}
