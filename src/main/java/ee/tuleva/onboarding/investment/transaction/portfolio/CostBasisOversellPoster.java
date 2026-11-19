package ee.tuleva.onboarding.investment.transaction.portfolio;

import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellEvents.EVENT_ID;
import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellEvents.OVERSELL_POSTED;
import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellEvents.RECORDING_ORDER;
import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellEvents.asOfDate;
import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellEvents.dedupKey;
import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellEvents.isFound;
import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellEvents.save;
import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellEvents.stateEvents;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.investment.transaction.TransactionAuditEvent;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEventRepository;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
final class CostBasisOversellPoster {

  private static final String OUTCOME = "outcome";
  private static final String SENT = "SENT";
  private static final String SUPERSEDED = "SUPERSEDED";

  private final TransactionAuditEventRepository auditEventRepository;
  private final OperationsNotificationService notificationService;
  private final Clock clock;

  void postPending() {
    try {
      List<TransactionAuditEvent> states = stateEvents(auditEventRepository).toList();
      List<TransactionAuditEvent> markers = auditEventRepository.findByEventType(OVERSELL_POSTED);
      Set<String> handled = markedIds(markers, Optional.empty());
      Set<String> sent = markedIds(markers, Optional.of(SENT));
      for (TransactionAuditEvent state : latestToSend(states, handled, sent)) {
        Optional<String> message = render(state);
        if (message.isPresent() && (!send(message.get(), dedupKey(state)) || !mark(state, SENT))) {
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

  void postDirectly(String message, String dedupKey) {
    send(message, dedupKey);
  }

  private List<TransactionAuditEvent> latestToSend(
      List<TransactionAuditEvent> states, Set<String> handled, Set<String> sent) {
    Map<String, List<TransactionAuditEvent>> unhandledByKey =
        states.stream()
            .filter(state -> !handled.contains(idOf(state)))
            .collect(groupingBy(CostBasisOversellEvents::dedupKey));
    List<TransactionAuditEvent> toSend = new ArrayList<>();
    for (List<TransactionAuditEvent> unhandled : unhandledByKey.values()) {
      List<TransactionAuditEvent> inOrder = unhandled.stream().sorted(RECORDING_ORDER).toList();
      TransactionAuditEvent latest = inOrder.getLast();
      inOrder.subList(0, inOrder.size() - 1).forEach(state -> mark(state, SUPERSEDED));
      if (isFound(latest) || wasAnnounced(dedupKey(latest), states, sent)) {
        toSend.add(latest);
      } else {
        mark(latest, SUPERSEDED);
      }
    }
    return toSend.stream().sorted(RECORDING_ORDER).toList();
  }

  private static boolean wasAnnounced(
      String dedupKey, List<TransactionAuditEvent> states, Set<String> sent) {
    return states.stream()
        .filter(CostBasisOversellEvents::isFound)
        .filter(found -> dedupKey(found).equals(dedupKey))
        .anyMatch(found -> sent.contains(idOf(found)));
  }

  private static Set<String> markedIds(
      List<TransactionAuditEvent> markers, Optional<String> outcome) {
    return markers.stream()
        .filter(
            marker ->
                outcome
                    .map(wanted -> wanted.equals(marker.getPayload().getOrDefault(OUTCOME, SENT)))
                    .orElse(true))
        .map(marker -> String.valueOf(marker.getPayload().get(EVENT_ID)))
        .collect(toSet());
  }

  private boolean mark(TransactionAuditEvent state, String outcome) {
    try {
      save(
          auditEventRepository,
          clock,
          OVERSELL_POSTED,
          dedupKey(state),
          Map.of(EVENT_ID, idOf(state), OUTCOME, outcome));
      return true;
    } catch (RuntimeException e) {
      log.error(
          "Cost-basis oversell alert handled but not recorded, it may repeat: dedupKey={},"
              + " outcome={}, error={}",
          dedupKey(state),
          outcome,
          e.getMessage(),
          e);
      return false;
    }
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

  private static Optional<String> render(TransactionAuditEvent state) {
    return asOfDate(state)
        .map(
            asOfDate ->
                isFound(state)
                    ? CostBasisOversellAlertText.found(state.getPayload(), asOfDate)
                    : CostBasisOversellAlertText.cleared(state.getPayload(), asOfDate));
  }

  private static String idOf(TransactionAuditEvent state) {
    return String.valueOf(state.getId());
  }
}
