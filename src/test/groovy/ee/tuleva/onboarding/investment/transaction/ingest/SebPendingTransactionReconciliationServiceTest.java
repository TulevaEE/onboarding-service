package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SENT;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SETTLED;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.investment.transaction.InstrumentType;
import ee.tuleva.onboarding.investment.transaction.OrderVenue;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEvent;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEventRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionSettlement;
import ee.tuleva.onboarding.investment.transaction.TransactionSettlementRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionSettlementService;
import ee.tuleva.onboarding.investment.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class SebPendingTransactionReconciliationServiceTest {

  @Mock private TransactionOrderRepository orderRepository;
  @Mock private TransactionExecutionRepository executionRepository;
  @Mock private TransactionAuditEventRepository auditEventRepository;
  @Mock private TransactionSettlementRepository settlementRepository;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private TransactionMatchingPolicy matchingPolicy;
  @Mock private InvestmentReportService reportService;

  // Real collaborators so we exercise the actual extraction + mapping pipeline
  private final SebPendingTransactionExtractor extractor = new SebPendingTransactionExtractor();
  private final TransactionExecutionMapper mapper = new TransactionExecutionMapper();
  private final Clock clock = Clock.fixed(Instant.parse("2026-05-13T07:00:00Z"), ZoneOffset.UTC);

  private SebPendingTransactionReconciliationService service;

  private SebPendingTransactionReconciliationService newService() {
    given(matchingPolicy.current())
        .willReturn(new TransactionMatchingProperties(null, null, null, null));
    SebClientNameToFundResolver resolver = new SebClientNameToFundResolver();
    QuantityAmountValidator validator = new QuantityAmountValidator();
    return new SebPendingTransactionReconciliationService(
        extractor,
        new SebPendingTransactionMatcher(orderRepository),
        new SebPendingTransactionComplexMatcher(
            orderRepository, executionRepository, resolver, validator),
        validator,
        matchingPolicy,
        mapper,
        executionRepository,
        orderRepository,
        eventPublisher,
        new ReconciliationAuditRecorder(auditEventRepository, clock),
        settlementRepository,
        new TransactionSettlementService(settlementRepository, orderRepository, clock),
        reportService);
  }

  @Test
  void reconcile_matchedOrder_insertsExecutionAndTransitionsOrderToExecuted() {
    service = newService();
    UUID clientRef = UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29");
    TransactionOrder order = sampleOrder(clientRef);
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.of(order));
    given(executionRepository.findByOrderId(123L)).willReturn(Optional.empty());

    service.reconcile(reportWithSingleRow(clientRef));

    verify(executionRepository)
        .save(
            argThat(
                (TransactionExecution e) ->
                    e.getOrderId().equals(123L)
                        && "DLA0799512".equals(e.getBrokerTransactionId())
                        && e.getExecutedQuantity().compareTo(new BigDecimal("15007")) == 0
                        && "SEB_OOTEL".equals(e.getSource())));

    assertThat(order.getOrderStatus()).isEqualTo(EXECUTED);
    verify(orderRepository).save(order);
  }

  @Test
  void reconcile_existingExecution_updatesInPlace() {
    service = newService();
    UUID clientRef = UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29");
    TransactionOrder order = sampleOrder(clientRef);
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.of(order));

    TransactionExecution existing =
        TransactionExecution.builder()
            .id(99L)
            .orderId(123L)
            .source("SEB_OOTEL")
            .brokerTransactionId("OLD")
            .executedQuantity(new BigDecimal("1"))
            .build();
    given(executionRepository.findByOrderId(123L)).willReturn(Optional.of(existing));

    service.reconcile(reportWithSingleRow(clientRef));

    verify(executionRepository).save(existing);
    assertThat(existing.getId()).isEqualTo(99L);
    assertThat(existing.getBrokerTransactionId()).isEqualTo("DLA0799512");
    assertThat(existing.getExecutedQuantity()).isEqualByComparingTo("15007");
  }

  @Test
  void reconcile_unmatchedRow_publishesNoEventAndDoesNotPersistExecution() {
    service = newService();
    UUID clientRef = UUID.fromString("00000000-0000-0000-0000-000000000099");
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.empty());

    service.reconcile(reportWithSingleRow(clientRef));

    verify(eventPublisher, org.mockito.Mockito.never()).publishEvent(any(Object.class));
    verify(executionRepository, org.mockito.Mockito.never()).save(any());
  }

  @Test
  void reconcile_rowMissingClientRef_fallsBackToComplexMatch() {
    service = newService();
    TransactionOrder order = sampleComplexMatchOrder(UUID.randomUUID());
    given(orderRepository.findByInstrumentIsin("IE000F60HVH9")).willReturn(List.of(order));
    given(executionRepository.findByOrderId(123L)).willReturn(Optional.empty());

    InvestmentReport report = reportWithSingleRow(null);
    service.reconcile(report);

    verify(executionRepository)
        .save(
            argThat(
                (TransactionExecution e) ->
                    e.getOrderId().equals(123L) && "SEB_OOTEL".equals(e.getSource())));
    assertThat(order.getOrderStatus()).isEqualTo(EXECUTED);
  }

  @Test
  void reconcile_unknownUuid_fallsBackToComplexMatch() {
    service = newService();
    UUID clientRef = UUID.fromString("00000000-0000-0000-0000-000000000099");
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.empty());

    TransactionOrder order = sampleComplexMatchOrder(UUID.randomUUID());
    given(orderRepository.findByInstrumentIsin("IE000F60HVH9")).willReturn(List.of(order));
    given(executionRepository.findByOrderId(123L)).willReturn(Optional.empty());

    service.reconcile(reportWithSingleRow(clientRef));

    verify(executionRepository).save(any(TransactionExecution.class));
    assertThat(order.getOrderStatus()).isEqualTo(EXECUTED);
  }

  @Test
  void reconcile_unknownUuidWithOurRefPointingAtExecution_matchesViaBrokerRefTier() {
    service = newService();
    UUID clientRef = UUID.fromString("00000000-0000-0000-0000-000000000099");
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.empty());

    TransactionOrder order = sampleOrder(UUID.randomUUID());
    TransactionExecution existing =
        TransactionExecution.builder()
            .id(99L)
            .orderId(123L)
            .source("SEB_OOTEL")
            .brokerTransactionId("DLA0799512")
            .executedQuantity(new BigDecimal("1"))
            .build();
    given(executionRepository.findAllByBrokerTransactionId("DLA0799512"))
        .willReturn(List.of(existing));
    given(orderRepository.findById(123L)).willReturn(Optional.of(order));
    given(executionRepository.findByOrderId(123L)).willReturn(Optional.of(existing));

    service.reconcile(reportWithSingleRow(clientRef));

    verify(orderRepository, never()).findByInstrumentIsin(any());
    verify(executionRepository).save(existing);
    assertThat(existing.getExecutedQuantity()).isEqualByComparingTo("15007");
    assertThat(order.getOrderStatus()).isEqualTo(EXECUTED);
  }

  @Test
  void reconcile_brokerRefMatchedSettledOrder_recordsReappearanceWithoutUpsert() {
    service = newService();
    UUID clientRef = UUID.fromString("00000000-0000-0000-0000-000000000099");
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.empty());

    TransactionOrder settledOrder = sampleOrder(UUID.randomUUID());
    settledOrder.setOrderStatus(SETTLED);
    TransactionExecution existing =
        TransactionExecution.builder()
            .id(99L)
            .orderId(123L)
            .source("SEB_OOTEL")
            .brokerTransactionId("DLA0799512")
            .build();
    given(executionRepository.findAllByBrokerTransactionId("DLA0799512"))
        .willReturn(List.of(existing));
    given(orderRepository.findById(123L)).willReturn(Optional.of(settledOrder));
    given(settlementRepository.findByOrderId(123L))
        .willReturn(Optional.of(settlement(123L, LocalDate.of(2026, 5, 10))));

    service.reconcile(reportWithSingleRow(clientRef));

    verify(auditEventRepository)
        .save(
            argThat(
                (TransactionAuditEvent event) ->
                    "SETTLEMENT_REAPPEARED".equals(event.getEventType())
                        && event.getOrderId().equals(123L)));
    verify(executionRepository, never()).save(any());
    assertThat(settledOrder.getOrderStatus()).isEqualTo(SETTLED);
  }

  @Test
  void reconcile_nearMissOrder_publishesQuantityAmountMismatchEventAndNotUnmatched() {
    service = newService();
    UUID clientRef = UUID.fromString("00000000-0000-0000-0000-000000000099");
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.empty());

    // Order quantity 15007 vs row 15007.0003 → outside 0.0001 tolerance but inside 0.0005 (5x)
    TransactionOrder order =
        TransactionOrder.builder()
            .id(123L)
            .fund(TKF100)
            .instrumentIsin("IE000F60HVH9")
            .transactionType(BUY)
            .instrumentType(ETF)
            .orderQuantity(new BigDecimal("15007"))
            .orderVenue(OrderVenue.SEB)
            .orderUuid(UUID.randomUUID())
            .orderStatus(SENT)
            .build();
    given(orderRepository.findByInstrumentIsin("IE000F60HVH9")).willReturn(List.of(order));
    given(executionRepository.findByOrderId(123L)).willReturn(Optional.empty());

    InvestmentReport report = reportWithSingleRowQuantity(clientRef, new BigDecimal("15007.0003"));
    service.reconcile(report);

    verify(eventPublisher)
        .publishEvent(
            argThat(
                (Object e) ->
                    e instanceof QuantityAmountMismatchEvent qe
                        && qe.reportDate().equals(report.getReportDate())
                        && qe.order().getId().equals(123L)
                        && qe.kind() == QuantityAmountMismatchEvent.MismatchKind.ETF_QUANTITY));
    verify(executionRepository, org.mockito.Mockito.never()).save(any());
  }

  @Test
  void reconcile_uuidMatchWithDivergentEtfQuantity_quarantinesWithoutExecuting() {
    service = newService();
    UUID clientRef = UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29");
    TransactionOrder order = orderWith(clientRef, ETF, BUY, new BigDecimal("15000"), null);
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.of(order));

    InvestmentReport report = reportWithSingleRow(clientRef);
    service.reconcile(report);

    verify(eventPublisher)
        .publishEvent(
            argThat(
                (Object e) ->
                    e instanceof QuantityAmountMismatchEvent qe
                        && qe.kind() == QuantityAmountMismatchEvent.MismatchKind.ETF_QUANTITY
                        && qe.expected().compareTo(new BigDecimal("15000")) == 0
                        && qe.actual().compareTo(new BigDecimal("15007")) == 0
                        && qe.reportDate().equals(report.getReportDate())));
    verify(auditEventRepository)
        .save(
            argThat(
                (TransactionAuditEvent event) ->
                    "QUANTITY_AMOUNT_MISMATCH".equals(event.getEventType())
                        && event.getOrderId().equals(123L)));
    // Quarantine: divergent fill is NOT absorbed as EXECUTED, no execution persisted.
    verify(executionRepository, never()).save(any());
    assertThat(order.getOrderStatus()).isEqualTo(SENT);
  }

  @Test
  void reconcile_uuidMatchWithNullOrderQuantity_publishesNoMismatch() {
    service = newService();
    UUID clientRef = UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29");
    TransactionOrder order = sampleOrder(clientRef);
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.of(order));
    given(executionRepository.findByOrderId(123L)).willReturn(Optional.empty());

    service.reconcile(reportWithSingleRow(clientRef));

    verify(eventPublisher, never()).publishEvent(any(Object.class));
    verify(executionRepository).save(any(TransactionExecution.class));
  }

  @Test
  void reconcile_uuidMatchWithEtfQuantityWithinTolerance_publishesNoMismatch() {
    service = newService();
    UUID clientRef = UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29");
    TransactionOrder order = orderWith(clientRef, ETF, BUY, new BigDecimal("15007"), null);
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.of(order));
    given(executionRepository.findByOrderId(123L)).willReturn(Optional.empty());

    service.reconcile(reportWithSingleRow(clientRef));

    verify(eventPublisher, never()).publishEvent(any(Object.class));
    verify(executionRepository).save(any(TransactionExecution.class));
  }

  @Test
  void reconcile_uuidMatchedFundBuyWithDivergentAmount_publishesFundBuyAmountMismatch() {
    service = newService();
    UUID clientRef = UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29");
    TransactionOrder order =
        orderWith(clientRef, InstrumentType.FUND, BUY, null, new BigDecimal("60000.00"));
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.of(order));

    service.reconcile(reportWithSingleRow(clientRef));

    verify(eventPublisher)
        .publishEvent(
            argThat(
                (Object e) ->
                    e instanceof QuantityAmountMismatchEvent qe
                        && qe.kind() == QuantityAmountMismatchEvent.MismatchKind.FUND_BUY_AMOUNT
                        && qe.expected().compareTo(new BigDecimal("60000.00")) == 0
                        && qe.actual().compareTo(new BigDecimal("70915.58")) == 0));
    verify(executionRepository, never()).save(any());
    assertThat(order.getOrderStatus()).isEqualTo(SENT);
  }

  @Test
  void reconcile_uuidMatchedFundBuyDeltaExactlyTwoPercentOfOrderedAmount_publishesMismatch() {
    service = newService();
    UUID clientRef = UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29");
    // row total 70915.58, delta 1390.58: /ordered = 2.0001% (mismatch), /executed = 1.961% (not)
    TransactionOrder order =
        orderWith(clientRef, InstrumentType.FUND, BUY, null, new BigDecimal("69525.00"));
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.of(order));

    service.reconcile(reportWithSingleRow(clientRef));

    verify(eventPublisher)
        .publishEvent(
            argThat(
                (Object e) ->
                    e instanceof QuantityAmountMismatchEvent qe
                        && qe.kind() == QuantityAmountMismatchEvent.MismatchKind.FUND_BUY_AMOUNT
                        && qe.expected().compareTo(new BigDecimal("69525.00")) == 0
                        && qe.actual().compareTo(new BigDecimal("70915.58")) == 0));
    verify(executionRepository, never()).save(any());
    assertThat(order.getOrderStatus()).isEqualTo(SENT);
  }

  @Test
  void reconcile_uuidMatchedFundBuyWithinTwoPercentOfOrderedAmountOnly_publishesNoMismatch() {
    service = newService();
    UUID clientRef = UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29");
    // row total 70915.58, delta 1390.58: /ordered = 1.923% (ok), /executed would be 1.961% too,
    // so pick ordered above executed: ordered 72340.00, delta 1424.42:
    // /ordered = 1.969% (ok), /executed = 2.009% (old denominator would mismatch)
    TransactionOrder order =
        orderWith(clientRef, InstrumentType.FUND, BUY, null, new BigDecimal("72340.00"));
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.of(order));
    given(executionRepository.findByOrderId(123L)).willReturn(Optional.empty());

    service.reconcile(reportWithSingleRow(clientRef));

    verify(eventPublisher, never()).publishEvent(any(Object.class));
    verify(executionRepository).save(any(TransactionExecution.class));
    assertThat(order.getOrderStatus()).isEqualTo(EXECUTED);
  }

  @Test
  void reconcile_uuidMatchedFundSellWithDivergentQuantity_publishesFundSellQuantityMismatch() {
    service = newService();
    UUID clientRef = UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29");
    TransactionOrder order =
        orderWith(clientRef, InstrumentType.FUND, SELL, new BigDecimal("15000"), null);
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.of(order));

    Map<String, Object> sellRow = validRawRow(clientRef);
    sellRow.put("Buy/Sell", "Sell");
    service.reconcile(reportOf(sellRow));

    verify(eventPublisher)
        .publishEvent(
            argThat(
                (Object e) ->
                    e instanceof QuantityAmountMismatchEvent qe
                        && qe.kind() == QuantityAmountMismatchEvent.MismatchKind.FUND_SELL_QUANTITY
                        && qe.expected().compareTo(new BigDecimal("15000")) == 0
                        && qe.actual().compareTo(new BigDecimal("15007")) == 0));
    verify(executionRepository, never()).save(any());
    assertThat(order.getOrderStatus()).isEqualTo(SENT);
  }

  @Test
  void reconcile_neitherUuidNorComplexMatch_publishesNoEventAndDoesNotPersist() {
    service = newService();
    UUID clientRef = UUID.fromString("00000000-0000-0000-0000-000000000099");
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.empty());
    given(orderRepository.findByInstrumentIsin(any())).willReturn(List.of());

    service.reconcile(reportWithSingleRow(clientRef));

    verify(eventPublisher, org.mockito.Mockito.never()).publishEvent(any(Object.class));
    verify(executionRepository, org.mockito.Mockito.never()).save(any());
  }

  @Test
  void reconcile_malformedRow_isSkippedAndValidRowsStillProcessed() {
    service = newService();
    UUID clientRef = UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29");
    TransactionOrder order = sampleOrder(clientRef);
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.of(order));
    given(executionRepository.findByOrderId(123L)).willReturn(Optional.empty());

    Map<String, Object> malformed = new HashMap<>();
    malformed.put("ISIN", "IE000F60HVH9");
    malformed.put("Client ref", "not-a-uuid");
    malformed.put("Buy/Sell", "Buy");
    malformed.put("Our ref", "DLA9999999");

    Map<String, Object> validRow = validRawRow(clientRef);

    InvestmentReport report =
        InvestmentReport.builder()
            .provider(SEB)
            .reportType(PENDING_TRANSACTIONS)
            .reportDate(LocalDate.of(2026, 5, 13))
            .rawData(List.of(malformed, validRow))
            .build();

    service.reconcile(report);

    verify(executionRepository)
        .save(
            argThat(
                (TransactionExecution e) ->
                    e.getOrderId().equals(123L)
                        && "DLA0799512".equals(e.getBrokerTransactionId())));
    assertThat(order.getOrderStatus()).isEqualTo(EXECUTED);
  }

  @Test
  void reconcile_secondRunResolvesToDifferentOrder_doesNotRelink() {
    service = newService();
    UUID clientRef = UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29");
    TransactionOrder order = sampleOrder(clientRef);
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.of(order));

    // Existing execution is linked to a DIFFERENT order (order 555)
    TransactionExecution existingOnOtherOrder =
        TransactionExecution.builder().id(99L).orderId(555L).source("SEB_OOTEL").build();
    given(executionRepository.findAllByBrokerTransactionId("DLA0799512"))
        .willReturn(List.of(existingOnOtherOrder));

    service.reconcile(reportWithSingleRow(clientRef));

    // Defensive: do not silently re-link — refuse to write a second execution that conflicts.
    verify(executionRepository, org.mockito.Mockito.never()).save(any());
  }

  @Test
  void reconcile_matchedOrder_persistsExecutionMatchedAuditEvent() {
    service = newService();
    UUID clientRef = UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29");
    TransactionOrder order = sampleOrder(clientRef);
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.of(order));
    given(executionRepository.findByOrderId(123L)).willReturn(Optional.empty());

    service.reconcile(reportWithSingleRow(clientRef));

    verify(auditEventRepository)
        .save(
            argThat(
                (TransactionAuditEvent event) ->
                    "EXECUTION_MATCHED".equals(event.getEventType())
                        && event.getOrderId().equals(123L)
                        && "DLA0799512".equals(event.getPayload().get("ourRef"))
                        && "2026-05-13".equals(event.getPayload().get("reportDate"))));
  }

  @Test
  void reconcile_existingExecution_doesNotPersistExecutionMatchedAuditEventAgain() {
    service = newService();
    UUID clientRef = UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29");
    TransactionOrder order = sampleOrder(clientRef);
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.of(order));
    given(executionRepository.findByOrderId(123L))
        .willReturn(
            Optional.of(
                TransactionExecution.builder().id(99L).orderId(123L).source("SEB_OOTEL").build()));

    service.reconcile(reportWithSingleRow(clientRef));

    verify(auditEventRepository, never())
        .save(argThat(event -> "EXECUTION_MATCHED".equals(event.getEventType())));
  }

  @Test
  void reconcile_unmatchedRow_persistsUnmatchedAuditEventWithoutBatchAndOrder() {
    service = newService();
    UUID clientRef = UUID.fromString("00000000-0000-0000-0000-000000000099");
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.empty());
    given(orderRepository.findByInstrumentIsin(any())).willReturn(List.of());

    service.reconcile(reportWithSingleRow(clientRef));

    verify(auditEventRepository)
        .save(
            argThat(
                (TransactionAuditEvent event) ->
                    "UNMATCHED_SEB_TRANSACTION".equals(event.getEventType())
                        && event.getOrderId() == null
                        && event.getBatch() == null
                        && "DLA0799512".equals(event.getPayload().get("ourRef"))));
  }

  @Test
  void reconcile_nearMissOrder_persistsQuantityAmountMismatchAuditEvent() {
    service = newService();
    UUID clientRef = UUID.fromString("00000000-0000-0000-0000-000000000099");
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.empty());

    TransactionOrder order =
        TransactionOrder.builder()
            .id(123L)
            .fund(TKF100)
            .instrumentIsin("IE000F60HVH9")
            .transactionType(BUY)
            .instrumentType(ETF)
            .orderQuantity(new BigDecimal("15007"))
            .orderVenue(OrderVenue.SEB)
            .orderUuid(UUID.randomUUID())
            .orderStatus(SENT)
            .build();
    given(orderRepository.findByInstrumentIsin("IE000F60HVH9")).willReturn(List.of(order));
    given(executionRepository.findByOrderId(123L)).willReturn(Optional.empty());

    service.reconcile(reportWithSingleRowQuantity(clientRef, new BigDecimal("15007.0003")));

    verify(auditEventRepository)
        .save(
            argThat(
                (TransactionAuditEvent event) ->
                    "QUANTITY_AMOUNT_MISMATCH".equals(event.getEventType())
                        && event.getOrderId().equals(123L)
                        && "ETF_QUANTITY".equals(event.getPayload().get("kind"))));
  }

  @Test
  void reconcile_absentExecutedOrder_recordsSettlementAndAuditEvent() {
    service = newService();
    UUID clientRef = UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29");
    TransactionOrder matchedOrder = sampleOrder(clientRef);
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.of(matchedOrder));
    given(executionRepository.findByOrderId(123L)).willReturn(Optional.empty());

    TransactionOrder absentOrder = executedOrder(456L);
    given(orderRepository.findByOrderStatusIn(anyCollection())).willReturn(List.of(absentOrder));
    given(executionRepository.findByOrderId(456L))
        .willReturn(Optional.of(executionWithTradeInstant(456L, "2026-05-11T10:00:00Z")));
    given(settlementRepository.save(any()))
        .willAnswer(invocation -> invocation.getArgument(0, TransactionSettlement.class));

    service.reconcile(reportWithSingleRow(clientRef));

    verify(settlementRepository)
        .save(
            argThat(
                (TransactionSettlement settlement) ->
                    settlement.getOrderId().equals(456L)
                        && settlement.getReportDate().equals(LocalDate.of(2026, 5, 13))));
    assertThat(absentOrder.getOrderStatus()).isEqualTo(SETTLED);
    verify(auditEventRepository)
        .save(
            argThat(
                (TransactionAuditEvent event) ->
                    "SETTLEMENT_DETECTED".equals(event.getEventType())
                        && event.getOrderId().equals(456L)
                        && "2026-05-13".equals(event.getPayload().get("reportDate"))));
  }

  @Test
  void reconcile_zeroMatchesOnNonEmptyReport_skipsSettlementDetection() {
    service = newService();
    UUID clientRef = UUID.fromString("00000000-0000-0000-0000-000000000099");
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.empty());
    given(orderRepository.findByInstrumentIsin(any())).willReturn(List.of());

    service.reconcile(reportWithSingleRow(clientRef));

    verify(orderRepository, never()).findByOrderStatusIn(anyCollection());
    verify(settlementRepository, never()).save(any());
  }

  @Test
  void reconcile_emptyReport_skipsSettlementDetection() {
    service = newService();
    InvestmentReport report =
        InvestmentReport.builder()
            .provider(SEB)
            .reportType(PENDING_TRANSACTIONS)
            .reportDate(LocalDate.of(2026, 5, 13))
            .rawData(List.of())
            .build();

    service.reconcile(report);

    verify(orderRepository, never()).findByOrderStatusIn(anyCollection());
    verify(settlementRepository, never()).save(any());
  }

  @Test
  void reconcile_settledOrderReappears_recordsReappearanceWithoutDuplicateSettlement() {
    service = newService();
    UUID clientRef = UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29");
    TransactionOrder settledOrder = sampleOrder(clientRef);
    settledOrder.setOrderStatus(SETTLED);
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.of(settledOrder));
    given(settlementRepository.findByOrderId(123L))
        .willReturn(Optional.of(settlement(123L, LocalDate.of(2026, 5, 10))));

    service.reconcile(reportWithSingleRow(clientRef));

    verify(auditEventRepository)
        .save(
            argThat(
                (TransactionAuditEvent event) ->
                    "SETTLEMENT_REAPPEARED".equals(event.getEventType())
                        && event.getOrderId().equals(123L)
                        && "2026-05-10".equals(event.getPayload().get("settlementReportDate"))));
    verify(executionRepository, never()).save(any());
    verify(settlementRepository, never()).save(any());
    assertThat(settledOrder.getOrderStatus()).isEqualTo(SETTLED);
  }

  @Test
  void reconcile_settledOrderInReplayedOlderReport_doesNotRecordReappearance() {
    service = newService();
    UUID clientRef = UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29");
    TransactionOrder settledOrder = sampleOrder(clientRef);
    settledOrder.setOrderStatus(SETTLED);
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.of(settledOrder));
    // Settled based on a LATER report — this run is a lookback replay of an older report
    given(settlementRepository.findByOrderId(123L))
        .willReturn(Optional.of(settlement(123L, LocalDate.of(2026, 5, 14))));

    service.reconcile(reportWithSingleRow(clientRef));

    verify(auditEventRepository, never())
        .save(argThat(event -> "SETTLEMENT_REAPPEARED".equals(event.getEventType())));
    verify(executionRepository, never()).save(any());
  }

  @Test
  void reconcile_executedOrderWithoutExecution_absentFromReport_isNotSettled() {
    service = newService();
    UUID clientRef = UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29");
    TransactionOrder matchedOrder = sampleOrder(clientRef);
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.of(matchedOrder));
    given(executionRepository.findByOrderId(123L)).willReturn(Optional.empty());

    TransactionOrder absentOrder = executedOrder(456L);
    given(orderRepository.findByOrderStatusIn(anyCollection())).willReturn(List.of(absentOrder));
    given(executionRepository.findByOrderId(456L)).willReturn(Optional.empty());

    service.reconcile(reportWithSingleRow(clientRef));

    verify(settlementRepository, never()).save(any());
    assertThat(absentOrder.getOrderStatus()).isEqualTo(EXECUTED);
  }

  @Test
  void reconcile_orderExecutedOnReportDate_isNotSettled() {
    service = newService();
    UUID clientRef = UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29");
    TransactionOrder matchedOrder = sampleOrder(clientRef);
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.of(matchedOrder));
    given(executionRepository.findByOrderId(123L)).willReturn(Optional.empty());

    TransactionOrder absentOrder = executedOrder(456L);
    given(orderRepository.findByOrderStatusIn(anyCollection())).willReturn(List.of(absentOrder));
    given(executionRepository.findByOrderId(456L))
        .willReturn(Optional.of(executionWithTradeInstant(456L, "2026-05-13T10:00:00Z")));

    service.reconcile(reportWithSingleRow(clientRef));

    verify(settlementRepository, never()).save(any());
  }

  @Test
  void reconcile_orderReferencedByOurRefInReport_isNotSettled() {
    service = newService();
    UUID clientRef = UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29");
    TransactionOrder matchedOrder = sampleOrder(clientRef);
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.of(matchedOrder));
    given(executionRepository.findByOrderId(123L)).willReturn(Optional.empty());

    // Second row carries no client ref and matches nothing, but its Our ref points at order 456
    UUID secondClientRef = UUID.fromString("00000000-0000-0000-0000-000000000099");
    given(orderRepository.findByOrderUuid(secondClientRef)).willReturn(Optional.empty());
    given(orderRepository.findByInstrumentIsin(any())).willReturn(List.of());
    given(executionRepository.findAllByBrokerTransactionId("DLA0799512")).willReturn(List.of());
    given(executionRepository.findAllByBrokerTransactionId("DLA0888888"))
        .willReturn(List.of(executionWithTradeInstant(456L, "2026-05-11T10:00:00Z")));

    TransactionOrder presentByOurRef = executedOrder(456L);
    given(orderRepository.findByOrderStatusIn(anyCollection()))
        .willReturn(List.of(presentByOurRef));

    Map<String, Object> secondRow = validRawRow(secondClientRef);
    secondRow.put("Our ref", "DLA0888888");
    InvestmentReport report =
        InvestmentReport.builder()
            .provider(SEB)
            .reportType(PENDING_TRANSACTIONS)
            .reportDate(LocalDate.of(2026, 5, 13))
            .rawData(List.of(validRawRow(clientRef), secondRow))
            .build();

    service.reconcile(report);

    verify(settlementRepository, never()).save(any());
    assertThat(presentByOurRef.getOrderStatus()).isEqualTo(EXECUTED);
  }

  @Test
  void reconcile_nonLatestReplayedReport_skipsSettlementByAbsence() {
    service = newService();
    UUID clientRef = UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29");
    TransactionOrder matchedOrder = sampleOrder(clientRef);
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.of(matchedOrder));
    given(executionRepository.findByOrderId(123L)).willReturn(Optional.empty());

    // A newer pending report exists than the one being reconciled (lookback replay of an older one)
    given(reportService.getLatestReport(SEB, PENDING_TRANSACTIONS))
        .willReturn(
            Optional.of(
                InvestmentReport.builder()
                    .provider(SEB)
                    .reportType(PENDING_TRANSACTIONS)
                    .reportDate(LocalDate.of(2026, 5, 14))
                    .rawData(List.of())
                    .build()));

    service.reconcile(reportWithSingleRow(clientRef)); // reportDate 2026-05-13

    verify(orderRepository, never()).findByOrderStatusIn(anyCollection());
    verify(settlementRepository, never()).save(any());
  }

  @Test
  void reconcile_reportWithMalformedRow_skipsSettlementByAbsence() {
    service = newService();
    UUID clientRef = UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29");
    TransactionOrder matchedOrder = sampleOrder(clientRef);
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.of(matchedOrder));
    given(executionRepository.findByOrderId(123L)).willReturn(Optional.empty());

    Map<String, Object> malformed = new HashMap<>();
    malformed.put("ISIN", "IE000F60HVH9");
    malformed.put("Client ref", "not-a-uuid");
    malformed.put("Buy/Sell", "Buy");
    malformed.put("Our ref", "DLA9999999");

    InvestmentReport report =
        InvestmentReport.builder()
            .provider(SEB)
            .reportType(PENDING_TRANSACTIONS)
            .reportDate(LocalDate.of(2026, 5, 13))
            .rawData(List.of(validRawRow(clientRef), malformed))
            .build();

    service.reconcile(report);

    verify(orderRepository, never()).findByOrderStatusIn(anyCollection());
    verify(settlementRepository, never()).save(any());
  }

  @Test
  void reconcile_existingExecutionFieldsChanged_recordsExecutionUpdatedAudit() {
    service = newService();
    UUID clientRef = UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29");
    TransactionOrder order = sampleOrder(clientRef);
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.of(order));
    TransactionExecution existing =
        TransactionExecution.builder()
            .id(99L)
            .orderId(123L)
            .source("SEB_OOTEL")
            .brokerTransactionId("OLD")
            .executedQuantity(new BigDecimal("1"))
            .build();
    given(executionRepository.findByOrderId(123L)).willReturn(Optional.of(existing));

    service.reconcile(reportWithSingleRow(clientRef));

    verify(auditEventRepository)
        .save(
            argThat(
                (TransactionAuditEvent event) ->
                    "EXECUTION_UPDATED".equals(event.getEventType())
                        && event.getOrderId().equals(123L)
                        && "DLA0799512".equals(event.getPayload().get("ourRef"))));
  }

  @Test
  void reconcile_existingExecutionUnchanged_doesNotRecordExecutionUpdatedAudit() {
    service = newService();
    UUID clientRef = UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29");
    TransactionOrder order = sampleOrder(clientRef);
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.of(order));
    TransactionExecution existing =
        mapper.toExecution(SebPendingTransactionRow.fromRawData(validRawRow(clientRef)), order);
    existing.setId(99L);
    given(executionRepository.findByOrderId(123L)).willReturn(Optional.of(existing));

    service.reconcile(reportWithSingleRow(clientRef));

    verify(auditEventRepository, never())
        .save(argThat(event -> "EXECUTION_UPDATED".equals(event.getEventType())));
  }

  @Test
  void reconcile_ambiguousBrokerRef_refusesMatchAndDoesNotPersist() {
    service = newService();
    UUID clientRef = UUID.fromString("00000000-0000-0000-0000-000000000099");
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.empty());
    // ourRef DLA0799512 resolves to TWO executions → ambiguous → refuse the broker-ref tier
    given(executionRepository.findAllByBrokerTransactionId("DLA0799512"))
        .willReturn(
            List.of(
                TransactionExecution.builder().id(1L).orderId(11L).build(),
                TransactionExecution.builder().id(2L).orderId(22L).build()));
    given(orderRepository.findByInstrumentIsin(any())).willReturn(List.of());

    service.reconcile(reportWithSingleRow(clientRef));

    verify(orderRepository, never()).findById(any());
    verify(executionRepository, never()).save(any());
  }

  private static TransactionOrder executedOrder(Long id) {
    return TransactionOrder.builder()
        .id(id)
        .fund(TKF100)
        .instrumentIsin("IE00BFG1TM61")
        .transactionType(BUY)
        .instrumentType(ETF)
        .orderQuantity(new BigDecimal("100"))
        .orderVenue(OrderVenue.SEB)
        .orderUuid(UUID.randomUUID())
        .orderStatus(EXECUTED)
        .build();
  }

  private static TransactionExecution executionWithTradeInstant(Long orderId, String instant) {
    return TransactionExecution.builder()
        .id(orderId + 1000)
        .orderId(orderId)
        .source("SEB_OOTEL")
        .executionTimestamp(Instant.parse(instant))
        .build();
  }

  private static TransactionSettlement settlement(Long orderId, LocalDate reportDate) {
    return TransactionSettlement.builder()
        .id(7L)
        .orderId(orderId)
        .settledAt(reportDate.atStartOfDay().toInstant(ZoneOffset.UTC))
        .reportDate(reportDate)
        .build();
  }

  private static TransactionOrder sampleComplexMatchOrder(UUID clientRef) {
    return TransactionOrder.builder()
        .id(123L)
        .fund(TKF100)
        .instrumentIsin("IE000F60HVH9")
        .transactionType(BUY)
        .instrumentType(ETF)
        .orderQuantity(new BigDecimal("15007"))
        .orderVenue(OrderVenue.SEB)
        .orderUuid(clientRef)
        .orderStatus(SENT)
        .build();
  }

  private static TransactionOrder orderWith(
      UUID clientRef,
      InstrumentType instrumentType,
      TransactionType transactionType,
      BigDecimal orderQuantity,
      BigDecimal orderAmount) {
    return TransactionOrder.builder()
        .id(123L)
        .fund(TKF100)
        .instrumentIsin("IE000F60HVH9")
        .transactionType(transactionType)
        .instrumentType(instrumentType)
        .orderQuantity(orderQuantity)
        .orderAmount(orderAmount)
        .orderVenue(OrderVenue.SEB)
        .orderUuid(clientRef)
        .orderStatus(SENT)
        .build();
  }

  private static TransactionOrder sampleOrder(UUID clientRef) {
    return TransactionOrder.builder()
        .id(123L)
        .fund(TKF100)
        .instrumentIsin("IE000F60HVH9")
        .transactionType(BUY)
        .instrumentType(ETF)
        .orderVenue(OrderVenue.SEB)
        .orderUuid(clientRef)
        .orderStatus(SENT)
        .build();
  }

  private static InvestmentReport reportWithSingleRowQuantity(UUID clientRef, BigDecimal quantity) {
    Map<String, Object> raw = new HashMap<>();
    raw.put("ISIN", "IE000F60HVH9");
    raw.put("Price", new BigDecimal("4.7255"));
    raw.put("Total", new BigDecimal("70915.58"));
    raw.put("Account", "VP68168");
    raw.put("Our ref", "DLA0799512");
    raw.put("Buy/Sell", "Buy");
    raw.put("Quantity", quantity);
    raw.put("Broker fee", new BigDecimal("0.00"));
    if (clientRef != null) {
      raw.put("Client ref", clientRef.toString());
    }
    raw.put("Trade date", "2026-05-11T10:26:04Z");
    raw.put("Settlement date", "2026-05-13");
    raw.put("Settlement amount", new BigDecimal("70915.58"));
    raw.put("Client name", "Tuleva Täiendav Kogumisfond");
    raw.put("Instrument name", "ICAV Amundi MSCI USA Screened UCITS ETF");
    return InvestmentReport.builder()
        .provider(SEB)
        .reportType(PENDING_TRANSACTIONS)
        .reportDate(LocalDate.of(2026, 5, 13))
        .rawData(List.of(raw))
        .build();
  }

  private static InvestmentReport reportWithSingleRow(UUID clientRef) {
    return reportOf(validRawRow(clientRef));
  }

  private static InvestmentReport reportOf(Map<String, Object> raw) {
    return InvestmentReport.builder()
        .provider(SEB)
        .reportType(PENDING_TRANSACTIONS)
        .reportDate(LocalDate.of(2026, 5, 13))
        .rawData(List.of(raw))
        .build();
  }

  private static Map<String, Object> validRawRow(UUID clientRef) {
    Map<String, Object> raw = new HashMap<>();
    raw.put("ISIN", "IE000F60HVH9");
    raw.put("Price", new BigDecimal("4.7255"));
    raw.put("Total", new BigDecimal("70915.58"));
    raw.put("Account", "VP68168");
    raw.put("Our ref", "DLA0799512");
    raw.put("Buy/Sell", "Buy");
    raw.put("Quantity", new BigDecimal("15007"));
    raw.put("Broker fee", new BigDecimal("0.00"));
    if (clientRef != null) {
      raw.put("Client ref", clientRef.toString());
    }
    raw.put("Trade date", "2026-05-11T10:26:04Z");
    raw.put("Settlement date", "2026-05-13");
    raw.put("Settlement amount", new BigDecimal("70915.58"));
    raw.put("Client name", "Tuleva Täiendav Kogumisfond");
    raw.put("Instrument name", "ICAV Amundi MSCI USA Screened UCITS ETF");
    return raw;
  }
}
