package ee.tuleva.onboarding.investment.transaction.export;

import static ee.tuleva.onboarding.investment.transaction.export.CustodianOrderMessageFactory.TRADE_ORDER_TAG;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.TRADING;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;
import static java.lang.Boolean.FALSE;
import static java.util.Locale.ROOT;
import static java.util.Objects.requireNonNull;
import static org.springframework.core.Ordered.HIGHEST_PRECEDENCE;

import ee.tuleva.onboarding.event.EventLog;
import ee.tuleva.onboarding.event.EventLogRepository;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.notification.email.EmailDeliveryFailedEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class CustodianOrderEmailFailureAlerter {

  private static final String RECORDED_TYPE_PREFIX = "TRADE_ORDER_EMAIL_";
  private static final String DEFERRAL = "deferral";
  private static final List<String> MANDRILL_DELIVERY_FAILURES =
      List.of("hard_bounce", "soft_bounce", "reject", DEFERRAL);
  private static final String RECIPIENT = "recipient";
  private static final String SUBJECT = "subject";
  private static final String EVENT_TYPE = "eventType";
  private static final String REASON = "reason";
  private static final String ALERT_POSTED = "alertPosted";

  private final OperationsNotificationService notificationService;
  private final EventLogRepository eventLogRepository;

  @EventListener
  @Order(HIGHEST_PRECEDENCE)
  void onEmailDeliveryFailed(EmailDeliveryFailedEvent event) {
    if (!event.tags().contains(TRADE_ORDER_TAG)) {
      return;
    }
    var recordedType = recordedType(event.eventType());
    if (eventLogRepository.existsByTypeAndPrincipal(recordedType, event.mandrillMessageId())) {
      log.debug(
          "Order email delivery failure already recorded, skipping: type={}, mandrillMessageId={}",
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
    var failure = notYetAlerted(event, recordedType);
    eventLogRepository.save(failure);
    alert(failure, event);
  }

  @Scheduled(fixedRateString = "1m")
  @SchedulerLock(
      name = "CustodianOrderEmailFailureAlerter_alertFailuresSlackMissed",
      lockAtMostFor = "5m",
      lockAtLeastFor = "30s")
  void alertFailuresSlackMissed() {
    eventLogRepository
        .findAllByTypeIn(
            MANDRILL_DELIVERY_FAILURES.stream()
                .map(CustodianOrderEmailFailureAlerter::recordedType)
                .toList())
        .stream()
        .filter(failure -> FALSE.equals(failure.getData().get(ALERT_POSTED)))
        .forEach(failure -> alert(failure, deliveryFailure(failure)));
  }

  private void alert(EventLog failure, EmailDeliveryFailedEvent event) {
    if (posted(event)) {
      failure.setData(withAlertPosted(failure.getData()));
      eventLogRepository.save(failure);
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
          "Order email delivery failure not posted to Slack, kept for the next retry run:"
              + " mandrillMessageId={}, event={}",
          event.mandrillMessageId(),
          event.eventType(),
          e);
      return false;
    }
  }

  private static String recordedType(String eventType) {
    return RECORDED_TYPE_PREFIX + eventType.toUpperCase(ROOT);
  }

  private static EventLog notYetAlerted(EmailDeliveryFailedEvent event, String recordedType) {
    Map<String, @Nullable Object> data = new HashMap<>();
    data.put(RECIPIENT, event.recipient());
    data.put(SUBJECT, event.subject());
    data.put(EVENT_TYPE, event.eventType());
    data.put(REASON, event.reason());
    data.put(ALERT_POSTED, false);
    return EventLog.builder()
        .type(recordedType)
        .principal(event.mandrillMessageId())
        .timestamp(event.timestamp())
        .data(data)
        .build();
  }

  private static Map<String, @Nullable Object> withAlertPosted(Map<String, @Nullable Object> data) {
    Map<String, @Nullable Object> alerted = new HashMap<>(data);
    alerted.put(ALERT_POSTED, true);
    return alerted;
  }

  private static EmailDeliveryFailedEvent deliveryFailure(EventLog failure) {
    var data = failure.getData();
    return new EmailDeliveryFailedEvent(
        (String) requireNonNull(data.get(RECIPIENT)),
        (String) data.get(SUBJECT),
        (String) requireNonNull(data.get(EVENT_TYPE)),
        (String) data.get(REASON),
        failure.getPrincipal(),
        failure.getTimestamp(),
        List.of(TRADE_ORDER_TAG));
  }

  private static String addressLine(EmailDeliveryFailedEvent event) {
    return "to=%s, subject=%s".formatted(event.recipient(), event.subject());
  }

  private static String causeLine(EmailDeliveryFailedEvent event) {
    return "%s: %s (id=%s)".formatted(event.eventType(), event.reason(), event.mandrillMessageId());
  }
}
