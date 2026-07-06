package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SENT;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SETTLED;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.report.CsvToJsonConverter;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportRepository;
import ee.tuleva.onboarding.investment.transaction.OrderVenue;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEvent;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEventRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionBatch;
import ee.tuleva.onboarding.investment.transaction.TransactionBatchRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionSettlement;
import ee.tuleva.onboarding.investment.transaction.TransactionSettlementRepository;
import jakarta.persistence.EntityManager;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
class SebPendingTransactionReconciliationIT {

  private static final UUID FIXTURE_CLIENT_REF =
      UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Autowired private SebPendingTransactionReconciliationService reconciliationService;
  @Autowired private InvestmentReportRepository reportRepository;
  @Autowired private TransactionBatchRepository batchRepository;
  @Autowired private TransactionOrderRepository orderRepository;
  @Autowired private TransactionExecutionRepository executionRepository;
  @Autowired private TransactionAuditEventRepository auditEventRepository;
  @Autowired private TransactionSettlementRepository settlementRepository;
  @Autowired private CsvToJsonConverter csvConverter;
  @Autowired private EntityManager entityManager;

  private TransactionOrder order;
  private InvestmentReport report;

  @BeforeEach
  void seed() {
    TransactionBatch batch =
        batchRepository.save(TransactionBatch.builder().fund(TKF100).createdBy("test").build());
    order =
        orderRepository.save(
            TransactionOrder.builder()
                .batch(batch)
                .fund(TKF100)
                .instrumentIsin("IE00BFG1TM61")
                .transactionType(BUY)
                .instrumentType(FUND)
                .orderQuantity(new BigDecimal("2670"))
                .orderVenue(OrderVenue.SEB)
                .orderUuid(FIXTURE_CLIENT_REF)
                .orderStatus(SENT)
                .build());

    List<Map<String, Object>> rawData = loadFixtureRawData();
    rawData.get(0).put("Client ref", FIXTURE_CLIENT_REF.toString());

    report =
        reportRepository.save(
            InvestmentReport.builder()
                .provider(SEB)
                .reportType(PENDING_TRANSACTIONS)
                .reportDate(LocalDate.of(2026, 2, 13))
                .rawData(rawData)
                .metadata(Map.of("source", "fixture"))
                .createdAt(Instant.now())
                .build());
  }

  @Test
  void reconcile_matchedClientRef_writesExecutionAndTransitionsOrder() {
    reconciliationService.reconcile(report);
    entityManager.flush();
    entityManager.clear();

    TransactionExecution execution = executionRepository.findAllByOrderId(order.getId()).getFirst();
    assertThat(execution.getBrokerTransactionId()).isEqualTo("DLA0000000");
    assertThat(execution.getExecutedQuantity()).isEqualByComparingTo("2669.9");
    assertThat(execution.getUnitPrice()).isEqualByComparingTo("34.37656841");
    assertThat(execution.getTotalConsideration()).isEqualByComparingTo("91782.00");
    assertThat(execution.getActualSettlementDate()).isEqualTo(LocalDate.of(2026, 2, 17));
    assertThat(execution.getExecutionTimestamp()).isEqualTo(Instant.parse("2026-02-10T16:06:58Z"));
    assertThat(execution.getSource()).isEqualTo("SEB_OOTEL");

    TransactionOrder reloaded = orderRepository.findById(order.getId()).orElseThrow();
    assertThat(reloaded.getOrderStatus()).isEqualTo(EXECUTED);
  }

  @Test
  void reconcile_rerun_isIdempotent() {
    reconciliationService.reconcile(report);
    entityManager.flush();
    Long firstExecutionId = executionRepository.findAllByOrderId(order.getId()).getFirst().getId();

    reconciliationService.reconcile(report);
    entityManager.flush();

    Long secondExecutionId = executionRepository.findAllByOrderId(order.getId()).getFirst().getId();
    assertThat(secondExecutionId).isEqualTo(firstExecutionId);
    assertThat(executionRepository.findAll())
        .filteredOn(e -> e.getOrderId().equals(order.getId()))
        .hasSize(1);
  }

