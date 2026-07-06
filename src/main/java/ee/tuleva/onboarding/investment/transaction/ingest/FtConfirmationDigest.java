package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.investment.transaction.FtConfirmation;
import ee.tuleva.onboarding.investment.transaction.FtConfirmationResult;
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

  void publish(List<FtConfirmationOutcome> outcomes) {
    if (!registryAuthoritative) {
      return;
    }
    List<FtConfirmationOutcome> unalerted =
        outcomes.stream()
            .filter(outcome -> outcome.result().isActionable())
            .distinct()
            .filter(
                outcome -> !auditRecorder.alreadyAlerted(outcome.confirmation(), outcome.result()))
            .toList();
    if (unalerted.isEmpty()) {
      return;
    }
    if (!send(buildMessage(unalerted))) {
      return;
    }
    unalerted.forEach(
        outcome -> auditRecorder.recordAlerted(outcome.confirmation(), outcome.result()));
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

  private static String buildMessage(List<FtConfirmationOutcome> actionable) {
    StringBuilder message = new StringBuilder();
    message
        .append("FT confirmation check: ")
        .append(actionable.size())
        .append(" issue(s) need attention");
    actionable.forEach(outcome -> message.append('\n').append(describe(outcome)));
    return message.toString();
  }

  private static String describe(FtConfirmationOutcome outcome) {
    FtConfirmation confirmation = outcome.confirmation();
    FtConfirmationResult result = outcome.result();
    if (result.isCancellation()) {
      return String.format(
          "FT broker cancellation: fund=%s, isin=%s, tradeDate=%s, quantity=%s"
              + " — verify and cancel in registry",
          confirmation.fund().name(),
          confirmation.isin(),
          confirmation.tradeDate(),
          confirmation.quantity().toPlainString());
    }
    return String.format(
        "FT issue: fund=%s, isin=%s, tradeDate=%s, quantity=%s, quantityStatus=%s, priceStatus=%s",
        confirmation.fund().name(),
        confirmation.isin(),
        confirmation.tradeDate(),
        confirmation.quantity().toPlainString(),
        result.quantityStatus(),
        result.priceStatus());
  }
}
