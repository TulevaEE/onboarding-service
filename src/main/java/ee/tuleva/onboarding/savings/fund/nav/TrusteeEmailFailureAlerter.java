package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.notification.email.EmailDeliveryFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@NullMarked
@RequiredArgsConstructor
class TrusteeEmailFailureAlerter {

  static final String TRUSTEE_EMAIL = "trustee@seb.ee";

  private final OperationsNotificationService notificationService;

  @EventListener
  void onEmailDeliveryFailed(EmailDeliveryFailedEvent event) {
    if (!TRUSTEE_EMAIL.equalsIgnoreCase(event.recipient())) {
      return;
    }
    var message =
        "🔴 Trustee email delivery FAILED: recipient=%s, event=%s, subject=%s, reason=%s, messageId=%s"
            .formatted(
                event.recipient(),
                event.eventType(),
                event.subject(),
                event.reason(),
                event.mandrillMessageId());
    log.error(message);
    notificationService.sendMessage(message, SAVINGS);
  }
}
