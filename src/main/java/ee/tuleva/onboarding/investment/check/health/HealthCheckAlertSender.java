package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.investment.report.ReportProvider;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class HealthCheckAlertSender {

  private static final int NO_POST_LEFT_UNANSWERED = 0;

  private final OperationsNotificationService notificationService;
  private final HealthCheckAlertRepository alertRepository;
  private final Clock clock;

  boolean send(ReportProvider provider, LocalDate reportDate, String message) {
    var foundAt = clock.instant();
    if (alertsAreWaiting()
        && recorded(provider, reportDate, message, foundAt, NO_POST_LEFT_UNANSWERED)) {
      return true;
    }
    try {
      notificationService.sendMessage(message, INVESTMENT);
      return true;
    } catch (RuntimeException e) {
      var failure = SlackPostFailure.of(e);
      log.warn(
          "Health check alert not posted to Slack, leaving it for the retry run:"
              + " provider={}, reportDate={}, failure={}, error={}",
          provider,
          reportDate,
          failure,
          e.toString());
      if (recorded(provider, reportDate, message, foundAt, failure.unansweredPosts())) {
        return true;
      }
      log.error(
          "Health check alert neither confirmed posted to Slack nor recorded for the retry run:"
              + " provider={}, reportDate={}, foundAt={}, failure={}, message={}",
          provider,
          reportDate,
          foundAt,
          failure,
          message);
      return false;
    }
  }

  private boolean alertsAreWaiting() {
    try {
      return alertRepository.anyNotPosted();
    } catch (RuntimeException e) {
      log.warn(
          "Health check alerts waiting for the retry run could not be read, posting right away:"
              + " error={}",
          e.toString());
      return false;
    }
  }

  private boolean recorded(
      ReportProvider provider,
      LocalDate reportDate,
      String message,
      Instant foundAt,
      int unansweredPosts) {
    try {
      alertRepository.recordNotPosted(provider, reportDate, message, foundAt, unansweredPosts);
      return true;
    } catch (RuntimeException e) {
      log.warn(
          "Health check alert not recorded for the retry run: provider={}, reportDate={},"
              + " error={}",
          provider,
          reportDate,
          e.toString());
      return false;
    }
  }
}