  @Test
  void reconcile_unknownClientRefButKnownOurRef_rematchesViaBrokerRefTier() {
    reconciliationService.reconcile(report);
    entityManager.flush();
    Long firstExecutionId = executionRepository.findAllByOrderId(order.getId()).getFirst().getId();

    report.getRawData().get(0).put("Client ref", "");
    reportRepository.save(report);
    entityManager.flush();

    reconciliationService.reconcile(report);
    entityManager.flush();
    entityManager.clear();

    TransactionExecution execution = executionRepository.findAllByOrderId(order.getId()).getFirst();
    assertThat(execution.getId()).isEqualTo(firstExecutionId);
    assertThat(executionRepository.findAll())
        .filteredOn(e -> e.getOrderId().equals(order.getId()))
        .hasSize(1);
    assertThat(auditEventRepository.findByEventType("UNMATCHED_SEB_TRANSACTION")).isEmpty();
    TransactionOrder reloaded = orderRepository.findById(order.getId()).orElseThrow();
    assertThat(reloaded.getOrderStatus()).isEqualTo(EXECUTED);
  }

  @Test
  void reconcile_matchedFundBuyWithDivergentAmount_persistsMismatchAuditAndQuarantines() {
    order.setOrderAmount(new BigDecimal("50000.00"));
    orderRepository.save(order);
    entityManager.flush();

    reconciliationService.reconcile(report);
    entityManager.flush();
    entityManager.clear();

    List<TransactionAuditEvent> mismatches =
        auditEventRepository.findByOrderIdAndEventType(order.getId(), "QUANTITY_AMOUNT_MISMATCH");
    assertThat(mismatches).hasSize(1);
    assertThat(mismatches.get(0).getPayload().get("kind")).isEqualTo("FUND_BUY_AMOUNT");

    // Quarantine: a divergent fill is flagged but not absorbed — no execution, order stays SENT.
    assertThat(executionRepository.findAllByOrderId(order.getId())).isEmpty();
    TransactionOrder reloaded = orderRepository.findById(order.getId()).orElseThrow();
    assertThat(reloaded.getOrderStatus()).isEqualTo(SENT);
  }

  @Test
  void reconcile_clientRefWithNoOrder_doesNotPersistExecution() {
    report.getRawData().get(0).put("Client ref", "99999999-9999-9999-9999-999999999999");
    reportRepository.save(report);
    entityManager.flush();

    reconciliationService.reconcile(report);
    entityManager.flush();

    assertThat(executionRepository.findAllByOrderId(order.getId())).isEmpty();
    TransactionOrder reloaded = orderRepository.findById(order.getId()).orElseThrow();
    assertThat(reloaded.getOrderStatus()).isEqualTo(SENT);
  }

  @Test
  void reconcile_matchedRow_persistsExecutionMatchedAuditEventOnceAcrossReruns() {
    reconciliationService.reconcile(report);
    reconciliationService.reconcile(report);
    entityManager.flush();
    entityManager.clear();

    List<TransactionAuditEvent> events =
        auditEventRepository.findByOrderIdAndEventType(order.getId(), "EXECUTION_MATCHED");

    assertThat(events).hasSize(1);
    assertThat(events.get(0).getPayload().get("ourRef")).isEqualTo("DLA0000000");
    assertThat(events.get(0).getPayload().get("reportDate")).isEqualTo("2026-02-13");
  }

  @Test
  void reconcile_unknownClientRef_persistsUnmatchedAuditEventWithoutBatchOnceAcrossReruns() {
    report.getRawData().get(0).put("Client ref", "99999999-9999-9999-9999-999999999999");
    reportRepository.save(report);
    entityManager.flush();

    reconciliationService.reconcile(report);
    reconciliationService.reconcile(report);
    entityManager.flush();
    entityManager.clear();

    List<TransactionAuditEvent> events =
        auditEventRepository.findByEventType("UNMATCHED_SEB_TRANSACTION");

    assertThat(events).hasSize(1);
    assertThat(events.get(0).getBatch()).isNull();
    assertThat(events.get(0).getOrderId()).isNull();
    assertThat(events.get(0).getPayload().get("ourRef")).isEqualTo("DLA0000000");
  }

  @Test
  void reconcile_executedOrderAbsentFromReport_settlesAndPersistsAuditEvent() {
    TransactionOrder absentOrder = seedExecutedOrderAbsentFromReport();

    reconciliationService.reconcile(report);
    entityManager.flush();
    entityManager.clear();

    TransactionOrder reloaded = orderRepository.findById(absentOrder.getId()).orElseThrow();
    assertThat(reloaded.getOrderStatus()).isEqualTo(SETTLED);

    TransactionSettlement settlement =
        settlementRepository.findByOrderId(absentOrder.getId()).orElseThrow();
    assertThat(settlement.getReportDate()).isEqualTo(LocalDate.of(2026, 2, 13));

    List<TransactionAuditEvent> events =
        auditEventRepository.findByOrderIdAndEventType(absentOrder.getId(), "SETTLEMENT_DETECTED");
    assertThat(events).hasSize(1);
    assertThat(events.get(0).getPayload().get("reportDate")).isEqualTo("2026-02-13");
  }

