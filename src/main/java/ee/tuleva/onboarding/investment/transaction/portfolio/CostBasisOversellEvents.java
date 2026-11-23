package ee.tuleva.onboarding.investment.transaction.portfolio;

import static java.util.Comparator.comparing;

import ee.tuleva.onboarding.investment.transaction.TransactionAuditEvent;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEventRepository;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class CostBasisOversellEvents {

  static final String OVERSELL_FOUND = "COST_BASIS_OVERSELL_FOUND";
  static final String OVERSELL_CLEARED = "COST_BASIS_OVERSELL_CLEARED";
  static final String OVERSELL_POSTED = "COST_BASIS_OVERSELL_POSTED";

  static final String FUND = "fund";
  static final String INSTRUMENT_ISIN = "instrumentIsin";
  static final String AS_OF_DATE = "asOfDate";
  static final String HELD_QUANTITY = "heldQuantity";
  static final String SELL_QUANTITY = "sellQuantity";
  static final String EVENT_ID = "eventId";

  static final Comparator<TransactionAuditEvent> RECORDING_ORDER =
      comparing(TransactionAuditEvent::getId);

  private static final String SYSTEM_ACTOR = "system";

  private CostBasisOversellEvents() {}

  static Stream<TransactionAuditEvent> stateEvents(TransactionAuditEventRepository repository) {
    return Stream.concat(
        repository.findByEventType(OVERSELL_FOUND).stream(),
        repository.findByEventType(OVERSELL_CLEARED).stream());
  }

  static void save(
      TransactionAuditEventRepository repository,
      Clock clock,
      String eventType,
      String dedupKey,
      Map<String, Object> payload) {
    repository.save(
        TransactionAuditEvent.builder()
            .eventType(eventType)
            .dedupKey(dedupKey)
            .actor(SYSTEM_ACTOR)
            .payload(payload)
            .createdAt(clock.instant())
            .build());
  }

  static boolean isFound(TransactionAuditEvent state) {
    return OVERSELL_FOUND.equals(state.getEventType());
  }

  static boolean isOf(TulevaFund fund, TransactionAuditEvent state) {
    return fund.getCode().equals(state.getPayload().get(FUND));
  }

  static Optional<LocalDate> asOfDate(TransactionAuditEvent state) {
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

  static String dedupKey(TransactionAuditEvent state) {
    Map<String, Object> payload = state.getPayload();
    return dedupKey(
        String.valueOf(payload.get(FUND)),
        String.valueOf(payload.get(INSTRUMENT_ISIN)),
        String.valueOf(payload.get(AS_OF_DATE)));
  }

  static String dedupKey(String fundCode, String instrumentIsin, String asOfDate) {
    return fundCode + " " + instrumentIsin + " " + asOfDate;
  }
}
