package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.UNCHECKED;
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
    if (result.quantityStatus() == UNCHECKED) {
      return String.format(
          "FT confirmation could not be checked: fund=%s, isin=%s, tradeDate=%s, quantity=%s,"
              + " error=%s",
          confirmation.fund().name(),
          confirmation.isin(),
          confirmation.tradeDate(),
          confirmation.quantity().toPlainString(),
          result.details().get("verificationError"));
    }
    if (result.isOnlyThePriceMissing()) {
      return String.format(
          "FT price could not be checked: fund=%s, isin=%s, tradeDate=%s, quantity=%s, price=%s"
              + " — no closing price for the trade date by the end of %s; check the price against"
              + " the official close by hand",
          confirmation.fund().name(),
          confirmation.isin(),
          confirmation.tradeDate(),
          confirmation.quantity().toPlainString(),
          confirmation.grossPrice().toPlainString(),
          result.details().get("closingPriceDueBy"));
    }
    if (confirmation.isCancellation()) {
      return String.format(
          "FT cancelled a trade and sent no new confirmation of the same quantity: fund=%s,"
              + " isin=%s, tradeDate=%s,"
              + " quantity=%s — check with FT; if the trade did not happen, cancel the order in"
              + " the registry",
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
