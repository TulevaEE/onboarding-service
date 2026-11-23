package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.investment.report.ReportProvider;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class HealthCheckAlertSender {

  private final OperationsNotificationService notificationService;
  private final HealthCheckAlertRepository alertRepository;
  private final Clock clock;

  boolean send(ReportProvider provider, LocalDate reportDate, String message) {
    var foundAt = clock.instant();
    var alertId = recorded(provider, reportDate, message, foundAt);
    if (!postedToSlack(provider, reportDate, message)) {
      if (alertId.isEmpty()) {
        log.error(
            "Health check alert neither recorded nor posted to Slack, it is lost:"
                + " provider={}, reportDate={}, foundAt={}, message={}",
            provider,
            reportDate,
            foundAt,
            message);
      }
      return alertId.isPresent();
    }
    alertId.ifPresent(this::markPosted);
    return true;
  }

  private Optional<Long> recorded(
      ReportProvider provider, LocalDate reportDate, String message, Instant foundAt) {
    try {
      return Optional.of(alertRepository.recordNotPosted(provider, reportDate, message, foundAt));
    } catch (RuntimeException e) {
      log.warn(
          "Health check alert not recorded, so a failed Slack post is not retried:"
              + " provider={}, reportDate={}, error={}",
          provider,
          reportDate,
          e.toString());
      return Optional.empty();
    }
  }

  private boolean postedToSlack(ReportProvider provider, LocalDate reportDate, String message) {
    try {
      notificationService.sendMessage(message, INVESTMENT);
      return true;
    } catch (RuntimeException e) {
      log.warn(
          "Health check alert not posted to Slack, the retry run posts it if it was recorded:"
              + " provider={}, reportDate={}, error={}",
          provider,
          reportDate,
          e.toString());
      return false;
    }
  }

  private void markPosted(long alertId) {
    try {
      alertRepository.markPosted(alertId, clock.instant());
    } catch (RuntimeException e) {
      log.warn(
          "Health check alert posted to Slack but not marked as posted, the retry run may post it"
              + " again: alertId={}, error={}",
          alertId,
          e.toString());
    }
  }
}
