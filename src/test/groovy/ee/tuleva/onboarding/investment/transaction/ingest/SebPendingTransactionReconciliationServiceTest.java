package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SENT;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.transaction.OrderVenue;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
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
  @Mock private ApplicationEventPublisher eventPublisher;

  // Real collaborators so we exercise the actual extraction + mapping pipeline
  private final SebPendingTransactionExtractor extractor = new SebPendingTransactionExtractor();
  private final TransactionExecutionMapper mapper = new TransactionExecutionMapper();

  private SebPendingTransactionReconciliationService service;

  private SebPendingTransactionReconciliationService newService() {
    SebClientNameToFundResolver resolver = new SebClientNameToFundResolver();
    return new SebPendingTransactionReconciliationService(
        extractor,
        new SebPendingTransactionMatcher(orderRepository),
        new SebPendingTransactionComplexMatcher(orderRepository, executionRepository, resolver),
        mapper,
        executionRepository,
        orderRepository,
        eventPublisher);
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
  void reconcile_unmatchedRow_publishesUnmatchedEventAndDoesNotPersistExecution() {
    service = newService();
    UUID clientRef = UUID.fromString("00000000-0000-0000-0000-000000000099");
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.empty());

    InvestmentReport report = reportWithSingleRow(clientRef);
    service.reconcile(report);

    verify(eventPublisher)
        .publishEvent(
            argThat(
                (Object e) ->
                    e instanceof UnmatchedPendingTransactionEvent ue
                        && ue.reportDate().equals(report.getReportDate())
                        && clientRef.equals(ue.row().clientRef())));

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
    verify(eventPublisher, org.mockito.Mockito.never())
        .publishEvent(any(UnmatchedPendingTransactionEvent.class));
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
    verify(eventPublisher, org.mockito.Mockito.never())
        .publishEvent(any(UnmatchedPendingTransactionEvent.class));
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
            .orderQuantity(15007L)
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
                        && qe.nearMissOrder().getId().equals(123L)
                        && qe.kind() == QuantityAmountMismatchEvent.MismatchKind.ETF_QUANTITY));
    verify(eventPublisher, org.mockito.Mockito.never())
        .publishEvent(any(UnmatchedPendingTransactionEvent.class));
    verify(executionRepository, org.mockito.Mockito.never()).save(any());
  }

  @Test
  void reconcile_neitherUuidNorComplexMatch_publishesUnmatchedEvent() {
    service = newService();
    UUID clientRef = UUID.fromString("00000000-0000-0000-0000-000000000099");
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.empty());
    given(orderRepository.findByInstrumentIsin(any())).willReturn(List.of());

    service.reconcile(reportWithSingleRow(clientRef));

    verify(eventPublisher).publishEvent(any(UnmatchedPendingTransactionEvent.class));
    verify(executionRepository, org.mockito.Mockito.never()).save(any());
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
    given(executionRepository.findByBrokerTransactionId("DLA0799512"))
        .willReturn(Optional.of(existingOnOtherOrder));

    service.reconcile(reportWithSingleRow(clientRef));

    // Defensive: do not silently re-link — refuse to write a second execution that conflicts.
    verify(executionRepository, org.mockito.Mockito.never()).save(any());
  }

  private static TransactionOrder sampleComplexMatchOrder(UUID clientRef) {
    return TransactionOrder.builder()
        .id(123L)
        .fund(TKF100)
        .instrumentIsin("IE000F60HVH9")
        .transactionType(BUY)
        .instrumentType(ETF)
        .orderQuantity(15007L)
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
    return InvestmentReport.builder()
        .provider(SEB)
        .reportType(PENDING_TRANSACTIONS)
        .reportDate(LocalDate.of(2026, 5, 13))
        .rawData(List.of(raw))
        .build();
  }
}
