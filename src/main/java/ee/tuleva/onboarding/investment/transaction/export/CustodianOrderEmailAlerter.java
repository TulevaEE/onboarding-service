package ee.tuleva.onboarding.investment.transaction.export;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.TRADING;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;
import static java.util.Locale.ROOT;
import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;
import static org.springframework.core.Ordered.HIGHEST_PRECEDENCE;
import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW;

import ee.tuleva.onboarding.event.EventLog;
import ee.tuleva.onboarding.event.EventLogRepository;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.notification.OperationsNotificationService.Severity;
import ee.tuleva.onboarding.notification.email.EmailDeliveryFailedEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
class CustodianOrderEmailAlerter {

  private static final String SEND_RESULT_TYPE = "TRADE_ORDER_EMAIL_SEND_RESULT";
  private static final String DEFERRAL = "deferral";
  private static final List<String> RECORDED_TYPES =
      List.of(
          "TRADE_ORDER_EMAIL_HARD_BOUNCE",
          "TRADE_ORDER_EMAIL_SOFT_BOUNCE",
          "TRADE_ORDER_EMAIL_REJECT",
          "TRADE_ORDER_EMAIL_DEFERRAL",
          SEND_RESULT_TYPE);
  private static final Duration RETRY_WINDOW = Duration.ofHours(24);
  private static final Duration GIVE_UP_LOOKBACK = Duration.ofHours(24);
  private static final int SLACK_POSTS_THAT_FIT_THE_5_MINUTE_LOCK_AT_70_SECONDS_EACH = 4;
  private static final Duration LATE_POST_AFTER = Duration.ofMinutes(5);
  private static final Duration DIRECT_POST_TIME_LONGER_THAN_SLACK_CONNECT_PLUS_READ_TIMEOUT =
      Duration.ofMinutes(2);
  private static final DateTimeFormatter EVENT_TIME =
      DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Europe/Tallinn"));
  private static final String TEXT = "text";
  private static final String SEVERITY = "severity";
  private static final String ALERT = "alert";
  private static final String NOT_POSTED = "NOT_POSTED";
  private static final String POSTED = "POSTED";
  private static final String GIVEN_UP = "GIVEN_UP";
  private static final String NOT_POSTED_WITHIN_THE_RETRY_WINDOW =
      "not posted within the 24 hour retry window";
  private static final String DATA_CANNOT_BE_READ = "its data cannot be read";

  private final OperationsNotificationService notificationService;
  private final EventLogRepository eventLogRepository;
  private final Clock clock;
  private final TransactionTemplate inItsOwnTransaction;

  CustodianOrderEmailAlerter(
      OperationsNotificationService notificationService,
      EventLogRepository eventLogRepository,
      Clock clock,
      PlatformTransactionManager transactionManager) {
    this.notificationService = notificationService;
    this.eventLogRepository = eventLogRepository;
    this.clock = clock;
    this.inItsOwnTransaction = new TransactionTemplate(transactionManager);
    this.inItsOwnTransaction.setPropagationBehavior(PROPAGATION_REQUIRES_NEW);
  }

  @EventListener
  @Order(HIGHEST_PRECEDENCE)
  void onEmailDeliveryFailed(EmailDeliveryFailedEvent event) {
    if (!event.isTradeOrder()) {
      return;
    }
    var recordedType = "TRADE_ORDER_EMAIL_" + event.eventType().toUpperCase(ROOT);
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
    var delayed = DEFERRAL.equals(event.eventType());
    eventLogRepository.save(
        notYetPosted(
            recordedType,
            event.mandrillMessageId(),
            event.timestamp(),
            alertText(event),
            delayed ? WARNING : ERROR));
  }

  void postSendResult(String principal, String text, Severity severity) {
    var sendResult = notYetPosted(SEND_RESULT_TYPE, principal, clock.instant(), text, severity);
    var recorded = recordInItsOwnTransaction(sendResult);
    try {
      notificationService.sendMessage(text, TRADING, severity);
    } catch (RuntimeException e) {
      if (recorded) {
        log.warn(
            "Order email send result not posted to Slack, the scheduled run posts it:"
                + " principal={}, error={}",
            principal,
            e.toString());
      } else {
        log.error(
            "Order email send result neither recorded nor posted to Slack: principal={}, text={}",
            principal,
            text,
            e);
      }
      return;
    }
    if (recorded) {
      markPostedInItsOwnTransaction(sendResult);
    }
  }

  @Scheduled(fixedRateString = "1m")
  @SchedulerLock(
      name = "CustodianOrderEmailAlerter_postAlertsNotYetPosted",
      lockAtMostFor = "5m",
      lockAtLeastFor = "30s")
  void postAlertsNotYetPosted() {
    var now = clock.instant();
    var retryFrom = now.minus(RETRY_WINDOW);
    var notYetPosted =
        eventLogRepository
            .findAllByTypeInAndTimestampGreaterThanEqualOrderByTimestamp(
                RECORDED_TYPES, retryFrom.minus(GIVE_UP_LOOKBACK))
            .stream()
            .filter(CustodianOrderEmailAlerter::isNotYetPosted)
            .filter(row -> !isLeftToItsDirectPost(row, now))
            .toList();
    notYetPosted.stream()
        .filter(failure -> failure.getTimestamp().isBefore(retryFrom))
        .forEach(failure -> giveUp(failure, NOT_POSTED_WITHIN_THE_RETRY_WINDOW));
    var retryable =
        notYetPosted.stream()
            .filter(failure -> !failure.getTimestamp().isBefore(retryFrom))
            .toList();
    retryable.stream()
        .filter(not(CustodianOrderEmailAlerter::isReadable))
        .forEach(failure -> giveUp(failure, DATA_CANNOT_BE_READ));
    retryable.stream()
        .filter(CustodianOrderEmailAlerter::isReadable)
        .limit(SLACK_POSTS_THAT_FIT_THE_5_MINUTE_LOCK_AT_70_SECONDS_EACH)
        .forEach(failure -> post(failure, now));
  }

