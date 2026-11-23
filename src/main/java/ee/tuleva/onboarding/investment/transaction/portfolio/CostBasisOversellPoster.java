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
import static java.util.stream.Collectors.maxBy;
import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.investment.transaction.TransactionAuditEvent;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEventRepository;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
final class CostBasisOversellPoster {

  private final TransactionAuditEventRepository auditEventRepository;
  private final OperationsNotificationService notificationService;
  private final Clock clock;

  void postPending() {
    try {
      List<TransactionAuditEvent> states = stateEvents(auditEventRepository).toList();
      Set<String> sent =
          auditEventRepository.findByEventType(OVERSELL_POSTED).stream()
              .map(marker -> String.valueOf(marker.getPayload().get(EVENT_ID)))
              .collect(toSet());
      for (TransactionAuditEvent state : latestUnsent(states, sent)) {
        Optional<String> message = render(state);
        if (message.isPresent() && (!send(message.get(), dedupKey(state)) || !markSent(state))) {
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

  private static List<TransactionAuditEvent> latestUnsent(
      List<TransactionAuditEvent> states, Set<String> sent) {
    return states.stream()
        .collect(groupingBy(CostBasisOversellEvents::dedupKey, maxBy(RECORDING_ORDER)))
        .values()
        .stream()
        .flatMap(Optional::stream)
        .filter(latest -> !sent.contains(idOf(latest)))
        .sorted(RECORDING_ORDER)
        .toList();
  }

  private boolean markSent(TransactionAuditEvent state) {
    try {
      save(
          auditEventRepository,
          clock,
          OVERSELL_POSTED,
          dedupKey(state),
          Map.of(EVENT_ID, idOf(state)));
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
