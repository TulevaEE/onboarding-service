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
  private static final String ANY_ORDER_EMAIL = "Tehingukorralduse ";

  private OrderEmailDeliveryText() {}

  static String deliveryFailure(EmailDeliveryFailedEvent event) {
    var email = OrderEmail.taggedIn(event.tags());
    var prefix = email.map(OrderEmailSlackText::prefix).orElse(ANY_ORDER_EMAIL);
    if (isDeferral(event)) {
      var wait =
          isUuid(email)
              ? "Mida teha: praegu mitte midagi, Mandrill proovib ise uuesti."
                  + " Kui kiri põrkab tagasi, tuleb uus teade."
              : "Mida teha: Mandrill proovib ise uuesti. Kui saaja pole korraldusi enne"
                  + " lõikeaega kinnitanud, helista talle. Saada uuesti alles siis, kui ta ütleb,"
                  + " et need ei jõudnud.";
      return "🟠 %skirja kohaletoimetamine viibib: %s\n%s\n%s"
          .formatted(prefix, event.recipient(), eventLine(event), wait);
    }
    var action =
        isUuid(email)
            ? SEND_THE_UUID_FILE_BY_HAND_THE_ORDERS_WENT_APART
            : "Mida teha: helista saajale ja kontrolli, kas korraldused jõudsid."
                + " Kui ei jõudnud, saada fail käsitsi.";
    return "%s %skiri ei jõudnud kohale: %s\n%s\n%s"
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
    return subject == null ? cause : "„%s“ · %s".formatted(subject, cause);
  }
}
