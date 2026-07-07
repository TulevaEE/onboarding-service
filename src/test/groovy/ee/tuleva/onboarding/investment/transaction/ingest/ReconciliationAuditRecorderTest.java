package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SENT;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.investment.transaction.OrderVenue;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEvent;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEventRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionSettlement;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReconciliationAuditRecorderTest {

  private static final LocalDate REPORT_DATE = LocalDate.of(2026, 5, 13);
  private static final UUID CLIENT_REF = UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29");
  private static final Instant NOW = Instant.parse("2026-05-13T07:00:00Z");

  @Mock private TransactionAuditEventRepository auditEventRepository;

  private ReconciliationAuditRecorder newRecorder() {
    return new ReconciliationAuditRecorder(auditEventRepository, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void recordExecutionMatched_savesEventWithOrderIdAndRowPayload() {
    newRecorder().recordExecutionMatched(sampleOrder(), sampleRow(), REPORT_DATE);

    verify(auditEventRepository)
        .save(
            argThat(
                (TransactionAuditEvent event) ->
                    "EXECUTION_MATCHED".equals(event.getEventType())
                        && event.getOrderId().equals(123L)
                        && "system".equals(event.getActor())
                        && NOW.equals(event.getCreatedAt())
                        && "DLA0799512".equals(event.getPayload().get("ourRef"))
                        && CLIENT_REF.toString().equals(event.getPayload().get("clientRef"))
                        && REPORT_DATE.toString().equals(event.getPayload().get("reportDate"))));
  }

  @Test
  void recordUnmatched_savesEventWithoutBatchAndOrder() {
    newRecorder().recordUnmatched(sampleRow(), REPORT_DATE);

    verify(auditEventRepository)
        .save(
            argThat(
                (TransactionAuditEvent event) ->
                    "UNMATCHED_SEB_TRANSACTION".equals(event.getEventType())
                        && event.getOrderId() == null
                        && event.getBatch() == null
                        && "2026-05-13|DLA0799512|bd83f551-8c79-4193-b92b-18e1dfd0bd29|IE000F60HVH9"
                            .equals(event.getDedupKey())
                        && "IE000F60HVH9".equals(event.getPayload().get("isin"))
                        && REPORT_DATE.toString().equals(event.getPayload().get("reportDate"))));
  }

  @Test
  void recordUnmatched_skipsDuplicateForSameRowAndReportDate() {
    given(
            auditEventRepository.findByEventTypeAndDedupKey(
                "UNMATCHED_SEB_TRANSACTION",
                "2026-05-13|DLA0799512|bd83f551-8c79-4193-b92b-18e1dfd0bd29|IE000F60HVH9"))
        .willReturn(
            List.of(
                TransactionAuditEvent.builder().eventType("UNMATCHED_SEB_TRANSACTION").build()));

    newRecorder().recordUnmatched(sampleRow(), REPORT_DATE);

    verify(auditEventRepository, never()).save(any());
  }

  @Test
  void recordQuantityAmountMismatch_savesEventWithOrderIdAndMismatchPayload() {
    newRecorder().recordQuantityAmountMismatch(sampleMismatch());

    verify(auditEventRepository)
        .save(
            argThat(
                (TransactionAuditEvent event) ->
                    "QUANTITY_AMOUNT_MISMATCH".equals(event.getEventType())
                        && event.getOrderId().equals(123L)
                        && "ETF_QUANTITY".equals(event.getPayload().get("kind"))
                        && new BigDecimal("15007").equals(event.getPayload().get("expected"))
                        && new BigDecimal("15007.0003").equals(event.getPayload().get("actual"))
                        && new BigDecimal("0.0001").equals(event.getPayload().get("tolerance"))
                        && new BigDecimal("5").equals(event.getPayload().get("nearMissMultiplier"))
                        && REPORT_DATE.toString().equals(event.getPayload().get("reportDate"))));
  }

  @Test
  void recordQuantityAmountMismatch_skipsDuplicateForSameOrderAndReportDate() {
    given(auditEventRepository.findByOrderIdAndEventType(123L, "QUANTITY_AMOUNT_MISMATCH"))
        .willReturn(
            List.of(
                TransactionAuditEvent.builder()
                    .orderId(123L)
                    .eventType("QUANTITY_AMOUNT_MISMATCH")
                    .payload(Map.of("reportDate", REPORT_DATE.toString()))
                    .build()));

    newRecorder().recordQuantityAmountMismatch(sampleMismatch());

    verify(auditEventRepository, never()).save(any());
  }

  @Test
  void recordSettlementDetected_savesEventWithOrderId() {
    newRecorder().recordSettlementDetected(sampleOrder(), REPORT_DATE);

    verify(auditEventRepository)
        .save(
            argThat(
                (TransactionAuditEvent event) ->
                    "SETTLEMENT_DETECTED".equals(event.getEventType())
                        && event.getOrderId().equals(123L)
                        && REPORT_DATE.toString().equals(event.getPayload().get("reportDate"))
                        && "IE000F60HVH9".equals(event.getPayload().get("isin"))));
  }

  @Test
  void recordSettlementReappeared_savesEventWithSettlementReportDate() {
    newRecorder()
        .recordSettlementReappeared(sampleOrder(), sampleSettlement(), sampleRow(), REPORT_DATE);

    verify(auditEventRepository)
        .save(
            argThat(
                (TransactionAuditEvent event) ->
                    "SETTLEMENT_REAPPEARED".equals(event.getEventType())
                        && event.getOrderId().equals(123L)
                        && REPORT_DATE.toString().equals(event.getPayload().get("reportDate"))
                        && "2026-05-11".equals(event.getPayload().get("settlementReportDate"))));
  }

  @Test
  void recordSettlementReappeared_skipsDuplicateForSameOrderAndReportDate() {
    given(auditEventRepository.findByOrderIdAndEventType(123L, "SETTLEMENT_REAPPEARED"))
        .willReturn(
            List.of(
                TransactionAuditEvent.builder()
                    .orderId(123L)
                    .eventType("SETTLEMENT_REAPPEARED")
                    .payload(Map.of("reportDate", REPORT_DATE.toString()))
                    .build()));

    newRecorder()
        .recordSettlementReappeared(sampleOrder(), sampleSettlement(), sampleRow(), REPORT_DATE);

    verify(auditEventRepository, never()).save(any());
  }

  private static TransactionOrder sampleOrder() {
    return TransactionOrder.builder()
        .id(123L)
        .fund(TKF100)
        .instrumentIsin("IE000F60HVH9")
        .transactionType(BUY)
        .instrumentType(ETF)
        .orderQuantity(new BigDecimal("15007"))
        .orderVenue(OrderVenue.SEB)
        .orderUuid(CLIENT_REF)
        .orderStatus(SENT)
        .build();
  }

  private static TransactionSettlement sampleSettlement() {
    return TransactionSettlement.builder()
        .id(7L)
        .orderId(123L)
        .settledAt(Instant.parse("2026-05-11T07:00:00Z"))
        .reportDate(LocalDate.of(2026, 5, 11))
        .build();
  }

  private static SebPendingTransactionRow sampleRow() {
    return new SebPendingTransactionRow(
        CLIENT_REF,
        "DLA0799512",
        "IE000F60HVH9",
        new BigDecimal("15007"),
        new BigDecimal("4.7255"),
        new BigDecimal("70915.58"),
        new BigDecimal("0.00"),
        new BigDecimal("70915.58"),
        BUY,
        Instant.parse("2026-05-11T10:26:04Z"),
        LocalDate.of(2026, 5, 13),
        "Tuleva Täiendav Kogumisfond",
        "VP68168",
        "ICAV Amundi MSCI USA Screened UCITS ETF");
  }

  private QuantityAmountMismatchEvent sampleMismatch() {
    return new QuantityAmountMismatchEvent(
        sampleRow(),
        sampleOrder(),
        QuantityAmountMismatchEvent.MismatchKind.ETF_QUANTITY,
        new BigDecimal("15007"),
        new BigDecimal("15007.0003"),
        new BigDecimal("0.0003"),
        new BigDecimal("0.0001"),
        new BigDecimal("5"),
        REPORT_DATE);
  }
}
