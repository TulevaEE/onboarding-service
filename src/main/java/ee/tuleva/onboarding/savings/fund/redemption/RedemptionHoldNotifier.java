package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.AML;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.FROZEN;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
class RedemptionHoldNotifier {

  private final OperationsNotificationService notificationService;

  boolean notifyHold(RedemptionRequest request) {
    return request.getStatus() == FROZEN ? notifyFrozen(request) : notifyPayoutHold(request);
  }

  boolean notifyFrozen(RedemptionRequest request) {
    return send(
        ("AML: SANCTIONS HIT, redemption frozen: id=%s, amount=%s EUR. "
                + "The order is not executed until released: POST /admin/redemptions/%s/release")
            .formatted(
                request.getId(), request.getRequestedAmount().toPlainString(), request.getId()));
  }

  boolean notifyPayoutHold(RedemptionRequest request) {
    return send(
        ("AML: redemption will be executed but the payout is held for review: "
                + "id=%s, amount=%s EUR, reason=%s. Release: POST /admin/redemptions/%s/release")
            .formatted(
                request.getId(),
                request.getRequestedAmount().toPlainString(),
                request.getHoldReason(),
                request.getId()));
  }

  boolean notifyPayoutHeldAtPricing(RedemptionRequest request) {
    return send(
        "AML: redemption priced, payout held for review: id=%s, cashAmount=%s EUR, NAV=%s"
            .formatted(request.getId(), request.getCashAmount(), request.getNavPerUnit()));
  }

  boolean notifyReleased(UUID requestId, String by) {
    return send("AML: redemption released: id=%s, by=%s".formatted(requestId, by));
  }

  boolean notifyUnscreened(int count) {
    return send(
        "AML: %d redemption request(s) unscreened for over an hour, is the screening service down?"
            .formatted(count));
  }

  private boolean send(String message) {
    try {
      notificationService.sendMessage(message, AML);
      return true;
    } catch (RuntimeException e) {
      log.error("Failed to notify AML channel: {}", message, e);
      return false;
    }
  }
}
