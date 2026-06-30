package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.AMBIGUOUS;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.ERROR;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.ORPHAN;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.investment.transaction.FtConfirmation;
import ee.tuleva.onboarding.investment.transaction.FtConfirmationResult;
import ee.tuleva.onboarding.investment.transaction.FtVerificationStatus;
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
  private final boolean registryAuthoritative;

  FtConfirmationDigest(
      OperationsNotificationService notificationService,
      @Value("${transaction-registry.ft-confirmation.registry-authoritative:false}")
          boolean registryAuthoritative) {
    this.notificationService = notificationService;
    this.registryAuthoritative = registryAuthoritative;
  }

  void publish(List<FtConfirmationOutcome> changedOutcomes) {
    List<FtConfirmationOutcome> actionable =
        changedOutcomes.stream().filter(this::shouldAlert).toList();
    if (actionable.isEmpty()) {
      return;
    }
    String message = buildMessage(actionable);
    try {
      notificationService.sendMessage(message, INVESTMENT);
    } catch (RuntimeException e) {
      log.error("Failed to send FT confirmation digest to Slack: error={}", e.getMessage(), e);
    }
  }

  private boolean shouldAlert(FtConfirmationOutcome outcome) {
    FtConfirmationResult result = outcome.result();
    if (result.quantityStatus() == ORPHAN) {
      return registryAuthoritative;
    }
    return isActionable(result.quantityStatus()) || isActionable(result.priceStatus());
  }

  private static boolean isActionable(FtVerificationStatus status) {
    return status == ERROR || status == AMBIGUOUS;
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
