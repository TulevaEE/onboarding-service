package ee.tuleva.onboarding.investment.transaction.ingest;

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;

import ee.tuleva.onboarding.investment.transaction.FtConfirmation;
import ee.tuleva.onboarding.investment.transaction.FtConfirmationResult;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEvent;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEventRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

  boolean recordOutcome(@Nullable TransactionOrder order, FtCheckOutcome outcome, String actor) {
    if (statusUnchangedSinceLastRecord(outcome)) {
      return false;
    }

    auditEventRepository.save(
        TransactionAuditEvent.builder()
            .orderId(order == null ? null : order.getId())
            .batch(order == null ? null : order.getBatch())
            .eventType(FT_CONFIRMATION_VERIFIED)
            .actor(actor)
            .dedupKey(outcome.auditKey())
            .payload(payload(outcome))
            .createdAt(clock.instant())
            .build());
    return true;
  }

  LocalDate firstSeenOn(FtConfirmation confirmation) {
    return auditEventRepository
        .findByEventTypeAndDedupKey(FT_CONFIRMATION_VERIFIED, dedupKey(confirmation))
        .stream()
        .map(TransactionAuditEvent::getCreatedAt)
        .filter(Objects::nonNull)
        .min(naturalOrder())
        .map(verifiedAt -> LocalDate.ofInstant(verifiedAt, clock.getZone()))
        .orElseGet(() -> LocalDate.now(clock));
  }

  boolean alreadyAlerted(FtCheckOutcome outcome) {
    return auditEventRepository
        .findByEventTypeAndDedupKey(FT_CONFIRMATION_ALERTED, outcome.auditKey())
        .stream()
        .map(event -> statusPair(event.getPayload()))
        .anyMatch(statusPair(outcome.result())::equals);
  }

  void recordAlerted(FtCheckOutcome outcome) {
    auditEventRepository.save(
        TransactionAuditEvent.builder()
            .eventType(FT_CONFIRMATION_ALERTED)
            .actor(ALERT_ACTOR)
            .dedupKey(outcome.auditKey())
            .payload(payload(outcome))
            .createdAt(clock.instant())
            .build());
  }

  private boolean statusUnchangedSinceLastRecord(FtCheckOutcome outcome) {
    return lastRecordedStatus(outcome.auditKey())
        .filter(statusPair(outcome.result())::equals)
        .isPresent();
  }

  private Optional<List<String>> lastRecordedStatus(String auditKey) {
    return auditEventRepository
        .findByEventTypeAndDedupKey(FT_CONFIRMATION_VERIFIED, auditKey)
        .stream()
        .max(comparing(TransactionAuditEvent::getCreatedAt))
        .map(event -> statusPair(event.getPayload()));
  }

  static String dedupKey(FtConfirmation confirmation) {
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

  private static Map<String, Object> payload(FtCheckOutcome outcome) {
    FtConfirmationResult result = outcome.result();
    Map<String, Object> payload = new LinkedHashMap<>(outcome.auditFacts());
    payload.put("quantityStatus", result.quantityStatus().name());
    payload.put("priceStatus", result.priceStatus().name());
    payload.put("details", result.details());
    return payload;
  }
}
