package ee.tuleva.onboarding.investment.transaction.export;

import static ee.tuleva.onboarding.investment.transaction.export.CustodianOrderMessageFactory.TRADE_ORDER_TAG;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.TRADING;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;
import static java.util.Locale.ROOT;
import static org.springframework.core.Ordered.HIGHEST_PRECEDENCE;

import ee.tuleva.onboarding.event.EventLog;
import ee.tuleva.onboarding.event.EventLogRepository;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.notification.email.EmailDeliveryFailedEvent;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class CustodianOrderEmailFailureAlerter {

  private static final String RECORDED_TYPE_PREFIX = "TRADE_ORDER_EMAIL_";
  private static final String DEFERRAL = "deferral";

  private final OperationsNotificationService notificationService;
  private final EventLogRepository eventLogRepository;

  @EventListener
  @Order(HIGHEST_PRECEDENCE)
  void onEmailDeliveryFailed(EmailDeliveryFailedEvent event) {
    if (!event.tags().contains(TRADE_ORDER_TAG)) {
      return;
    }
    var recordedType = RECORDED_TYPE_PREFIX + event.eventType().toUpperCase(ROOT);
    if (eventLogRepository.existsByTypeAndPrincipal(recordedType, event.mandrillMessageId())) {
      log.debug(
          "Order email delivery failure already alerted, skipping: type={}, mandrillMessageId={}",
          recordedType,
          event.mandrillMessageId());
      return;
    }
    log.error(
        "Order email delivery failed: recipient={}, event={}, reason={}, mandrillMessageId={}",
        event.recipient(),
        event.eventType(),
        event.reason(),
        event.mandrillMessageId());
    if (posted(event)) {
      eventLogRepository.save(
          EventLog.builder()
              .type(recordedType)
              .principal(event.mandrillMessageId())
              .timestamp(event.timestamp())
              .data(Map.of("recipient", event.recipient()))
              .build());
    }
  }

  private boolean posted(EmailDeliveryFailedEvent event) {
    var delayed = DEFERRAL.equals(event.eventType());
    var text =
        delayed
            ? "🟠 Order email delayed: %s\n%s\nMandrill keeps trying. If the desk hasn't confirmed the orders before the cut-off, check with the desk."
                .formatted(addressLine(event), causeLine(event))
            : "🔴 Order email NOT delivered: %s\n%s\nThe orders did not reach this address. Check with the desk before sending them again."
                .formatted(addressLine(event), causeLine(event));
    try {
      notificationService.sendMessage(text, TRADING, delayed ? WARNING : ERROR);
      return true;
    } catch (RuntimeException e) {
      log.error(
          "Order email delivery failure not posted to Slack, not recorded so a later event alerts"
              + " again: mandrillMessageId={}",
          event.mandrillMessageId(),
          e);
      return false;
    }
  }

  private static String addressLine(EmailDeliveryFailedEvent event) {
    return "to=%s, subject=%s".formatted(event.recipient(), event.subject());
  }

  private static String causeLine(EmailDeliveryFailedEvent event) {
    return "%s: %s (id=%s)".formatted(event.eventType(), event.reason(), event.mandrillMessageId());
  }
}