  @Test
  void reconcile_settledOrderReappearsInLaterReport_persistsReappearanceWithoutDuplicate() {
    order.setOrderStatus(SETTLED);
    orderRepository.save(order);
    settlementRepository.save(
        TransactionSettlement.builder()
            .orderId(order.getId())
            .settledAt(Instant.parse("2026-02-11T07:00:00Z"))
            .reportDate(LocalDate.of(2026, 2, 11))
            .build());
    entityManager.flush();

    reconciliationService.reconcile(report);
    entityManager.flush();
    entityManager.clear();

    List<TransactionAuditEvent> events =
        auditEventRepository.findByOrderIdAndEventType(order.getId(), "SETTLEMENT_REAPPEARED");
    assertThat(events).hasSize(1);
    assertThat(events.get(0).getPayload().get("settlementReportDate")).isEqualTo("2026-02-11");

    assertThat(settlementRepository.findAll())
        .filteredOn(settlement -> settlement.getOrderId().equals(order.getId()))
        .hasSize(1);
    TransactionOrder reloaded = orderRepository.findById(order.getId()).orElseThrow();
    assertThat(reloaded.getOrderStatus()).isEqualTo(SETTLED);
  }

  @Test
  void reconcile_splitOrderAcrossFourPieces_writesFourExecutionsSummingToTargetAndDoesNotSettle() {
    TransactionBatch batch =
        batchRepository.save(TransactionBatch.builder().fund(TKF100).createdBy("test").build());
    UUID splitClientRef = UUID.fromString("4ffa4ff5-2081-48c2-98dc-d72730955b33");
    TransactionOrder splitOrder =
        orderRepository.save(
            TransactionOrder.builder()
                .batch(batch)
                .fund(TKF100)
                .instrumentIsin("IE000I9HGDZ3")
                .transactionType(BUY)
                .instrumentType(ETF)
                .orderQuantity(new BigDecimal("16508198"))
                .orderVenue(OrderVenue.SEB)
                .orderUuid(splitClientRef)
                .orderStatus(SENT)
                .build());

    InvestmentReport splitReport =
        reportRepository.save(
            InvestmentReport.builder()
                .provider(SEB)
                .reportType(PENDING_TRANSACTIONS)
                .reportDate(LocalDate.of(2026, 6, 24))
                .rawData(
                    List.of(
                        splitRow(splitClientRef, "DLA0933862", "16031199"),
                        splitRow(splitClientRef, "DLA0935620", "34985"),
                        splitRow(splitClientRef, "DLA0936075", "377403"),
                        splitRow(splitClientRef, "DLA0927877", "64611")))
                .metadata(Map.of("source", "fixture"))
                .createdAt(Instant.now())
                .build());

    reconciliationService.reconcile(splitReport);
    entityManager.flush();
    entityManager.clear();

    List<TransactionExecution> executions =
        executionRepository.findAllByOrderId(splitOrder.getId());
    assertThat(executions).hasSize(4);
    assertThat(executions)
        .extracting(TransactionExecution::getBrokerTransactionId)
        .containsExactlyInAnyOrder("DLA0933862", "DLA0935620", "DLA0936075", "DLA0927877");
    assertThat(executions)
        .allSatisfy(e -> assertThat(e.getAggregatedOrderId()).isEqualTo(splitClientRef));
    assertThat(
            executions.stream()
                .map(TransactionExecution::getExecutedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
        .isEqualByComparingTo("16508198");

    TransactionOrder reloaded = orderRepository.findById(splitOrder.getId()).orElseThrow();
    assertThat(reloaded.getOrderStatus()).isEqualTo(EXECUTED);
    assertThat(settlementRepository.findByOrderId(splitOrder.getId())).isEmpty();
    assertThat(
            auditEventRepository.findByOrderIdAndEventType(
                splitOrder.getId(), "QUANTITY_AMOUNT_MISMATCH"))
        .isEmpty();
  }

  @Test
  void reconcile_splitOrderWithDivergentPiecePrices_publishesPriceConsistencyAlert(
      ApplicationEvents events) {
    TransactionBatch batch =
        batchRepository.save(TransactionBatch.builder().fund(TKF100).createdBy("test").build());
    UUID clientRef = UUID.fromString("5ffa4ff5-2081-48c2-98dc-d72730955b33");
    TransactionOrder order =
        orderRepository.save(
            TransactionOrder.builder()
                .batch(batch)
                .fund(TKF100)
                .instrumentIsin("IE000I9HGDZ3")
                .transactionType(BUY)
                .instrumentType(ETF)
                .orderQuantity(new BigDecimal("200000"))
                .orderVenue(OrderVenue.SEB)
                .orderUuid(clientRef)
                .orderStatus(SENT)
                .build());

    InvestmentReport report =
        reportRepository.save(
            InvestmentReport.builder()
                .provider(SEB)
                .reportType(PENDING_TRANSACTIONS)
                .reportDate(LocalDate.of(2026, 6, 24))
                .rawData(
                    List.of(
                        splitRowAtPrice(clientRef, "DLA1000001", "100000", "9.99"),
                        splitRowAtPrice(clientRef, "DLA1000002", "100000", "10.40")))
                .metadata(Map.of("source", "fixture"))
                .createdAt(Instant.now())
                .build());

    reconciliationService.reconcile(report);

    List<ExecutionPriceConsistencyEvent> alerts =
        events.stream(ExecutionPriceConsistencyEvent.class).toList();
    assertThat(alerts).hasSize(1);
    ExecutionPriceConsistencyEvent alert = alerts.getFirst();
    assertThat(alert.orderId()).isEqualTo(order.getId());
    assertThat(alert.isin()).isEqualTo("IE000I9HGDZ3");
    assertThat(alert.minUnitPrice()).isEqualByComparingTo("9.99");
    assertThat(alert.maxUnitPrice()).isEqualByComparingTo("10.40");
    assertThat(alert.reportDate()).isEqualTo(LocalDate.of(2026, 6, 24));
  }

  private static Map<String, Object> splitRowAtPrice(
      UUID clientRef, String ourRef, String quantity, String price) {
    Map<String, Object> raw = splitRow(clientRef, ourRef, quantity);
    BigDecimal qty = new BigDecimal(quantity);
    BigDecimal unitPrice = new BigDecimal(price);
    raw.put("Price", unitPrice);
    raw.put("Total", qty.multiply(unitPrice));
    raw.put("Settlement amount", qty.multiply(unitPrice));
    return raw;
  }

  private static Map<String, Object> splitRow(UUID clientRef, String ourRef, String quantity) {
    BigDecimal qty = new BigDecimal(quantity);
    BigDecimal amount = qty.multiply(new BigDecimal("9.999"));
    Map<String, Object> raw = new HashMap<>();
    raw.put("ISIN", "IE000I9HGDZ3");
    raw.put("Price", new BigDecimal("9.999"));
    raw.put("Total", amount);
    raw.put("Account", "VP68959");
    raw.put("Our ref", ourRef);
    raw.put("Buy/Sell", "Buy");
    raw.put("Quantity", qty);
    raw.put("Broker fee", new BigDecimal("0.00"));
    raw.put("Client ref", clientRef.toString());
    raw.put("Trade date", "2026-06-22T10:00:00Z");
    raw.put("Settlement date", "2026-06-24");
    raw.put("Settlement amount", amount);
    raw.put(
        "Client name", "Tuleva Täiendav Kogumisfond"); // TKF100, matching the split order's fund
    raw.put("Instrument name", "Xtrackers MSCI World Screened UCITS ETF 1C");
    return raw;
  }

  private TransactionOrder seedExecutedOrderAbsentFromReport() {
    TransactionBatch batch =
        batchRepository.save(TransactionBatch.builder().fund(TKF100).createdBy("test").build());
    TransactionOrder absentOrder =
        orderRepository.save(
            TransactionOrder.builder()
                .batch(batch)
                .fund(TKF100)
                .instrumentIsin("IE000F60HVH9")
                .transactionType(BUY)
                .instrumentType(FUND)
                .orderQuantity(new BigDecimal("100"))
                .orderVenue(OrderVenue.SEB)
                .orderUuid(UUID.randomUUID())
                .orderStatus(EXECUTED)
                .build());
    executionRepository.save(
        TransactionExecution.builder()
            .orderId(absentOrder.getId())
            .brokerTransactionId("DLA0000099")
            .executionTimestamp(Instant.parse("2026-02-10T10:00:00Z"))
            .executedQuantity(new BigDecimal("100"))
            .source("SEB_OOTEL")
            .build());
    entityManager.flush();
    return absentOrder;
  }

  private List<Map<String, Object>> loadFixtureRawData() {
    try (InputStream is =
        getClass()
            .getClassLoader()
            .getResourceAsStream("nav-test-data/2026-02-13_pending_transactions.csv")) {
      assertThat(is).as("fixture must exist").isNotNull();
      return csvConverter.convert(is, ';', 5);
    } catch (Exception e) {
      throw new RuntimeException("Failed to load fixture", e);
    }
  }
}
