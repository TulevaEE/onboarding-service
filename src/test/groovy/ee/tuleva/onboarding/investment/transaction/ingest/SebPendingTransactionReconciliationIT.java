package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
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

    TransactionExecution execution = executionRepository.findByOrderId(order.getId()).orElseThrow();
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
    Long firstExecutionId = executionRepository.findByOrderId(order.getId()).orElseThrow().getId();

    reconciliationService.reconcile(report);
    entityManager.flush();

    Long secondExecutionId = executionRepository.findByOrderId(order.getId()).orElseThrow().getId();
    assertThat(secondExecutionId).isEqualTo(firstExecutionId);
    assertThat(executionRepository.findAll())
        .filteredOn(e -> e.getOrderId().equals(order.getId()))
        .hasSize(1);
  }

  @Test
  void reconcile_unknownClientRefButKnownOurRef_rematchesViaBrokerRefTier() {
    reconciliationService.reconcile(report);
    entityManager.flush();
    Long firstExecutionId = executionRepository.findByOrderId(order.getId()).orElseThrow().getId();

    report.getRawData().get(0).put("Client ref", "");
    reportRepository.save(report);
    entityManager.flush();

    reconciliationService.reconcile(report);
    entityManager.flush();
    entityManager.clear();

    TransactionExecution execution = executionRepository.findByOrderId(order.getId()).orElseThrow();
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
    assertThat(executionRepository.findByOrderId(order.getId())).isEmpty();
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

    assertThat(executionRepository.findByOrderId(order.getId())).isEmpty();
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
