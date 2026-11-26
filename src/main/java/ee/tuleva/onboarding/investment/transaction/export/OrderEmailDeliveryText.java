package ee.tuleva.onboarding.investment.transaction.export;

import static ee.tuleva.onboarding.investment.transaction.export.OrderEmail.UUID;
import static ee.tuleva.onboarding.investment.transaction.export.OrderEmailSlackText.SEND_THE_UUID_FILE_BY_HAND_THE_ORDERS_WENT_APART;
import static ee.tuleva.onboarding.investment.transaction.export.OrderEmailSlackText.icon;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;

import ee.tuleva.onboarding.notification.OperationsNotificationService.Severity;
import ee.tuleva.onboarding.notification.email.EmailDeliveryFailedEvent;
import java.util.Optional;

final class OrderEmailDeliveryText {

  private static final String DEFERRAL = "deferral";
  private static final String ANY_ORDER_EMAIL = "Order ";

  private OrderEmailDeliveryText() {}

  static String deliveryFailure(EmailDeliveryFailedEvent event) {
    var email = OrderEmail.taggedIn(event.tags());
    var prefix = email.map(OrderEmailSlackText::prefix).orElse(ANY_ORDER_EMAIL);
    if (isDeferral(event)) {
      var wait =
          isUuid(email)
              ? "What to do: nothing yet, Mandrill keeps trying."
                  + " If the email bounces, another message follows."
              : "What to do: Mandrill keeps trying. If the desk hasn't confirmed the orders"
                  + " before the cut-off, call the desk. Send by hand only if the desk agrees to"
                  + " leave the late original unexecuted.";
      return "🟠 %semail delivery delayed: %s\n%s\n%s"
          .formatted(prefix, event.recipient(), eventLine(event), wait);
    }
    var action =
        isUuid(email)
            ? SEND_THE_UUID_FILE_BY_HAND_THE_ORDERS_WENT_APART
            : "What to do: if this address is a copy, don't send the orders again. Otherwise call"
                + " the desk and send the file by hand only if the desk confirms the orders did"
                + " not arrive.";
    return "%s %semail NOT delivered: %s\n%s\n%s"
        .formatted(
            icon(deliveryFailureSeverity(event)),
            prefix,
            event.recipient(),
            eventLine(event),
            action);
  }

  static Severity deliveryFailureSeverity(EmailDeliveryFailedEvent event) {
    return isDeferral(event) || isUuid(OrderEmail.taggedIn(event.tags())) ? WARNING : ERROR;
  }

  private static boolean isDeferral(EmailDeliveryFailedEvent event) {
    return DEFERRAL.equals(event.eventType());
  }

  private static boolean isUuid(Optional<OrderEmail> email) {
    return email.filter(UUID::equals).isPresent();
  }

  private static String eventLine(EmailDeliveryFailedEvent event) {
    var cause =
        "%s: %s (id=%s)".formatted(event.eventType(), event.reason(), event.mandrillMessageId());
    String subject = event.subject();
    return subject == null ? cause : "\"%s\" · %s".formatted(subject, cause);
  }
}
