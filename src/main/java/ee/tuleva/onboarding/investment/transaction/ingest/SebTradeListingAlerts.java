package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.investment.transaction.TransactionAuditEvent;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEventRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionType;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class SebTradeListingAlerts {

  static final String DROPPED_ALERTED = "SEB_TRADE_DROPPED_WITHOUT_PAYMENT_ALERTED";
  static final String NEVER_LISTED_ALERTED = "SEB_ORDER_NEVER_LISTED_ALERTED";
  static final String NOT_RUN_ALERTED = "SEB_TRADE_LISTING_CHECK_NOT_RUN_ALERTED";
  private static final String SYSTEM_ACTOR = "system";
  private static final ZoneId TALLINN = ZoneId.of(TIMEZONE);
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
  private static final String BLOCK_SEPARATOR = "\n\n";

  private final OperationsNotificationService notificationService;
  private final TransactionAuditEventRepository auditEventRepository;
  private final Clock clock;

  void publish(
      List<DroppedTrade> dropped,
      List<TransactionOrder> neverListed,
      List<SkippedListingCheck> skipped) {
    List<DroppedTrade> newDropped =
        dropped.stream().filter(trade -> !hasAlerted(DROPPED_ALERTED, trade.reference())).toList();
    List<TransactionOrder> newNeverListed =
        neverListed.stream()
            .filter(order -> !hasAlerted(NEVER_LISTED_ALERTED, neverListedKey(order)))
            .toList();
    List<SkippedListingCheck> newSkipped =
        skipped.stream().filter(check -> !hasAlerted(NOT_RUN_ALERTED, todaysKey(check))).toList();

    List<String> blocks = new ArrayList<>();
    if (!newDropped.isEmpty()) {
      blocks.add(droppedBlock(newDropped));
    }
    if (!newNeverListed.isEmpty()) {
      blocks.add(neverListedBlock(newNeverListed));
    }
    newSkipped.forEach(check -> blocks.add(skippedBlock(check)));
    if (blocks.isEmpty() || !send(String.join(BLOCK_SEPARATOR, blocks))) {
      return;
    }
    try {
      newDropped.forEach(this::recordAlerted);
      newNeverListed.forEach(this::recordAlerted);
      newSkipped.forEach(this::recordAlerted);
    } catch (RuntimeException e) {
      log.error(
          "SEB trade listing alerts sent but not recorded, they may repeat: error={}",
          e.getMessage(),
          e);
    }
  }

  private static String droppedBlock(List<DroppedTrade> dropped) {
    return "⚠️ SEB tehing kadus ootel-tehingutest, pangas pole selle viitega makset: "
        + dropped.size()
        + "\n"
        + dropped.stream().map(SebTradeListingAlerts::droppedLine).collect(Collectors.joining("\n"))
        + "\nKüsi SEB-lt, miks tehing nimekirjast kadus ja kas see arveldub.";
  }

  private static String droppedLine(DroppedTrade trade) {
    TransactionOrder order = trade.order();
    return order.getFund().getCode()
        + " · "
        + trade.reference()
        + " · "
        + side(order.getTransactionType())
        + " "
        + order.getInstrumentIsin()
        + " · SEB arvelduspäev "
        + DATE.format(trade.settlementDate());
  }

  private static String neverListedBlock(List<TransactionOrder> orders) {
    return "⚠️ SEB ootel-tehingutes pole tehingukorraldust: "
        + orders.size()
        + "\n"
        + orders.stream()
            .map(SebTradeListingAlerts::neverListedLine)
            .collect(Collectors.joining("\n"))
        + "\nKontrolli SEB-lt, kas tehingukorraldus jõudis kohale (või jäi SEB rida os-is"
        + " sidumata).";
  }

  private static String neverListedLine(TransactionOrder order) {
    return order.getFund().getCode()
        + " · tehingukorraldus "
        + order.getId()
        + " · "
        + side(order.getTransactionType())
        + " "
        + order.getInstrumentIsin()
        + " · saadetud "
        + sentOn(order.getOrderTimestamp());
  }

  private static String sentOn(@Nullable Instant orderTimestamp) {
    return DATE.format(
        requireNonNull(orderTimestamp, "A never-listed order has a send time").atZone(TALLINN));
  }

  private static String skippedBlock(SkippedListingCheck check) {
    return "⚠️ SEB ootel-tehingute kontroll jäi tegemata: "
        + check.cause()
        + ".\n"
        + check.action()
        + " Kontroll kordub järgmisel tööpäeval.";
  }

  private static String side(TransactionType side) {
    return side == BUY ? "ost" : "müük";
  }

  private boolean send(String message) {
    try {
      notificationService.sendMessage(message, INVESTMENT, WARNING);
      return true;
    } catch (RuntimeException e) {
      log.error("Failed to send SEB trade listing alert to Slack: error={}", e.getMessage(), e);
      return false;
    }
  }

  private boolean hasAlerted(String eventType, String dedupKey) {
    try {
      return !auditEventRepository.findByEventTypeAndDedupKey(eventType, dedupKey).isEmpty();
    } catch (RuntimeException e) {
      log.error(
          "SEB trade listing alert history unreadable, alerting again: dedupKey={}, error={}",
          dedupKey,
          e.getMessage(),
          e);
      return false;
    }
  }

  private void recordAlerted(DroppedTrade trade) {
    save(
        DROPPED_ALERTED,
        trade.order().getId(),
        trade.reference(),
        Map.of("settlementDate", trade.settlementDate().toString()));
  }

  private void recordAlerted(TransactionOrder order) {
    save(NEVER_LISTED_ALERTED, order.getId(), neverListedKey(order), Map.of());
  }

  private void recordAlerted(SkippedListingCheck check) {
    save(NOT_RUN_ALERTED, null, todaysKey(check), Map.of("reason", check.cause()));
  }

  private static String neverListedKey(TransactionOrder order) {
    return order.getOrderUuid().toString();
  }

  private String todaysKey(SkippedListingCheck check) {
    return LocalDate.ofInstant(clock.instant(), TALLINN) + " " + check.cause();
  }

  private void save(
      String eventType, @Nullable Long orderId, String dedupKey, Map<String, Object> payload) {
    auditEventRepository.save(
        TransactionAuditEvent.builder()
            .eventType(eventType)
            .orderId(orderId)
            .actor(SYSTEM_ACTOR)
            .dedupKey(dedupKey)
            .payload(payload)
            .createdAt(clock.instant())
            .build());
  }
}
