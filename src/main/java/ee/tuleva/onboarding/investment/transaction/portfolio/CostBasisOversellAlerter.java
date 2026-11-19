package ee.tuleva.onboarding.investment.transaction.portfolio;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.maxBy;
import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.investment.transaction.TransactionAuditEvent;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEventRepository;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class CostBasisOversellAlerter {

  static final String OVERSELL_FOUND = "COST_BASIS_OVERSELL_FOUND";
  static final String OVERSELL_CLEARED = "COST_BASIS_OVERSELL_CLEARED";
  static final String OVERSELL_POSTED = "COST_BASIS_OVERSELL_POSTED";

  private static final String SYSTEM_ACTOR = "system";
  static final String FUND = "fund";
  static final String INSTRUMENT_ISIN = "instrumentIsin";
  static final String AS_OF_DATE = "asOfDate";
  static final String HELD_QUANTITY = "heldQuantity";
  static final String SELL_QUANTITY = "sellQuantity";
  private static final String EVENT_ID = "eventId";
  private static final Comparator<TransactionAuditEvent> RECORDING_ORDER =
      comparing(TransactionAuditEvent::getId);

  private final TransactionAuditEventRepository auditEventRepository;
  private final OperationsNotificationService notificationService;
  private final Clock clock;

  void recordFound(TulevaFund fund, CostBasisOversellException oversell) {
    String dedupKey =
        dedupKey(fund.getCode(), oversell.getInstrumentIsin(), oversell.getAsOfDate().toString());
    Map<String, Object> payload = foundPayload(fund, oversell);
    try {
      if (latestState(dedupKey).filter(state -> isTheSameOversell(state, payload)).isEmpty()) {
        save(OVERSELL_FOUND, dedupKey, payload);
      }
    } catch (RuntimeException e) {
      log.error(
          "Cost-basis oversell not recorded, posting it directly with a manual-recompute note:"
              + " dedupKey={}, error={}",
          dedupKey,
          e.getMessage(),
          e);
      send(CostBasisOversellAlertText.unrecorded(payload, oversell.getAsOfDate()), dedupKey);
      return;
    }
    clearOpenBefore(fund, oversell.getAsOfDate());
  }

  private void clearOpenBefore(TulevaFund fund, LocalDate stoppedAt) {
    try {
      openOversellsOf(fund)
          .filter(found -> asOfDate(found).filter(date -> date.isBefore(stoppedAt)).isPresent())
          .forEach(this::clear);
    } catch (RuntimeException e) {
      log.error(
          "Oversells the replay got past not cleared, next self-heal retries: fundCode={},"
              + " error={}",
          fund.getCode(),
          e.getMessage(),
          e);
    }
  }

  boolean hasOpenOversell(TulevaFund fund) {
    return openOversellsOf(fund).findAny().isPresent();
  }

  void clearOpen(TulevaFund fund, LocalDate rebuiltTo) {
    try {
      openOversellsOf(fund)
          .filter(found -> asOfDate(found).filter(date -> !date.isAfter(rebuiltTo)).isPresent())
          .forEach(this::clear);
    } catch (RuntimeException e) {
      log.error(
          "Resolved cost-basis oversells not cleared, next self-heal replays and retries:"
              + " fundCode={}, error={}",
          fund.getCode(),
          e.getMessage(),
          e);
    }
  }

  private Stream<TransactionAuditEvent> openOversellsOf(TulevaFund fund) {
    return openOversells().stream().filter(found -> isOf(fund, found));
  }

  private void clear(TransactionAuditEvent found) {
    save(OVERSELL_CLEARED, dedupKey(found), clearedPayload(found));
  }

  void postPending() {
    try {
      for (TransactionAuditEvent state : unposted()) {
        Optional<String> message = render(state);
        if (message.isPresent()
            && (!send(message.get(), dedupKey(state)) || !recordPosted(state))) {
          return;
        }
      }
    } catch (RuntimeException e) {
      log.error(
          "Cost-basis oversell alerts unreadable, next self-heal retries: error={}",
          e.getMessage(),
          e);
    }
  }

  private boolean recordPosted(TransactionAuditEvent state) {
    try {
      save(OVERSELL_POSTED, dedupKey(state), Map.of(EVENT_ID, String.valueOf(state.getId())));
      return true;
    } catch (RuntimeException e) {
      log.error(
          "Cost-basis oversell alert sent but not recorded, it may repeat: dedupKey={},"
              + " error={}",
          dedupKey(state),
          e.getMessage(),
          e);
      return false;
    }
  }

  private Optional<TransactionAuditEvent> latestState(String dedupKey) {
    return Stream.concat(
            auditEventRepository.findByEventTypeAndDedupKey(OVERSELL_FOUND, dedupKey).stream(),
            auditEventRepository.findByEventTypeAndDedupKey(OVERSELL_CLEARED, dedupKey).stream())
        .max(RECORDING_ORDER);
  }

  private List<TransactionAuditEvent> openOversells() {
    return stateEvents()
        .collect(groupingBy(CostBasisOversellAlerter::dedupKey, maxBy(RECORDING_ORDER)))
        .values()
        .stream()
        .flatMap(Optional::stream)
        .filter(CostBasisOversellAlerter::isFound)
        .toList();
  }

  private List<TransactionAuditEvent> unposted() {
    Set<String> postedEventIds =
        auditEventRepository.findByEventType(OVERSELL_POSTED).stream()
            .map(posted -> String.valueOf(posted.getPayload().get(EVENT_ID)))
            .collect(toSet());
    return stateEvents()
        .filter(state -> !postedEventIds.contains(String.valueOf(state.getId())))
        .sorted(RECORDING_ORDER)
        .toList();
  }

  private Stream<TransactionAuditEvent> stateEvents() {
    return Stream.concat(
        auditEventRepository.findByEventType(OVERSELL_FOUND).stream(),
        auditEventRepository.findByEventType(OVERSELL_CLEARED).stream());
  }

  private boolean send(String message, String dedupKey) {
    try {
      notificationService.sendMessage(message, INVESTMENT);
      return true;
    } catch (RuntimeException e) {
      log.error(
          "Failed to send cost-basis oversell alert to Slack, next self-heal retries:"
              + " dedupKey={}, error={}",
          dedupKey,
          e.getMessage(),
          e);
      return false;
    }
  }

  private void save(String eventType, String dedupKey, Map<String, Object> payload) {
    auditEventRepository.save(
        TransactionAuditEvent.builder()
            .eventType(eventType)
            .dedupKey(dedupKey)
            .actor(SYSTEM_ACTOR)
            .payload(payload)
            .createdAt(clock.instant())
            .build());
  }

  private static boolean isTheSameOversell(
      TransactionAuditEvent state, Map<String, Object> payload) {
    return isFound(state) && payload.equals(state.getPayload());
  }

  private static boolean isFound(TransactionAuditEvent state) {
    return OVERSELL_FOUND.equals(state.getEventType());
  }

  private static boolean isOf(TulevaFund fund, TransactionAuditEvent found) {
    return fund.getCode().equals(found.getPayload().get(FUND));
  }

  private static Optional<LocalDate> asOfDate(TransactionAuditEvent state) {
    try {
      return Optional.of(LocalDate.parse(String.valueOf(state.getPayload().get(AS_OF_DATE))));
    } catch (DateTimeParseException e) {
      log.error(
          "Cost-basis oversell event has no readable date, skipped: eventId={}, error={}",
          state.getId(),
          e.getMessage(),
          e);
      return Optional.empty();
    }
  }

  private static Optional<String> render(TransactionAuditEvent state) {
    return asOfDate(state)
        .map(
            asOfDate ->
                isFound(state)
                    ? CostBasisOversellAlertText.found(state.getPayload(), asOfDate)
                    : CostBasisOversellAlertText.cleared(state.getPayload(), asOfDate));
  }

  private static String dedupKey(TransactionAuditEvent state) {
    Map<String, Object> payload = state.getPayload();
    return dedupKey(
        String.valueOf(payload.get(FUND)),
        String.valueOf(payload.get(INSTRUMENT_ISIN)),
        String.valueOf(payload.get(AS_OF_DATE)));
  }

  private static String dedupKey(String fundCode, String instrumentIsin, String asOfDate) {
    return fundCode + " " + instrumentIsin + " " + asOfDate;
  }

  private static Map<String, Object> foundPayload(
      TulevaFund fund, CostBasisOversellException oversell) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put(FUND, fund.getCode());
    payload.put(INSTRUMENT_ISIN, oversell.getInstrumentIsin());
    payload.put(AS_OF_DATE, oversell.getAsOfDate().toString());
    payload.put(HELD_QUANTITY, plain(oversell.getHeldQuantity()));
    payload.put(SELL_QUANTITY, plain(oversell.getSellQuantity()));
    return payload;
  }

  private static Map<String, Object> clearedPayload(TransactionAuditEvent found) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put(FUND, found.getPayload().get(FUND));
    payload.put(INSTRUMENT_ISIN, found.getPayload().get(INSTRUMENT_ISIN));
    payload.put(AS_OF_DATE, found.getPayload().get(AS_OF_DATE));
    return payload;
  }

  private static String plain(BigDecimal quantity) {
    return quantity.stripTrailingZeros().toPlainString();
  }
}
