package ee.tuleva.onboarding.investment.transaction.portfolio;

import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellEvents.AS_OF_DATE;
import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellEvents.FUND;
import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellEvents.HELD_QUANTITY;
import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellEvents.INSTRUMENT_ISIN;
import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellEvents.OVERSELL_CLEARED;
import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellEvents.OVERSELL_FOUND;
import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellEvents.RECORDING_ORDER;
import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellEvents.SELL_QUANTITY;
import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellEvents.asOfDate;
import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellEvents.dedupKey;
import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellEvents.isFound;
import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellEvents.isOf;
import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellEvents.save;
import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellEvents.stateEvents;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.maxBy;

import ee.tuleva.onboarding.investment.transaction.TransactionAuditEvent;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEventRepository;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
class CostBasisOversellAlerter {

  private final TransactionAuditEventRepository auditEventRepository;
  private final Clock clock;
  private final CostBasisOversellPoster poster;

  CostBasisOversellAlerter(
      TransactionAuditEventRepository auditEventRepository,
      OperationsNotificationService notificationService,
      Clock clock) {
    this.auditEventRepository = auditEventRepository;
    this.clock = clock;
    this.poster = new CostBasisOversellPoster(auditEventRepository, notificationService, clock);
  }

  boolean recordFound(TulevaFund fund, CostBasisOversellException exception) {
    CostBasisOversell oversell = exception.getOversell();
    String dedupKey =
        dedupKey(fund.getCode(), oversell.instrumentIsin(), oversell.asOfDate().toString());
    Map<String, Object> payload = foundPayload(fund, oversell);
    try {
      if (latestState(dedupKey).filter(state -> isTheSameOversell(state, payload)).isEmpty()) {
        save(auditEventRepository, clock, OVERSELL_FOUND, dedupKey, payload);
      }
      return true;
    } catch (RuntimeException saveFailure) {
      log.error(
          "Cost-basis oversell not recorded, posting it directly with a manual-recompute note:"
              + " dedupKey={}, error={}",
          dedupKey,
          saveFailure.getMessage(),
          saveFailure);
      poster.postDirectly(
          CostBasisOversellAlertText.unrecorded(payload, oversell.asOfDate()), dedupKey);
      return false;
    }
  }

  boolean hasOpenOversell(TulevaFund fund) {
    return openOversellsOf(fund).findAny().isPresent();
  }

  void clearOpen(TulevaFund fund, LocalDate rebuiltTo) {
    try {
      openOversellsOf(fund)
          .filter(found -> asOfDate(found).filter(date -> !date.isAfter(rebuiltTo)).isPresent())
          .forEach(
              found ->
                  save(
                      auditEventRepository,
                      clock,
                      OVERSELL_CLEARED,
                      dedupKey(found),
                      clearedPayload(found)));
    } catch (RuntimeException e) {
      log.error(
          "Resolved cost-basis oversells not cleared, next self-heal replays and retries:"
              + " fundCode={}, error={}",
          fund.getCode(),
          e.getMessage(),
          e);
    }
  }

  void postPending() {
    poster.postPending();
  }

  private Stream<TransactionAuditEvent> openOversellsOf(TulevaFund fund) {
    return openOversells().stream().filter(found -> isOf(fund, found));
  }

  private List<TransactionAuditEvent> openOversells() {
    return stateEvents(auditEventRepository)
        .collect(groupingBy(CostBasisOversellEvents::dedupKey, maxBy(RECORDING_ORDER)))
        .values()
        .stream()
        .flatMap(Optional::stream)
        .filter(CostBasisOversellEvents::isFound)
        .toList();
  }

  private Optional<TransactionAuditEvent> latestState(String dedupKey) {
    return Stream.concat(
            auditEventRepository.findByEventTypeAndDedupKey(OVERSELL_FOUND, dedupKey).stream(),
            auditEventRepository.findByEventTypeAndDedupKey(OVERSELL_CLEARED, dedupKey).stream())
        .max(RECORDING_ORDER);
  }

  private static boolean isTheSameOversell(
      TransactionAuditEvent state, Map<String, Object> payload) {
    return isFound(state) && payload.equals(state.getPayload());
  }

  private static Map<String, Object> foundPayload(TulevaFund fund, CostBasisOversell oversell) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put(FUND, fund.getCode());
    payload.put(INSTRUMENT_ISIN, oversell.instrumentIsin());
    payload.put(AS_OF_DATE, oversell.asOfDate().toString());
    payload.put(HELD_QUANTITY, plain(oversell.heldQuantity()));
    payload.put(SELL_QUANTITY, plain(oversell.sellQuantity()));
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
