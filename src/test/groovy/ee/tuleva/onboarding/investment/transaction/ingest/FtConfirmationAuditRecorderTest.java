package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.transaction.FtConfirmationType.CANCELLATION;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.CANCELLED;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.ERROR;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.OK;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.ORPHAN;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.investment.transaction.FtConfirmation;
import ee.tuleva.onboarding.investment.transaction.FtConfirmationResult;
import ee.tuleva.onboarding.investment.transaction.OrderVenue;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEvent;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEventRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FtConfirmationAuditRecorderTest {

  private static final Instant NOW = Instant.parse("2026-06-09T08:00:00Z");
  private static final UUID ORDER_UUID = UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29");
  private static final String DEDUP_KEY = "TUK75|IE000F60HVH9|2026-06-08|40434|10.09|NORMAL";

  @Mock private TransactionAuditEventRepository auditEventRepository;

  private FtConfirmationAuditRecorder recorder() {
    return new FtConfirmationAuditRecorder(auditEventRepository, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void recordOutcome_noPriorEvent_savesOrderLevelEventAndReturnsTrue() {
    TransactionOrder order = order();
    FtConfirmation confirmation = confirmation();
    FtConfirmationResult result =
        new FtConfirmationResult(
            OK, ERROR, Map.of("orderQuantity", "40434", "priceDeltaPercent", "0.12"));

    boolean recorded = recorder().recordOutcome(order, confirmation, result, "admin");

    assertThat(recorded).isTrue();
    verify(auditEventRepository)
        .save(
            TransactionAuditEvent.builder()
                .orderId(42L)
                .eventType("FT_CONFIRMATION_VERIFIED")
                .actor("admin")
                .dedupKey(DEDUP_KEY)
                .payload(
                    Map.of(
                        "fund", "TUK75",
                        "isin", "IE000F60HVH9",
                        "tradeDate", "2026-06-08",
                        "quantity", "40434",
                        "grossPrice", "10.09",
                        "type", "NORMAL",
                        "quantityStatus", "OK",
                        "priceStatus", "ERROR",
                        "details", Map.of("orderQuantity", "40434", "priceDeltaPercent", "0.12")))
                .createdAt(NOW)
                .build());
  }

  @Test
  void recordOutcome_cancellation_recordsTypeAndAccount() {
    TransactionOrder order = order();
    FtConfirmation confirmation =
        new FtConfirmation(
            TUK75,
            "IE000F60HVH9",
            LocalDate.parse("2026-06-08"),
            new BigDecimal("40434"),
            new BigDecimal("10.09"),
            CANCELLATION,
            "FT-ACC");
    FtConfirmationResult result =
        new FtConfirmationResult(CANCELLED, CANCELLED, Map.of("cancellationSignature", "sig"));

    recorder().recordOutcome(order, confirmation, result, "admin");

    verify(auditEventRepository)
        .save(
            TransactionAuditEvent.builder()
                .orderId(42L)
                .eventType("FT_CONFIRMATION_VERIFIED")
                .actor("admin")
                .dedupKey("TUK75|IE000F60HVH9|2026-06-08|40434|10.09|CANCELLATION")
                .payload(
                    Map.of(
                        "fund", "TUK75",
                        "isin", "IE000F60HVH9",
                        "tradeDate", "2026-06-08",
                        "quantity", "40434",
                        "grossPrice", "10.09",
                        "type", "CANCELLATION",
                        "account", "FT-ACC",
                        "quantityStatus", "CANCELLED",
                        "priceStatus", "CANCELLED",
                        "details", Map.of("cancellationSignature", "sig")))
                .createdAt(NOW)
                .build());
  }

  @Test
  void recordOutcome_sameStatusAlreadyRecorded_skipsSaveAndReturnsFalse() {
    given(auditEventRepository.findByEventTypeAndDedupKey("FT_CONFIRMATION_VERIFIED", DEDUP_KEY))
        .willReturn(List.of(priorEvent("ERROR", "OK")));

    boolean recorded =
        recorder()
            .recordOutcome(
                order(), confirmation(), new FtConfirmationResult(ERROR, OK, Map.of()), "admin");

    assertThat(recorded).isFalse();
    verify(auditEventRepository, never()).save(any());
  }

  @Test
  void recordOutcome_statusChangedSinceLastRecord_savesAndReturnsTrue() {
    given(auditEventRepository.findByEventTypeAndDedupKey("FT_CONFIRMATION_VERIFIED", DEDUP_KEY))
        .willReturn(List.of(priorEvent("PENDING_EXECUTION", "PENDING_NAV")));

    boolean recorded =
        recorder()
            .recordOutcome(
                order(), confirmation(), new FtConfirmationResult(ERROR, OK, Map.of()), "admin");

    assertThat(recorded).isTrue();
    verify(auditEventRepository).save(any());
  }

  @Test
  void recordOutcome_orphanHasNoOrder_savesWithNullOrderIdAndGivenActor() {
    FtConfirmation confirmation = confirmation();
    FtConfirmationResult result =
        new FtConfirmationResult(ORPHAN, ORPHAN, Map.of("orphanReason", "no matching order"));

    boolean recorded = recorder().recordOutcome(null, confirmation, result, "ops-person");

    assertThat(recorded).isTrue();
    verify(auditEventRepository)
        .save(
            TransactionAuditEvent.builder()
                .eventType("FT_CONFIRMATION_VERIFIED")
                .actor("ops-person")
                .dedupKey(DEDUP_KEY)
                .payload(
                    Map.of(
                        "fund", "TUK75",
                        "isin", "IE000F60HVH9",
                        "tradeDate", "2026-06-08",
                        "quantity", "40434",
                        "grossPrice", "10.09",
                        "type", "NORMAL",
                        "quantityStatus", "ORPHAN",
                        "priceStatus", "ORPHAN",
                        "details", Map.of("orphanReason", "no matching order")))
                .createdAt(NOW)
                .build());
  }

  @Test
  void alreadyAlerted_noPriorAlert_returnsFalse() {
    given(auditEventRepository.findByEventTypeAndDedupKey("FT_CONFIRMATION_ALERTED", DEDUP_KEY))
        .willReturn(List.of());

    boolean alerted =
        recorder().alreadyAlerted(confirmation(), new FtConfirmationResult(ERROR, OK, Map.of()));

    assertThat(alerted).isFalse();
  }

  @Test
  void alreadyAlerted_priorAlertWithSameStatus_returnsTrue() {
    given(auditEventRepository.findByEventTypeAndDedupKey("FT_CONFIRMATION_ALERTED", DEDUP_KEY))
        .willReturn(List.of(priorAlert("ERROR", "OK")));

    boolean alerted =
        recorder().alreadyAlerted(confirmation(), new FtConfirmationResult(ERROR, OK, Map.of()));

    assertThat(alerted).isTrue();
  }

  @Test
  void alreadyAlerted_priorAlertWithDifferentStatus_returnsFalse() {
    given(auditEventRepository.findByEventTypeAndDedupKey("FT_CONFIRMATION_ALERTED", DEDUP_KEY))
        .willReturn(List.of(priorAlert("AMBIGUOUS", "AMBIGUOUS")));

    boolean alerted =
        recorder().alreadyAlerted(confirmation(), new FtConfirmationResult(ERROR, OK, Map.of()));

    assertThat(alerted).isFalse();
  }

  @Test
  void alreadyAlerted_normalizesAmountScaleWhenBuildingDedupKey() {
    given(auditEventRepository.findByEventTypeAndDedupKey("FT_CONFIRMATION_ALERTED", DEDUP_KEY))
        .willReturn(List.of(priorAlert("ERROR", "OK")));

    boolean alerted =
        recorder()
            .alreadyAlerted(
                new FtConfirmation(
                    TUK75,
                    "IE000F60HVH9",
                    LocalDate.parse("2026-06-08"),
                    new BigDecimal("40434.000"),
                    new BigDecimal("10.0900")),
                new FtConfirmationResult(ERROR, OK, Map.of()));

    assertThat(alerted).isTrue();
  }

  @Test
  void recordAlerted_savesAlertedEventWithSystemActor() {
    recorder().recordAlerted(confirmation(), new FtConfirmationResult(ERROR, OK, Map.of()));

    verify(auditEventRepository)
        .save(
            TransactionAuditEvent.builder()
                .eventType("FT_CONFIRMATION_ALERTED")
                .actor("system")
                .dedupKey(DEDUP_KEY)
                .payload(
                    Map.of(
                        "fund", "TUK75",
                        "isin", "IE000F60HVH9",
                        "tradeDate", "2026-06-08",
                        "quantity", "40434",
                        "grossPrice", "10.09",
                        "type", "NORMAL",
                        "quantityStatus", "ERROR",
                        "priceStatus", "OK",
                        "details", Map.of()))
                .createdAt(NOW)
                .build());
  }

  private static TransactionAuditEvent priorAlert(String quantityStatus, String priceStatus) {
    return priorAlert(quantityStatus, priceStatus, "40434", "10.09");
  }

  private static TransactionAuditEvent priorAlert(
      String quantityStatus, String priceStatus, String quantity, String grossPrice) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("fund", "TUK75");
    payload.put("isin", "IE000F60HVH9");
    payload.put("tradeDate", "2026-06-08");
    payload.put("quantity", quantity);
    payload.put("grossPrice", grossPrice);
    payload.put("type", "NORMAL");
    payload.put("quantityStatus", quantityStatus);
    payload.put("priceStatus", priceStatus);
    return TransactionAuditEvent.builder()
        .eventType("FT_CONFIRMATION_ALERTED")
        .payload(payload)
        .createdAt(NOW)
        .build();
  }

  private static TransactionOrder order() {
    return TransactionOrder.builder()
        .id(42L)
        .fund(TUK75)
        .instrumentIsin("IE000F60HVH9")
        .transactionType(BUY)
        .instrumentType(ETF)
        .orderQuantity(new BigDecimal("40434"))
        .orderVenue(OrderVenue.FT)
        .orderUuid(ORDER_UUID)
        .build();
  }

  private static FtConfirmation confirmation() {
    return new FtConfirmation(
        TUK75,
        "IE000F60HVH9",
        LocalDate.parse("2026-06-08"),
        new BigDecimal("40434"),
        new BigDecimal("10.09"));
  }

  private static TransactionAuditEvent priorEvent(String quantityStatus, String priceStatus) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("fund", "TUK75");
    payload.put("isin", "IE000F60HVH9");
    payload.put("tradeDate", "2026-06-08");
    payload.put("quantity", "40434");
    payload.put("grossPrice", "10.09");
    payload.put("type", "NORMAL");
    payload.put("quantityStatus", quantityStatus);
    payload.put("priceStatus", priceStatus);
    return TransactionAuditEvent.builder()
        .eventType("FT_CONFIRMATION_VERIFIED")
        .payload(payload)
        .createdAt(NOW.minusSeconds(60))
        .build();
  }
}
