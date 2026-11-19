package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@NullMarked
@Slf4j
@Component
class FtConfirmationDigest {

  private final OperationsNotificationService notificationService;
  private final FtConfirmationAuditRecorder auditRecorder;
  private final boolean registryAuthoritative;

  FtConfirmationDigest(
      OperationsNotificationService notificationService,
      FtConfirmationAuditRecorder auditRecorder,
      @Value("${transaction-registry.ft-confirmation.registry-authoritative:false}")
          boolean registryAuthoritative) {
    this.notificationService = notificationService;
    this.auditRecorder = auditRecorder;
    this.registryAuthoritative = registryAuthoritative;
  }

  void publish(List<FtCheckOutcome> outcomes) {
    if (!registryAuthoritative) {
      return;
    }
    List<FtCheckOutcome> unalerted =
        outcomes.stream()
            .filter(outcome -> outcome.result().isActionable())
            .distinct()
            .filter(outcome -> !auditRecorder.alreadyAlerted(outcome))
            .toList();
    if (unalerted.isEmpty()) {
      return;
    }
    if (!send(buildMessage(unalerted))) {
      return;
    }
    unalerted.forEach(auditRecorder::recordAlerted);
  }

  private boolean send(String message) {
    try {
      notificationService.sendMessage(message, INVESTMENT);
      return true;
    } catch (RuntimeException e) {
      log.error("Failed to send FT confirmation digest to Slack: error={}", e.getMessage(), e);
      return false;
    }
  }

  private static String buildMessage(List<FtCheckOutcome> actionable) {
    StringBuilder message = new StringBuilder();
    message
        .append("FT confirmation check: ")
        .append(actionable.size())
        .append(" issue(s) need attention");
    actionable.forEach(outcome -> message.append('\n').append(outcome.describe()));
    return message.toString();
  }
}
