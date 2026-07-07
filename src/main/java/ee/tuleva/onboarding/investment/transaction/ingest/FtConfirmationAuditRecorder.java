package ee.tuleva.onboarding.investment.transaction.ingest;

import static java.util.Comparator.comparing;

import ee.tuleva.onboarding.investment.transaction.FtConfirmation;
import ee.tuleva.onboarding.investment.transaction.FtConfirmationResult;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEvent;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEventRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@NullMarked
@Component
@RequiredArgsConstructor
class FtConfirmationAuditRecorder {

  static final String FT_CONFIRMATION_VERIFIED = "FT_CONFIRMATION_VERIFIED";
  static final String FT_CONFIRMATION_ALERTED = "FT_CONFIRMATION_ALERTED";
  private static final String ALERT_ACTOR = "system";

  private final TransactionAuditEventRepository auditEventRepository;
  private final Clock clock;

  boolean recordOutcome(
      @Nullable TransactionOrder order,
      FtConfirmation confirmation,
      FtConfirmationResult result,
      String actor) {
    if (statusUnchangedSinceLastRecord(confirmation, result)) {
      return false;
    }

    auditEventRepository.save(
        TransactionAuditEvent.builder()
            .orderId(order == null ? null : order.getId())
            .batch(order == null ? null : order.getBatch())
            .eventType(FT_CONFIRMATION_VERIFIED)
            .actor(actor)
            .dedupKey(dedupKey(confirmation))
            .payload(payload(confirmation, result))
            .createdAt(clock.instant())
            .build());
    return true;
  }

  boolean alreadyAlerted(FtConfirmation confirmation, FtConfirmationResult result) {
    return auditEventRepository
        .findByEventTypeAndDedupKey(FT_CONFIRMATION_ALERTED, dedupKey(confirmation))
        .stream()
        .map(event -> statusPair(event.getPayload()))
        .anyMatch(statusPair(result)::equals);
  }

  void recordAlerted(FtConfirmation confirmation, FtConfirmationResult result) {
    auditEventRepository.save(
        TransactionAuditEvent.builder()
            .eventType(FT_CONFIRMATION_ALERTED)
            .actor(ALERT_ACTOR)
            .dedupKey(dedupKey(confirmation))
            .payload(payload(confirmation, result))
            .createdAt(clock.instant())
            .build());
  }

  private boolean statusUnchangedSinceLastRecord(
      FtConfirmation confirmation, FtConfirmationResult result) {
    return lastRecordedStatus(confirmation).filter(statusPair(result)::equals).isPresent();
  }

  private Optional<List<String>> lastRecordedStatus(FtConfirmation confirmation) {
    return auditEventRepository
        .findByEventTypeAndDedupKey(FT_CONFIRMATION_VERIFIED, dedupKey(confirmation))
        .stream()
        .max(comparing(TransactionAuditEvent::getCreatedAt))
        .map(event -> statusPair(event.getPayload()));
  }

  private static String dedupKey(FtConfirmation confirmation) {
    return String.join(
        "|",
        confirmation.fund().name(),
        confirmation.isin(),
        confirmation.tradeDate().toString(),
        normalized(confirmation.quantity()),
        normalized(confirmation.grossPrice()),
        confirmation.type().name());
  }

  private static String normalized(BigDecimal amount) {
    return amount.stripTrailingZeros().toPlainString();
  }

  private static List<String> statusPair(FtConfirmationResult result) {
    return List.of(result.quantityStatus().name(), result.priceStatus().name());
  }

  private static List<String> statusPair(Map<String, Object> payload) {
    return List.of(
        String.valueOf(payload.get("quantityStatus")), String.valueOf(payload.get("priceStatus")));
  }

  private static Map<String, Object> payload(
      FtConfirmation confirmation, FtConfirmationResult result) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("fund", confirmation.fund().name());
    payload.put("isin", confirmation.isin());
    payload.put("tradeDate", confirmation.tradeDate().toString());
    payload.put("quantity", confirmation.quantity().toPlainString());
    payload.put("grossPrice", confirmation.grossPrice().toPlainString());
    payload.put("type", confirmation.type().name());
    if (confirmation.account() != null) {
      payload.put("account", confirmation.account());
    }
    payload.put("quantityStatus", result.quantityStatus().name());
    payload.put("priceStatus", result.priceStatus().name());
    payload.put("details", result.details());
    return payload;
  }
}
