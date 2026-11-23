package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.investment.report.ReportProvider;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class HealthCheckAlertOutbox {

  private final OperationsNotificationService notificationService;
  private final HealthCheckAlertRepository alertRepository;
  private final Clock clock;

  boolean raise(ReportProvider provider, LocalDate reportDate, String message) {
    var foundAt = clock.instant();
    try {
      alertRepository.recordNotPosted(provider, reportDate, message, foundAt);
      return true;
    } catch (RuntimeException e) {
      log.warn(
          "Health check alert not saved for the posting job, posting it right away instead:"
              + " provider={}, reportDate={}, cause={}",
          provider,
          reportDate,
          NestedExceptionUtils.getMostSpecificCause(e).toString());
      return postedRightAway(provider, reportDate, message, foundAt);
    }
  }

  private boolean postedRightAway(
      ReportProvider provider, LocalDate reportDate, String message, Instant foundAt) {
    try {
      notificationService.sendMessage(message, INVESTMENT);
      return true;
    } catch (RuntimeException e) {
      log.error(
          "Health check alert neither saved nor posted to Slack, it is lost: provider={},"
              + " reportDate={}, foundAt={}, failure={}, cause={}, message={}",
          provider,
          reportDate,
          foundAt,
          SlackPostFailure.of(e),
          SlackPostFailure.describe(e),
          message);
      return false;
    }
  }
}
