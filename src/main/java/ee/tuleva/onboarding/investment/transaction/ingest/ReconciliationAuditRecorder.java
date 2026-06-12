package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.transaction.TransactionAuditEvent;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEventRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionSettlement;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ReconciliationAuditRecorder {

  static final String EXECUTION_MATCHED = "EXECUTION_MATCHED";
  static final String UNMATCHED_SEB_TRANSACTION = "UNMATCHED_SEB_TRANSACTION";
  static final String QUANTITY_AMOUNT_MISMATCH = "QUANTITY_AMOUNT_MISMATCH";
  static final String SETTLEMENT_DETECTED = "SETTLEMENT_DETECTED";
  static final String SETTLEMENT_REAPPEARED = "SETTLEMENT_REAPPEARED";

  private static final String SYSTEM_ACTOR = "system";

  private final TransactionAuditEventRepository auditEventRepository;
  private final Clock clock;

  void recordExecutionMatched(
      TransactionOrder order, SebPendingTransactionRow row, LocalDate reportDate) {
    save(EXECUTION_MATCHED, order, rowPayload(row, reportDate));
  }

  void recordUnmatched(SebPendingTransactionRow row, LocalDate reportDate) {
    boolean alreadyRecorded =
        auditEventRepository.findByEventType(UNMATCHED_SEB_TRANSACTION).stream()
            .anyMatch(event -> sameRowIdentity(event.getPayload(), row, reportDate));
    if (alreadyRecorded) {
      return;
    }
    save(UNMATCHED_SEB_TRANSACTION, null, rowPayload(row, reportDate));
  }

  void recordQuantityAmountMismatch(QuantityAmountMismatchEvent mismatch) {
    TransactionOrder order = mismatch.order();
    LocalDate reportDate = mismatch.reportDate();
    if (alreadyRecordedForReportDate(order.getId(), QUANTITY_AMOUNT_MISMATCH, reportDate)) {
      return;
    }
    Map<String, Object> payload = rowPayload(mismatch.row(), reportDate);
    payload.put("kind", mismatch.kind().name());
    putIfNotNull(payload, "expected", mismatch.expected());
    putIfNotNull(payload, "actual", mismatch.actual());
    putIfNotNull(payload, "delta", mismatch.delta());
    putIfNotNull(payload, "tolerance", mismatch.tolerance());
    putIfNotNull(payload, "nearMissMultiplier", mismatch.nearMissMultiplier());
    save(QUANTITY_AMOUNT_MISMATCH, order, payload);
  }

  void recordSettlementDetected(TransactionOrder order, LocalDate reportDate) {
    save(SETTLEMENT_DETECTED, order, orderPayload(order, reportDate));
  }

  void recordSettlementReappeared(
      TransactionOrder order,
      TransactionSettlement settlement,
      SebPendingTransactionRow row,
      LocalDate reportDate) {
    if (alreadyRecordedForReportDate(order.getId(), SETTLEMENT_REAPPEARED, reportDate)) {
      return;
    }
    Map<String, Object> payload = rowPayload(row, reportDate);
    payload.put("settlementReportDate", settlement.getReportDate().toString());
    save(SETTLEMENT_REAPPEARED, order, payload);
  }

  private boolean alreadyRecordedForReportDate(
      Long orderId, String eventType, LocalDate reportDate) {
    return auditEventRepository.findByOrderIdAndEventType(orderId, eventType).stream()
        .anyMatch(event -> reportDate.toString().equals(event.getPayload().get("reportDate")));
  }

  private void save(String eventType, TransactionOrder order, Map<String, Object> payload) {
    auditEventRepository.save(
        TransactionAuditEvent.builder()
            .orderId(order == null ? null : order.getId())
            .batch(order == null ? null : order.getBatch())
            .eventType(eventType)
            .actor(SYSTEM_ACTOR)
            .payload(payload)
            .createdAt(clock.instant())
            .build());
  }

  private static boolean sameRowIdentity(
      Map<String, Object> payload, SebPendingTransactionRow row, LocalDate reportDate) {
    return Objects.equals(payload.get("reportDate"), reportDate.toString())
        && Objects.equals(payload.get("ourRef"), row.ourRef())
        && Objects.equals(
            payload.get("clientRef"), row.clientRef() == null ? null : row.clientRef().toString())
        && Objects.equals(payload.get("isin"), row.isin());
  }

  private static Map<String, Object> rowPayload(
      SebPendingTransactionRow row, LocalDate reportDate) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("reportDate", reportDate.toString());
    putIfNotNull(payload, "clientRef", row.clientRef() == null ? null : row.clientRef().toString());
    putIfNotNull(payload, "ourRef", row.ourRef());
    putIfNotNull(payload, "isin", row.isin());
    putIfNotNull(payload, "quantity", row.quantity());
    putIfNotNull(payload, "price", row.price());
    putIfNotNull(payload, "settlementAmount", row.settlementAmount());
    putIfNotNull(payload, "brokerFee", row.brokerFee());
    putIfNotNull(payload, "total", row.total());
    putIfNotNull(payload, "side", row.side() == null ? null : row.side().name());
    putIfNotNull(payload, "tradeDate", row.tradeDate() == null ? null : row.tradeDate().toString());
    putIfNotNull(
        payload,
        "settlementDate",
        row.settlementDate() == null ? null : row.settlementDate().toString());
    putIfNotNull(payload, "clientName", row.clientName());
    putIfNotNull(payload, "account", row.account());
    putIfNotNull(payload, "instrumentName", row.instrumentName());
    return payload;
  }

  private static Map<String, Object> orderPayload(TransactionOrder order, LocalDate reportDate) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("reportDate", reportDate.toString());
    payload.put("orderUuid", order.getOrderUuid().toString());
    payload.put("isin", order.getInstrumentIsin());
    payload.put("fund", order.getFund().name());
    return payload;
  }

  private static void putIfNotNull(Map<String, Object> payload, String key, Object value) {
    if (value != null) {
      payload.put(key, value);
    }
  }
}
