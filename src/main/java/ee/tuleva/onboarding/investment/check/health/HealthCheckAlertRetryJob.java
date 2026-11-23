package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.JobRunSchedule.HEALTH_CHECK_ALERT_RETRY;
import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONTENT_TOO_LARGE;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

@Slf4j
@Component
@RequiredArgsConstructor
class HealthCheckAlertRetryJob {

  private static final Duration DIRECT_POST_TIME_LONGER_THAN_SLACK_CONNECT_PLUS_READ_TIMEOUT =
      Duration.ofMinutes(2);
  private static final Duration RETRY_WINDOW = Duration.ofHours(24);
  private static final int SLACK_POSTS_THAT_FIT_THE_5_MINUTE_LOCK_AT_70_SECONDS_EACH = 4;
  private static final List<HttpStatus> SLACK_REFUSES_THE_PAYLOAD_ITSELF_WITH =
      List.of(BAD_REQUEST, CONTENT_TOO_LARGE);
  private static final String NOT_POSTED_WITHIN_THE_RETRY_WINDOW =
      "not posted within 24 hours of its state being found";

  private final OperationsNotificationService notificationService;
  private final HealthCheckAlertRepository alertRepository;
  private final Clock clock;

  @Scheduled(cron = HEALTH_CHECK_ALERT_RETRY, zone = TIMEZONE)
  @SchedulerLock(name = "HealthCheckAlertRetryJob", lockAtMostFor = "5m", lockAtLeastFor = "30s")
  void postAlertsNotYetPosted() {
    var now = clock.instant();
    var giveUpFoundBefore = now.minus(RETRY_WINDOW);
    var notPosted =
        alertRepository.findNotPostedFoundBefore(
            now.minus(DIRECT_POST_TIME_LONGER_THAN_SLACK_CONNECT_PLUS_READ_TIMEOUT));
    notPosted.stream()
        .filter(alert -> alert.foundBefore(giveUpFoundBefore))
        .forEach(alert -> giveUp(alert, NOT_POSTED_WITHIN_THE_RETRY_WINDOW));
    var oldestFirst =
        notPosted.stream()
            .filter(alert -> !alert.foundBefore(giveUpFoundBefore))
            .limit(SLACK_POSTS_THAT_FIT_THE_5_MINUTE_LOCK_AT_70_SECONDS_EACH)
            .toList();
    for (var alert : oldestFirst) {
      if (!postedOrRefused(alert)) {
        return;
      }
    }
  }

  private boolean postedOrRefused(HealthCheckAlert alert) {
    try {
      notificationService.sendMessage(alert.lateMessage(), INVESTMENT);
    } catch (HttpClientErrorException e) {
      if (isRefusalOfThePayloadItself(e)) {
        giveUp(alert, "Slack refused it with " + e.getStatusCode());
        return true;
      }
      keepForTheNextRun(alert, e);
      return false;
    } catch (RuntimeException e) {
      keepForTheNextRun(alert, e);
      return false;
    }
    markPosted(alert);
    return true;
  }

  private static boolean isRefusalOfThePayloadItself(HttpClientErrorException e) {
    return SLACK_REFUSES_THE_PAYLOAD_ITSELF_WITH.stream()
        .anyMatch(status -> status.isSameCodeAs(e.getStatusCode()));
  }

  private static void keepForTheNextRun(HealthCheckAlert alert, RuntimeException e) {
    log.warn(
        "Health check alert not posted to Slack, trying it and the alerts after it again next run:"
            + " alertId={}, provider={}, reportDate={}, foundAt={}, error={}",
        alert.id(),
        alert.provider(),
        alert.reportDate(),
        alert.foundAt(),
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

  private void giveUp(HealthCheckAlert alert, String why) {
    try {
      if (alertRepository.giveUp(alert.id())) {
        log.error(
            "Health check alert given up, never posted to Slack: why={}, alertId={}, provider={},"
                + " reportDate={}, foundAt={}",
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
