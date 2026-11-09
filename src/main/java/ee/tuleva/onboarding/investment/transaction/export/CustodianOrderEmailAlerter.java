package ee.tuleva.onboarding.investment.transaction.export;

import static ee.tuleva.onboarding.investment.transaction.export.OrderEmailAlertRows.SEND_RESULT_TYPE;
import static ee.tuleva.onboarding.investment.transaction.export.OrderEmailAlertRows.isCandidate;
import static ee.tuleva.onboarding.investment.transaction.export.OrderEmailAlertRows.severity;
import static ee.tuleva.onboarding.investment.transaction.export.OrderEmailAlertRows.text;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.TRADING;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.INFO;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;
import static java.util.Comparator.comparingInt;
import static java.util.Locale.ROOT;
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
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
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
  private static final DateTimeFormatter EVENT_TIME =
      DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Europe/Tallinn"));
  private static final String NOT_POSTED_WITHIN_THE_RETRY_WINDOW =
      "not posted within the 24 hour retry window";
  private static final String DATA_CANNOT_BE_READ = "its data cannot be read";
  private static final String NOT_RESOLVED_BEFORE_THE_RUNS_TWO_DAY_READ_WINDOW =
      "still unresolved when it fell out of the run's two-day read window";

  private final OperationsNotificationService notificationService;
  private final EventLogRepository eventLogRepository;
  private final OrderEmailAlertRows alertRows;
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
    alertRows.recordDeliveryFailure(
        recordedType,
        event.mandrillMessageId(),
        event.timestamp(),
        alertText(event),
        delayed ? WARNING : ERROR);
  }

  @Scheduled(fixedRateString = "1m")
  @SchedulerLock(
      name = "CustodianOrderEmailAlerter_postAlertsNotYetPosted",
      lockAtMostFor = "5m",
      lockAtLeastFor = "30s")
  void postAlertsNotYetPosted() {
    var now = clock.instant();
    var retryFrom = now.minus(RETRY_WINDOW);
    var candidates =
        eventLogRepository
            .findAllByTypeInAndTimestampGreaterThanEqualOrderByTimestamp(
                RECORDED_TYPES, retryFrom.minus(GIVE_UP_LOOKBACK))
            .stream()
            .filter(row -> isCandidate(row, now))
            .toList();
    candidates.stream()
        .filter(row -> row.getTimestamp().isBefore(retryFrom))
        .forEach(row -> giveUp(row, NOT_POSTED_WITHIN_THE_RETRY_WINDOW));
    var retryable =
        candidates.stream().filter(row -> !row.getTimestamp().isBefore(retryFrom)).toList();
    retryable.stream()
        .filter(not(OrderEmailAlertRows::isReadable))
        .forEach(row -> giveUp(row, DATA_CANNOT_BE_READ));
    var mostUrgentFirst =
        retryable.stream()
            .filter(OrderEmailAlertRows::isReadable)
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

  @Scheduled(fixedRateString = "1h")
  @SchedulerLock(
      name = "CustodianOrderEmailAlerter_giveUpAlertsOlderThanTheReadWindow",
      lockAtMostFor = "10m",
      lockAtLeastFor = "1m")
  void giveUpAlertsOlderThanTheReadWindow() {
    eventLogRepository
        .findAllByTypeInAndTimestampLessThan(
            RECORDED_TYPES, clock.instant().minus(RETRY_WINDOW).minus(GIVE_UP_LOOKBACK))
        .stream()
        .filter(OrderEmailAlertRows::isUnresolved)
        .forEach(row -> giveUp(row, NOT_RESOLVED_BEFORE_THE_RUNS_TWO_DAY_READ_WINDOW));
  }

  private boolean postTakesASlot(EventLog row, Instant now) {
    try {
      return alertRows.postIfStillDue(
          row,
          now,
          locked ->
              notificationService.sendMessage(postedText(locked, now), TRADING, severity(locked)));
    } catch (HttpClientErrorException e) {
      if (isRefusalOfThePayloadItself(e)) {
        giveUp(row, "Slack refused it with " + e.getStatusCode());
        return false;
      }
      keepForTheNextRun(row, e);
    } catch (RuntimeException e) {
      keepForTheNextRun(row, e);
    }
    return true;
  }

  private static boolean isRefusalOfThePayloadItself(HttpClientErrorException e) {
    return SLACK_REFUSES_THE_PAYLOAD_ITSELF_WITH.stream()
        .anyMatch(status -> status.isSameCodeAs(e.getStatusCode()));
  }

  private static void keepForTheNextRun(EventLog row, RuntimeException e) {
    log.warn(
        "Order email alert not posted to Slack, trying again next run:"
            + " eventLogId={}, type={}, principal={}, error={}",
        row.getId(),
        row.getType(),
        row.getPrincipal(),
        e.toString());
  }

  private void giveUp(EventLog row, String why) {
    try {
      if (alertRows.giveUp(row)) {
        log.error(
            "Order email alert given up, never posted to Slack: why={}, eventLogId={}, type={},"
                + " principal={}, at={}",
            why,
            row.getId(),
            row.getType(),
            row.getPrincipal(),
            row.getTimestamp());
      }
    } catch (RuntimeException e) {
      log.warn(
          "Order email alert not marked as given up, trying again next run:"
              + " eventLogId={}, error={}",
          row.getId(),
          e.toString());
    }
  }

  private static String postedText(EventLog row, Instant now) {
    var text = text(row);
    if (!row.getTimestamp().plus(LATE_POST_AFTER).isBefore(now)) {
      return text;
    }
    var lines = text.split("\n", 2);
    var firstLine = "%s (at %s)".formatted(lines[0], EVENT_TIME.format(row.getTimestamp()));
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
