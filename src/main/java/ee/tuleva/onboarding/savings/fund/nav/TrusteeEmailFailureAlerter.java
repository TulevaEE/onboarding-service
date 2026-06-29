package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;

import ee.tuleva.onboarding.event.EventLog;
import ee.tuleva.onboarding.event.EventLogRepository;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.notification.email.EmailDeliveryFailedEvent;
import java.util.Map;
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
  private final EventLogRepository eventLogRepository;

  @EventListener
  void onEmailDeliveryFailed(EmailDeliveryFailedEvent event) {
    if (!TRUSTEE_EMAIL.equalsIgnoreCase(event.recipient())) {
      return;
    }

    var type = event.eventType().toUpperCase();
    if (eventLogRepository.existsByTypeAndPrincipal(type, event.mandrillMessageId())) {
      log.debug(
          "Trustee delivery failure already alerted, skipping: type={}, mandrillMessageId={}",
          type,
          event.mandrillMessageId());
      return;
    }

    eventLogRepository.save(
        EventLog.builder()
            .type(type)
            .principal(event.mandrillMessageId())
            .timestamp(event.timestamp())
            .data(Map.of("recipient", event.recipient()))
            .build());

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