  private void post(EventLog failure, Instant now) {
    try {
      notificationService.sendMessage(postedText(failure, now), TRADING, severity(failure));
      markAs(POSTED, failure);
    } catch (RuntimeException e) {
      log.warn(
          "Order email alert not posted to Slack, trying again next run:"
              + " eventLogId={}, type={}, principal={}, error={}",
          failure.getId(),
          failure.getType(),
          failure.getPrincipal(),
          e.toString());
    }
  }

  private void giveUp(EventLog failure, String why) {
    try {
      markAs(GIVEN_UP, failure);
      log.error(
          "Order email alert given up, never posted to Slack: why={}, eventLogId={}, type={},"
              + " principal={}, at={}",
          why,
          failure.getId(),
          failure.getType(),
          failure.getPrincipal(),
          failure.getTimestamp());
    } catch (RuntimeException e) {
      log.warn(
          "Order email alert not marked as given up, trying again next run:"
              + " eventLogId={}, error={}",
          failure.getId(),
          e.toString());
    }
  }

  private boolean recordInItsOwnTransaction(EventLog sendResult) {
    try {
      inItsOwnTransaction.executeWithoutResult(status -> eventLogRepository.save(sendResult));
      return true;
    } catch (RuntimeException e) {
      log.error(
          "Order email send result not recorded, posting it to Slack anyway: principal={}, text={}",
          sendResult.getPrincipal(),
          text(sendResult),
          e);
      return false;
    }
  }

  private void markPostedInItsOwnTransaction(EventLog sendResult) {
    try {
      inItsOwnTransaction.executeWithoutResult(status -> markAs(POSTED, sendResult));
    } catch (RuntimeException e) {
      log.warn(
          "Order email send result posted to Slack but not marked as posted, the scheduled run"
              + " may post it again: principal={}, error={}",
          sendResult.getPrincipal(),
          e.toString());
    }
  }

  private static boolean isLeftToItsDirectPost(EventLog row, Instant now) {
    return SEND_RESULT_TYPE.equals(row.getType())
        && row.getTimestamp()
            .isAfter(now.minus(DIRECT_POST_TIME_LONGER_THAN_SLACK_CONNECT_PLUS_READ_TIMEOUT));
  }

  private void markAs(String alert, EventLog failure) {
    Map<String, @Nullable Object> data = new HashMap<>(failure.getData());
    data.put(ALERT, alert);
    failure.setData(data);
    eventLogRepository.save(failure);
  }

  private static boolean isReadable(EventLog failure) {
    try {
      severity(failure);
      text(failure);
      return true;
    } catch (RuntimeException e) {
      return false;
    }
  }

  private static boolean isNotYetPosted(EventLog failure) {
    return failure.getData() != null && NOT_POSTED.equals(failure.getData().get(ALERT));
  }

  private static EventLog notYetPosted(
      String type, String principal, Instant at, String text, Severity severity) {
    Map<String, @Nullable Object> data = new HashMap<>();
    data.put(TEXT, text);
    data.put(SEVERITY, severity.name());
    data.put(ALERT, NOT_POSTED);
    return EventLog.builder().type(type).principal(principal).timestamp(at).data(data).build();
  }

  private static Severity severity(EventLog failure) {
    return Severity.valueOf((String) requireNonNull(failure.getData().get(SEVERITY)));
  }

  private static String text(EventLog failure) {
    return (String) requireNonNull(failure.getData().get(TEXT));
  }

  private static String postedText(EventLog failure, Instant now) {
    var text = text(failure);
    if (!failure.getTimestamp().plus(LATE_POST_AFTER).isBefore(now)) {
      return text;
    }
    var lines = text.split("\n", 2);
    var firstLine = "%s (at %s)".formatted(lines[0], EVENT_TIME.format(failure.getTimestamp()));
    return lines.length == 1 ? firstLine : firstLine + "\n" + lines[1];
  }

  private static String alertText(EmailDeliveryFailedEvent event) {
    return DEFERRAL.equals(event.eventType())
        ? "🟠 Order email delayed: %s\n%s\nMandrill keeps trying. If the desk hasn't confirmed the orders before the cut-off, check with the desk."
            .formatted(addressLine(event), causeLine(event))
        : "🔴 Order email NOT delivered: %s\n%s\nThe orders did not reach this address. Check with the desk before sending them again."
            .formatted(addressLine(event), causeLine(event));
  }

  private static String addressLine(EmailDeliveryFailedEvent event) {
    return "to=%s, subject=%s".formatted(event.recipient(), event.subject());
  }

  private static String causeLine(EmailDeliveryFailedEvent event) {
    return "%s: %s (id=%s)".formatted(event.eventType(), event.reason(), event.mandrillMessageId());
  }
}
