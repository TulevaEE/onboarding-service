package ee.tuleva.onboarding.investment.transaction.export;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static java.util.Objects.requireNonNull;
import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW;

import ee.tuleva.onboarding.event.EventLog;
import ee.tuleva.onboarding.event.EventLogRepository;
import ee.tuleva.onboarding.notification.OperationsNotificationService.Severity;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class OrderEmailAlertRows {

  static final String SEND_RESULT_TYPE = "TRADE_ORDER_EMAIL_SEND_RESULT";
  private static final String TEXT = "text";
  private static final String SEVERITY = "severity";
  private static final String ALERT = "alert";
  private static final String SENDING = "SENDING";
  private static final String NOT_POSTED = "NOT_POSTED";
  private static final String POSTED = "POSTED";
  private static final String GIVEN_UP = "GIVEN_UP";
  private static final String CLAIMED = "CLAIMED";
  private static final String CLAIMED_AT = "claimedAt";
  private static final Duration CLAIM_LONGER_THAN_SLACK_CONNECT_PLUS_READ_TIMEOUT =
      Duration.ofMinutes(2);
  private static final Duration DIRECT_POST_TIME_LONGER_THAN_SLACK_CONNECT_PLUS_READ_TIMEOUT =
      Duration.ofMinutes(2);
  private static final Duration SEND_ABANDONED_AFTER_MORE_THAN_3_MANDRILL_TRIES_OF_10S_PLUS_30S =
      Duration.ofMinutes(5);

  private final EventLogRepository eventLogRepository;
  private final Clock clock;
  private final TransactionTemplate inItsOwnTransaction;

  OrderEmailAlertRows(
      EventLogRepository eventLogRepository,
      Clock clock,
      PlatformTransactionManager transactionManager) {
    this.eventLogRepository = eventLogRepository;
    this.clock = clock;
    this.inItsOwnTransaction = new TransactionTemplate(transactionManager);
    this.inItsOwnTransaction.setPropagationBehavior(PROPAGATION_REQUIRES_NEW);
  }

  void recordDeliveryFailure(
      String type, String mandrillMessageId, Instant failedAt, String text, Severity severity) {
    eventLogRepository.save(
        row(type, mandrillMessageId, failedAt, alertData(text, severity, NOT_POSTED)));
  }

  void recordSendAttempt(long batchId, String resultUnknown) {
    eventLogRepository.save(
        row(
            SEND_RESULT_TYPE,
            String.valueOf(batchId),
            clock.instant(),
            alertData(resultUnknown, ERROR, SENDING)));
  }

  void recordSendResult(long batchId, String text, Severity severity) {
    recordSendResultAs(NOT_POSTED, batchId, text, severity);
  }

  void recordPostedSendResult(long batchId, String text, Severity severity) {
    recordSendResultAs(POSTED, batchId, text, severity);
  }

  void markSendResultPosted(long batchId, String text) {
    inItsOwnTransaction.executeWithoutResult(
        status ->
            latestSendResultLocked(batchId)
                .filter(row -> isIn(NOT_POSTED, row) && text.equals(row.getData().get(TEXT)))
                .ifPresent(row -> moveTo(POSTED, row)));
  }

  static boolean isCandidate(EventLog row, Instant now) {
    return row.getData() == null || isDue(row, now);
  }

  boolean postIfStillDue(EventLog row, Instant now, Consumer<EventLog> post) {
    var id = requireNonNull(row.getId());
    var claimed =
        inItsOwnTransaction.execute(
            status ->
                eventLogRepository
                    .findLockedById(id)
                    .filter(locked -> isDue(locked, now))
                    .map(locked -> claim(locked, now))
                    .orElse(null));
    if (claimed == null) {
      return false;
    }
    post.accept(claimed);
    inItsOwnTransaction.executeWithoutResult(
        status ->
            eventLogRepository
                .findLockedById(id)
                .filter(locked -> isStillClaimedAs(claimed, locked))
                .ifPresent(locked -> moveTo(POSTED, locked)));
    return true;
  }

  boolean giveUp(EventLog row) {
    return Boolean.TRUE.equals(
        inItsOwnTransaction.execute(
            status ->
                eventLogRepository
                    .findLockedById(requireNonNull(row.getId()))
                    .filter(
                        locked ->
                            locked.getData() == null
                                || isIn(NOT_POSTED, locked)
                                || isIn(SENDING, locked)
                                || isIn(CLAIMED, locked))
                    .map(
                        locked -> {
                          moveTo(GIVEN_UP, locked);
                          return true;
                        })
                    .orElse(false)));
  }

  static boolean isReadable(EventLog row) {
    try {
      severity(row);
      text(row);
      return true;
    } catch (RuntimeException e) {
      return false;
    }
  }

  static Severity severity(EventLog row) {
    return Severity.valueOf((String) requireNonNull(row.getData().get(SEVERITY)));
  }

  static String text(EventLog row) {
    return (String) requireNonNull(row.getData().get(TEXT));
  }

  private void recordSendResultAs(String alert, long batchId, String text, Severity severity) {
    inItsOwnTransaction.executeWithoutResult(
        status -> {
          var result =
              latestSendResultLocked(batchId)
                  .filter(row -> isIn(SENDING, row) || isIn(CLAIMED, row))
                  .orElseGet(
                      () ->
                          EventLog.builder()
                              .type(SEND_RESULT_TYPE)
                              .principal(String.valueOf(batchId))
                              .build());
          result.setTimestamp(clock.instant());
          result.setData(alertData(text, severity, alert));
          eventLogRepository.save(result);
        });
  }

  private Optional<EventLog> latestSendResultLocked(long batchId) {
    return eventLogRepository.findFirstByTypeAndPrincipalOrderByTimestampDesc(
        SEND_RESULT_TYPE, String.valueOf(batchId));
  }

  private EventLog claim(EventLog row, Instant now) {
    Map<String, @Nullable Object> data = new HashMap<>(row.getData());
    data.put(ALERT, CLAIMED);
    data.put(CLAIMED_AT, now.toString());
    row.setData(data);
    eventLogRepository.save(row);
    return row;
  }

  private void moveTo(String alert, EventLog row) {
    Map<String, @Nullable Object> data =
        row.getData() == null ? new HashMap<>() : new HashMap<>(row.getData());
    data.put(ALERT, alert);
    data.remove(CLAIMED_AT);
    row.setData(data);
    eventLogRepository.save(row);
  }

  private static boolean isStillClaimedAs(EventLog claimed, EventLog row) {
    return isIn(CLAIMED, row)
        && Objects.equals(claimed.getData().get(CLAIMED_AT), row.getData().get(CLAIMED_AT));
  }

  private static boolean isDue(EventLog row, Instant now) {
    return isNotYetPostedAndNotLeftToItsDirectPost(row, now)
        || isAbandonedSendAttempt(row, now)
        || isExpiredClaim(row, now);
  }

  private static boolean isNotYetPostedAndNotLeftToItsDirectPost(EventLog row, Instant now) {
    return isIn(NOT_POSTED, row) && !isLeftToItsDirectPost(row, now);
  }

  private static boolean isAbandonedSendAttempt(EventLog row, Instant now) {
    return isIn(SENDING, row)
        && row.getTimestamp()
            .isBefore(now.minus(SEND_ABANDONED_AFTER_MORE_THAN_3_MANDRILL_TRIES_OF_10S_PLUS_30S));
  }

  private static boolean isExpiredClaim(EventLog row, Instant now) {
    return isIn(CLAIMED, row)
        && claimedBefore(row, now.minus(CLAIM_LONGER_THAN_SLACK_CONNECT_PLUS_READ_TIMEOUT));
  }

  private static boolean claimedBefore(EventLog row, Instant cutoff) {
    try {
      return Instant.parse(String.valueOf(row.getData().get(CLAIMED_AT))).isBefore(cutoff);
    } catch (DateTimeParseException e) {
      return true;
    }
  }

  private static boolean isLeftToItsDirectPost(EventLog row, Instant now) {
    return SEND_RESULT_TYPE.equals(row.getType())
        && row.getTimestamp()
            .isAfter(now.minus(DIRECT_POST_TIME_LONGER_THAN_SLACK_CONNECT_PLUS_READ_TIMEOUT));
  }

  private static boolean isIn(String alert, EventLog row) {
    return row.getData() != null && alert.equals(row.getData().get(ALERT));
  }

  private static EventLog row(
      String type, String principal, Instant at, Map<String, @Nullable Object> data) {
    return EventLog.builder().type(type).principal(principal).timestamp(at).data(data).build();
  }

  private static Map<String, @Nullable Object> alertData(
      String text, Severity severity, String alert) {
    Map<String, @Nullable Object> data = new HashMap<>();
    data.put(TEXT, text);
    data.put(SEVERITY, severity.name());
    data.put(ALERT, alert);
    return data;
  }
}
