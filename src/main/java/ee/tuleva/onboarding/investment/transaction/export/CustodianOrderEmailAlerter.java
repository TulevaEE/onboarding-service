package ee.tuleva.onboarding.investment.transaction.export;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.TRADING;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.INFO;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;
import static java.util.Comparator.comparingInt;
import static java.util.Locale.ROOT;
import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;
import static org.springframework.core.Ordered.HIGHEST_PRECEDENCE;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONTENT_TOO_LARGE;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

@Slf4j
@Component
@RequiredArgsConstructor
class CustodianOrderEmailAlerter {

  static final String SEND_RESULT_TYPE = "TRADE_ORDER_EMAIL_SEND_RESULT";
  static final String SENDING = "SENDING";
  static final String NOT_POSTED = "NOT_POSTED";
  static final String POSTED = "POSTED";
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
  private static final List<Severity> SEVERITIES_MOST_URGENT_FIRST = List.of(ERROR, WARNING, INFO);
  private static final List<HttpStatus> SLACK_REFUSES_THE_PAYLOAD_ITSELF_WITH =
      List.of(BAD_REQUEST, CONTENT_TOO_LARGE);
  private static final Duration DIRECT_POST_TIME_LONGER_THAN_SLACK_CONNECT_PLUS_READ_TIMEOUT =
      Duration.ofMinutes(2);
  private static final Duration SEND_ABANDONED_AFTER_MORE_THAN_3_MANDRILL_TRIES_OF_10S_PLUS_30S =
      Duration.ofMinutes(5);
  private static final DateTimeFormatter EVENT_TIME =
      DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Europe/Tallinn"));
  private static final String TEXT = "text";
  private static final String SEVERITY = "severity";
  private static final String ALERT = "alert";
  private static final String GIVEN_UP = "GIVEN_UP";
  private static final String NOT_POSTED_WITHIN_THE_RETRY_WINDOW =
      "not posted within the 24 hour retry window";
  private static final String DATA_CANNOT_BE_READ = "its data cannot be read";

  private final OperationsNotificationService notificationService;
  private final EventLogRepository eventLogRepository;
  private final Clock clock;

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
        EventLog.builder()
            .type(recordedType)
            .principal(event.mandrillMessageId())
            .timestamp(event.timestamp())
            .data(alertData(alertText(event), delayed ? WARNING : ERROR, NOT_POSTED))
            .build());
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
            .filter(row -> isNotYetPosted(row) || isAbandonedSendAttempt(row, now))
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
    var mostUrgentFirst =
        retryable.stream()
            .filter(CustodianOrderEmailAlerter::isReadable)
            .sorted(
                comparingInt((EventLog row) -> SEVERITIES_MOST_URGENT_FIRST.indexOf(severity(row)))
                    .thenComparing(EventLog::getTimestamp))
            .toList();
    var slots = SLACK_POSTS_THAT_FIT_THE_5_MINUTE_LOCK_AT_70_SECONDS_EACH;
    for (var row : mostUrgentFirst) {
      if (slots == 0) {
        return;
      }
      if (postTakesASlot(row, now)) {
        slots--;
      }
    }
  }

  private boolean postTakesASlot(EventLog failure, Instant now) {
    try {
      notificationService.sendMessage(postedText(failure, now), TRADING, severity(failure));
      markAs(POSTED, failure);
    } catch (HttpClientErrorException e) {
      if (isRefusalOfThePayloadItself(e)) {
        giveUp(failure, "Slack refused it with " + e.getStatusCode());
        return false;
      }
      keepForTheNextRun(failure, e);
    } catch (RuntimeException e) {
      keepForTheNextRun(failure, e);
    }
    return true;
  }

  private static boolean isRefusalOfThePayloadItself(HttpClientErrorException e) {
    return SLACK_REFUSES_THE_PAYLOAD_ITSELF_WITH.stream()
        .anyMatch(status -> status.isSameCodeAs(e.getStatusCode()));
  }

  private static void keepForTheNextRun(EventLog failure, RuntimeException e) {
    log.warn(
        "Order email alert not posted to Slack, trying again next run:"
            + " eventLogId={}, type={}, principal={}, error={}",
        failure.getId(),
        failure.getType(),
        failure.getPrincipal(),
        e.toString());
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

  private static boolean isNotYetPosted(EventLog row) {
    return NOT_POSTED.equals(row.getData().get(ALERT));
  }

  private static boolean isAbandonedSendAttempt(EventLog row, Instant now) {
    return SENDING.equals(row.getData().get(ALERT))
        && row.getTimestamp()
            .isBefore(now.minus(SEND_ABANDONED_AFTER_MORE_THAN_3_MANDRILL_TRIES_OF_10S_PLUS_30S));
  }

  static Map<String, @Nullable Object> alertData(String text, Severity severity, String alert) {
    Map<String, @Nullable Object> data = new HashMap<>();
    data.put(TEXT, text);
    data.put(SEVERITY, severity.name());
    data.put(ALERT, alert);
    return data;
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
