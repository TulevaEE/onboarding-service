package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SENT;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SETTLED;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.event.trigger.JobTriggerLease;
import ee.tuleva.onboarding.investment.report.CsvToJsonConverter;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportRepository;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
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
import ee.tuleva.onboarding.investment.transaction.TransactionSettlementService;
import ee.tuleva.onboarding.investment.transaction.ingest.SebPendingReplayMessages.ReplaySummary;
import jakarta.persistence.EntityManager;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
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

  private static final BooleanSupplier LOCK_HELD = () -> true;

  private static final UUID FIXTURE_CLIENT_REF =
      UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Autowired private SebPendingTransactionReconciliationService reconciliationService;
  @Autowired private InvestmentReportRepository reportRepository;
  @Autowired private TransactionBatchRepository batchRepository;
  @Autowired private TransactionOrderRepository orderRepository;
  @Autowired private TransactionExecutionRepository executionRepository;
  @Autowired private TransactionAuditEventRepository auditEventRepository;
  @Autowired private TransactionSettlementRepository settlementRepository;
  @Autowired private TransactionSettlementService settlementService;
  @Autowired private CsvToJsonConverter csvConverter;
  @Autowired private EntityManager entityManager;
  @Autowired private InvestmentReportService reportService;

  private SebReplayListings listings = new SebReplayListings();

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
                .metadata(Map.of("source", "fixture", "asOfDate", "2026-02-12"))
                .createdAt(Instant.now())
                .build());
  }

  @Test
  void reconcile_matchedClientRef_writesExecutionAndTransitionsOrder() {
    reconciliationService.reconcile(report, LOCK_HELD);
    entityManager.flush();
    entityManager.clear();

    TransactionExecution execution = executionRepository.findAllByOrderId(order.getId()).getFirst();
    assertThat(execution.getBrokerTransactionId()).isEqualTo("DLA0000000");
    assertThat(execution.getExecutedQuantity()).isEqualByComparingTo("2669.9");
    assertThat(execution.getUnitPrice()).isEqualByComparingTo("34.37656841");
    assertThat(execution.getTotalConsideration()).isEqualByComparingTo("91782.00");
    assertThat(execution.getScheduledSettlementDate()).isEqualTo(LocalDate.of(2026, 2, 17));
    assertThat(execution.getExecutionTimestamp()).isEqualTo(Instant.parse("2026-02-10T16:06:58Z"));
    assertThat(execution.getSource()).isEqualTo("SEB_OOTEL");

    TransactionOrder reloaded = orderRepository.findById(order.getId()).orElseThrow();
    assertThat(reloaded.getOrderStatus()).isEqualTo(EXECUTED);
  }

  @Test
  void reconcile_rerun_isIdempotent() {
    reconciliationService.reconcile(report, LOCK_HELD);
    entityManager.flush();
    Long firstExecutionId = executionRepository.findAllByOrderId(order.getId()).getFirst().getId();

    reconciliationService.reconcile(report, LOCK_HELD);
    entityManager.flush();

    Long secondExecutionId = executionRepository.findAllByOrderId(order.getId()).getFirst().getId();
    assertThat(secondExecutionId).isEqualTo(firstExecutionId);
    assertThat(executionRepository.findAll())
        .filteredOn(e -> e.getOrderId().equals(order.getId()))
        .hasSize(1);
  }

  @Test
  void reconcile_unknownClientRefButKnownOurRef_rematchesViaBrokerRefTier() {
    reconciliationService.reconcile(report, LOCK_HELD);
    entityManager.flush();
    Long firstExecutionId = executionRepository.findAllByOrderId(order.getId()).getFirst().getId();

    report.getRawData().get(0).put("Client ref", "");
    reportRepository.save(report);
    entityManager.flush();

    reconciliationService.reconcile(report, LOCK_HELD);
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

    reconciliationService.reconcile(report, LOCK_HELD);
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

    reconciliationService.reconcile(report, LOCK_HELD);
    entityManager.flush();

    assertThat(executionRepository.findAllByOrderId(order.getId())).isEmpty();
    TransactionOrder reloaded = orderRepository.findById(order.getId()).orElseThrow();
    assertThat(reloaded.getOrderStatus()).isEqualTo(SENT);
  }

  @Test
  void reconcile_matchedRow_persistsExecutionMatchedAuditEventOnceAcrossReruns() {
    reconciliationService.reconcile(report, LOCK_HELD);
    reconciliationService.reconcile(report, LOCK_HELD);
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

    reconciliationService.reconcile(report, LOCK_HELD);
    reconciliationService.reconcile(report, LOCK_HELD);
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
    TransactionOrder absentOrder =
        seedExecutedOrderAbsentFromReport(LocalDate.of(2026, 2, 10), "SEB_OOTEL");

    reconciliationService.reconcile(report, LOCK_HELD);
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
  void replay_reportsOldestFirst_settlesAnOrderOnTheFirstReportItIsMissingFrom() {
    TransactionOrder stillOpen = givenOrderLeavesAfterFirstReportWhileAnotherStays();
    InvestmentReport missingFrom = reportWhoseFirstRowIs(LocalDate.of(2026, 2, 16), stillOpen);
    InvestmentReport latest = reportWhoseFirstRowIs(LocalDate.of(2026, 2, 20), stillOpen);
    List<ReconciliationAlert> heldBack = new ArrayList<>();

    reconciliationService.replay(report, heldBack::add, listings, LOCK_HELD);
    reconciliationService.replay(missingFrom, heldBack::add, listings, LOCK_HELD);
    reconciliationService.replay(latest, heldBack::add, listings, LOCK_HELD);
    entityManager.flush();
    entityManager.clear();

    assertThat(settlementRepository.findByOrderId(order.getId()).orElseThrow().getReportDate())
        .isEqualTo(LocalDate.of(2026, 2, 16));
    assertThat(orderRepository.findById(order.getId()).orElseThrow().getOrderStatus())
        .isEqualTo(SETTLED);
    assertThat(orderRepository.findById(stillOpen.getId()).orElseThrow().getOrderStatus())
        .isEqualTo(EXECUTED);
    assertThat(heldBack).isEmpty();
  }

  @Test
  void replay_runTwice_keepsTheSettlementDatesAndAddsNoExecutions() {
    TransactionOrder stillOpen = givenOrderLeavesAfterFirstReportWhileAnotherStays();
    List<InvestmentReport> history =
        List.of(
            report,
            reportWhoseFirstRowIs(LocalDate.of(2026, 2, 16), stillOpen),
            reportWhoseFirstRowIs(LocalDate.of(2026, 2, 20), stillOpen));
    history.forEach(
        stored -> reconciliationService.replay(stored, alert -> {}, listings, LOCK_HELD));
    entityManager.flush();
    long executionsAfterFirstRun = executionRepository.count();

    listings = new SebReplayListings();
    history.forEach(
        stored -> reconciliationService.replay(stored, alert -> {}, listings, LOCK_HELD));
    entityManager.flush();
    entityManager.clear();

    assertThat(executionRepository.count()).isEqualTo(executionsAfterFirstRun);
    assertThat(settlementRepository.findByOrderId(order.getId()).orElseThrow().getReportDate())
        .isEqualTo(LocalDate.of(2026, 2, 16));
    assertThat(settlementRepository.findByOrderId(stillOpen.getId())).isEmpty();
  }

  @Test
  void replay_orderFirstListedByALaterReport_isNotSettledByAnEarlierReportItIsMissingFrom() {
    TransactionOrder listedLater =
        seedExecutedOrderAbsentFromReport(LocalDate.of(2026, 2, 14), "SEB_OOTEL");

    reconciliationService.replay(report, alert -> {}, listings, LOCK_HELD);
    entityManager.flush();
    entityManager.clear();

    assertThat(settlementRepository.findByOrderId(listedLater.getId())).isEmpty();
    assertThat(orderRepository.findById(listedLater.getId()).orElseThrow().getOrderStatus())
        .isEqualTo(EXECUTED);
  }

  @Test
  void replay_executionStoredWithALaterReportedDate_movesItBackAndSettlesOnTime() {
    TransactionOrder stillOpen = givenOrderLeavesAfterFirstReportWhileAnotherStays();
    TransactionExecution storedLate =
        executionRepository.save(
            TransactionExecution.builder()
                .orderId(order.getId())
                .brokerTransactionId("DLA0000000")
                .executionTimestamp(Instant.parse("2026-02-10T16:06:58Z"))
                .executedQuantity(new BigDecimal("2669.9"))
                .source("SEB_OOTEL")
                .reportedDate(LocalDate.of(2026, 3, 1))
                .build());
    order.setOrderStatus(EXECUTED);
    orderRepository.save(order);
    entityManager.flush();

    reconciliationService.replay(report, alert -> {}, listings, LOCK_HELD);
    reconciliationService.replay(
        reportWhoseFirstRowIs(LocalDate.of(2026, 2, 16), stillOpen),
        alert -> {},
        listings,
        LOCK_HELD);
    entityManager.flush();
    entityManager.clear();

    assertThat(executionRepository.findById(storedLate.getId()).orElseThrow().getReportedDate())
        .isEqualTo(LocalDate.of(2026, 2, 12));
    assertThat(settlementRepository.findByOrderId(order.getId()).orElseThrow().getReportDate())
        .isEqualTo(LocalDate.of(2026, 2, 16));
  }

  @Test
  void
      replay_orderImportedFromTheSheetThatNoSebReportHasListed_isNotSettledByAReportItIsMissingFrom() {
    TransactionOrder imported =
        seedExecutedOrderAbsentFromReport(LocalDate.of(2026, 2, 10), "HISTORICAL_IMPORT");

    reconciliationService.replay(report, alert -> {}, listings, LOCK_HELD);
    entityManager.flush();
    entityManager.clear();

    assertThat(settlementRepository.findByOrderId(imported.getId())).isEmpty();
    assertThat(orderRepository.findById(imported.getId()).orElseThrow().getOrderStatus())
        .isEqualTo(EXECUTED);
  }

  @Test
  void reconcile_orderImportedFromTheSheet_settlesOnTheLatestReportItIsMissingFrom() {
    TransactionOrder imported =
        seedExecutedOrderAbsentFromReport(LocalDate.of(2026, 2, 10), "HISTORICAL_IMPORT");

    reconciliationService.reconcile(report, LOCK_HELD);
    entityManager.flush();
    entityManager.clear();

    assertThat(settlementRepository.findByOrderId(imported.getId()).orElseThrow().getReportDate())
        .isEqualTo(report.getReportDate());
  }

  @Test
  void replay_executionImportedFromTheSheet_countsOnceASebReportHasListedIt() {
    TransactionOrder stillOpen = givenOrderLeavesAfterFirstReportWhileAnotherStays();
    TransactionExecution imported =
        executionRepository.save(
            TransactionExecution.builder()
                .orderId(order.getId())
                .brokerTransactionId("DLA0000000")
                .executionTimestamp(Instant.parse("2026-02-10T16:06:58Z"))
                .executedQuantity(new BigDecimal("2669.9"))
                .source("HISTORICAL_IMPORT")
                .reportedDate(LocalDate.of(2026, 2, 10))
                .build());
    order.setOrderStatus(EXECUTED);
    orderRepository.save(order);
    entityManager.flush();

    reconciliationService.replay(report, alert -> {}, listings, LOCK_HELD);
    reconciliationService.replay(
        reportWhoseFirstRowIs(LocalDate.of(2026, 2, 16), stillOpen),
        alert -> {},
        listings,
        LOCK_HELD);
    entityManager.flush();
    entityManager.clear();

    TransactionExecution listed = executionRepository.findById(imported.getId()).orElseThrow();
    assertThat(listed.getSource()).isEqualTo("SEB_OOTEL");
    assertThat(listed.getReportedDate()).isEqualTo(LocalDate.of(2026, 2, 12));
    assertThat(settlementRepository.findByOrderId(order.getId()).orElseThrow().getReportDate())
        .isEqualTo(LocalDate.of(2026, 2, 16));
  }

  @Test
  void
      replay_runTwice_executionImportedFromTheSheet_keepsTheDateSebFirstListedItAndIsNotSettledEarly() {
    TransactionOrder stillOpen = givenOrderLeavesAfterFirstReportWhileAnotherStays();
    TransactionExecution imported =
        executionRepository.save(
            TransactionExecution.builder()
                .orderId(order.getId())
                .brokerTransactionId("DLA0000000")
                .executionTimestamp(Instant.parse("2026-02-10T16:06:58Z"))
                .executedQuantity(new BigDecimal("2669.9"))
                .source("HISTORICAL_IMPORT")
                .reportedDate(LocalDate.of(2026, 2, 10))
                .build());
    order.setOrderStatus(EXECUTED);
    orderRepository.save(order);
    InvestmentReport withoutTheImportedTrade =
        reportWhoseFirstRowIs(LocalDate.of(2026, 2, 12), stillOpen);
    entityManager.flush();

    for (int run = 0; run < 2; run++) {
      listings = new SebReplayListings();
      reconciliationService.replay(withoutTheImportedTrade, alert -> {}, listings, LOCK_HELD);
      reconciliationService.replay(report, alert -> {}, listings, LOCK_HELD);
      entityManager.flush();
    }
    entityManager.clear();

    assertThat(executionRepository.findById(imported.getId()).orElseThrow().getReportedDate())
        .isEqualTo(LocalDate.of(2026, 2, 12));
    assertThat(settlementRepository.findByOrderId(order.getId())).isEmpty();
  }

  @Test
  void replay_sheetImportALiveRunFlippedToSeb_countsOnlyFromTheReportThatFirstListsIt() {
    TransactionExecution imported = givenASheetImportALiveRunFlippedToSeb();

    replayerAt(SEB_LISTS_NOTHING_MORE_BY)
        .replayOldestFirst(
            SHEET_IMPORT_LACKED_BY_THE_FIRST_REPORT,
            SHEET_IMPORT_LACKED_BY_THE_FIRST_REPORT,
            LOCK_HELD);
    entityManager.flush();
    entityManager.clear();

    assertThat(settlementRepository.findByOrderId(order.getId()).orElseThrow().getReportDate())
        .isEqualTo(LocalDate.of(2026, 2, 16));
    assertThat(executionRepository.findById(imported.getId()).orElseThrow().getReportedDate())
        .isEqualTo(LocalDate.of(2026, 2, 12));
  }

  @Test
  void replay_resumedBetweenTheReportThatLacksASheetImportAndTheOneThatFirstListsIt_endsTheSame() {
    TransactionExecution imported = givenASheetImportALiveRunFlippedToSeb();

    replayerAt(SEB_LISTS_NOTHING_MORE_BY)
        .replayOldestFirst(
            SHEET_IMPORT_LACKED_BY_THE_FIRST_REPORT, LocalDate.of(2026, 2, 13), LOCK_HELD);
    entityManager.flush();
    entityManager.clear();

    assertThat(settlementRepository.findByOrderId(order.getId()).orElseThrow().getReportDate())
        .isEqualTo(LocalDate.of(2026, 2, 16));
    assertThat(executionRepository.findById(imported.getId()).orElseThrow().getReportedDate())
        .isEqualTo(LocalDate.of(2026, 2, 12));
  }

  @Test
  void replay_firstListingQuarantined_datesTheExecutionByThatListingWhenALaterReportCorrectsIt() {
    order.setOrderAmount(new BigDecimal("50000.00"));
    orderRepository.save(order);
    InvestmentReport corrected =
        reportCorrectingTheFixtureOrderToItsAmountOn(LocalDate.of(2026, 2, 16));
    entityManager.flush();

    for (int run = 0; run < 2; run++) {
      listings = new SebReplayListings();
      reconciliationService.replay(report, alert -> {}, listings, LOCK_HELD);
      reconciliationService.replay(corrected, alert -> {}, listings, LOCK_HELD);
      entityManager.flush();
    }
    entityManager.clear();

    assertThat(executionRepository.findAllByOrderId(order.getId()))
        .singleElement()
        .extracting(TransactionExecution::getReportedDate)
        .isEqualTo(LocalDate.of(2026, 2, 12));
  }

  @Test
  void replay_quarantinedFirstListing_datesTheLinkedExecutionAndTheOrderStillSettlesByAbsence() {
    TransactionOrder stillOpen = givenOrderLeavesAfterFirstReportWhileAnotherStays();
    order.setOrderAmount(new BigDecimal("50000.00"));
    order.setOrderStatus(EXECUTED);
    orderRepository.save(order);
    TransactionExecution imported =
        executionRepository.save(
            TransactionExecution.builder()
                .orderId(order.getId())
                .brokerTransactionId("DLA0000000")
                .executionTimestamp(Instant.parse("2026-02-10T16:06:58Z"))
                .executedQuantity(new BigDecimal("2669.9"))
                .totalConsideration(new BigDecimal("91782.00"))
                .source("HISTORICAL_IMPORT")
                .reportedDate(LocalDate.of(2026, 2, 10))
                .build());
    InvestmentReport missingFrom = reportWhoseFirstRowIs(LocalDate.of(2026, 2, 16), stillOpen);
    entityManager.flush();

    reconciliationService.replay(report, alert -> {}, listings, LOCK_HELD);
    reconciliationService.replay(missingFrom, alert -> {}, listings, LOCK_HELD);
    entityManager.flush();
    entityManager.clear();

    TransactionExecution dated = executionRepository.findById(imported.getId()).orElseThrow();
    assertThat(dated.getReportedDate()).isEqualTo(LocalDate.of(2026, 2, 12));
    assertThat(dated.getUnitPrice()).isNull();
    assertThat(dated.getSource()).isEqualTo("HISTORICAL_IMPORT");
    assertThat(settlementRepository.findByOrderId(order.getId()).orElseThrow().getReportDate())
        .isEqualTo(LocalDate.of(2026, 2, 16));
  }

  @Test
  void
      replay_orderSettledBeforeTheReplay_datesItsExecutionByTheFirstListingAndKeepsItsSettlement() {
    order.setOrderStatus(SETTLED);
    orderRepository.save(order);
    settlementRepository.save(
        TransactionSettlement.builder()
            .orderId(order.getId())
            .settledAt(Instant.parse("2026-02-11T07:00:00Z"))
            .reportDate(LocalDate.of(2026, 2, 11))
            .build());
    TransactionExecution storedLate =
        executionRepository.save(
            TransactionExecution.builder()
                .orderId(order.getId())
                .brokerTransactionId("DLA0000000")
                .executionTimestamp(Instant.parse("2026-02-10T16:06:58Z"))
                .executedQuantity(new BigDecimal("2669.9"))
                .source("SEB_OOTEL")
                .reportedDate(LocalDate.of(2026, 3, 1))
                .build());
    entityManager.flush();

    reconciliationService.replay(report, alert -> {}, listings, LOCK_HELD);
    entityManager.flush();
    entityManager.clear();

    TransactionExecution dated = executionRepository.findById(storedLate.getId()).orElseThrow();
    assertThat(dated.getReportedDate()).isEqualTo(LocalDate.of(2026, 2, 12));
    assertThat(dated.getExecutedQuantity()).isEqualByComparingTo("2669.9");
    assertThat(settlementRepository.findByOrderId(order.getId()).orElseThrow().getReportDate())
        .isEqualTo(LocalDate.of(2026, 2, 11));
    assertThat(orderRepository.findById(order.getId()).orElseThrow().getOrderStatus())
        .isEqualTo(SETTLED);
  }

  @Test
  void replay_firstListingLacksAnIsin_datesTheLinkedExecutionOnlyAndAuditsTheDateChange() {
    report.getRawData().get(0).put("ISIN", "");
    reportRepository.save(report);
    order.setOrderStatus(EXECUTED);
    orderRepository.save(order);
    TransactionExecution storedLate =
        executionRepository.save(
            TransactionExecution.builder()
                .orderId(order.getId())
                .brokerTransactionId("DLA0000000")
                .executionTimestamp(Instant.parse("2026-02-10T16:06:58Z"))
                .executedQuantity(new BigDecimal("2669.9"))
                .totalConsideration(new BigDecimal("50000.00"))
                .source("HISTORICAL_IMPORT")
                .reportedDate(LocalDate.of(2026, 3, 1))
                .build());
    entityManager.flush();

    reconciliationService.replay(report, alert -> {}, listings, LOCK_HELD);
    entityManager.flush();
    entityManager.clear();

    TransactionExecution dated = executionRepository.findById(storedLate.getId()).orElseThrow();
    assertThat(dated.getReportedDate()).isEqualTo(LocalDate.of(2026, 2, 12));
    assertThat(dated.getExecutedQuantity()).isEqualByComparingTo("2669.9");
    assertThat(dated.getTotalConsideration()).isEqualByComparingTo("50000.00");
    assertThat(dated.getUnitPrice()).isNull();
    assertThat(dated.getSource()).isEqualTo("HISTORICAL_IMPORT");
    assertThat(
            auditEventRepository.findByOrderIdAndEventType(
                order.getId(), "INCONSISTENT_MATCHED_ROW"))
        .singleElement()
        .satisfies(event -> assertThat(event.getPayload()).containsEntry("reason", "MISSING_ISIN"));
    List<TransactionAuditEvent> updates =
        auditEventRepository.findByOrderIdAndEventType(order.getId(), "EXECUTION_UPDATED");
    assertThat(updates).hasSize(1);
    Map<String, Object> before = executionSnapshot(updates.get(0), "before");
    Map<String, Object> onlyTheDateMoved = new HashMap<>(before);
    onlyTheDateMoved.put("reportedDate", "2026-02-12");
    assertThat(before)
        .containsEntry("reportedDate", "2026-03-01")
        .containsEntry("totalConsideration", "50000.00")
        .containsEntry("source", "HISTORICAL_IMPORT");
    assertThat(executionSnapshot(updates.get(0), "after")).isEqualTo(onlyTheDateMoved);
    assertThat(orderRepository.findById(order.getId()).orElseThrow().getOrderStatus())
        .isEqualTo(EXECUTED);
  }

  @Test
  void
      replay_quarantinedRowUnderAnotherOrdersClientRef_datesTheBrokerRefsExecutionUnderItsOwnOrderBeforeItSettles() {
    TransactionOrder clientRefOwner = givenOrderLeavesAfterFirstReportWhileAnotherStays();
    TransactionExecution brokerRefsExecution = givenTheFixtureOrderExecutedUnderOurRefListedLate();
    report.getRawData().get(0).put("Client ref", clientRefOwner.getOrderUuid().toString());
    report.getRawData().get(0).put("ISIN", "");
    reportRepository.save(report);
    InvestmentReport missingFrom = reportWhoseFirstRowIs(LocalDate.of(2026, 2, 16), clientRefOwner);
    entityManager.flush();

    reconciliationService.replay(report, alert -> {}, listings, LOCK_HELD);
    reconciliationService.replay(missingFrom, alert -> {}, listings, LOCK_HELD);
    entityManager.flush();
    entityManager.clear();

    assertThatOnlyItsDateMovedToTheFirstListingAndItsOrderSettledByAbsence(
        brokerRefsExecution, clientRefOwner);
  }

  @Test
  void
      replay_relinkRefusedForAnotherOrdersClientRef_datesTheBrokerRefsExecutionUnderItsOwnOrderBeforeItSettles() {
    TransactionOrder clientRefOwner = givenOrderLeavesAfterFirstReportWhileAnotherStays();
    TransactionExecution brokerRefsExecution = givenTheFixtureOrderExecutedUnderOurRefListedLate();
    report.getRawData().get(0).put("Client ref", clientRefOwner.getOrderUuid().toString());
    reportRepository.save(report);
    InvestmentReport missingFrom = reportWhoseFirstRowIs(LocalDate.of(2026, 2, 16), clientRefOwner);
    entityManager.flush();

    reconciliationService.replay(report, alert -> {}, listings, LOCK_HELD);
    reconciliationService.replay(missingFrom, alert -> {}, listings, LOCK_HELD);
    entityManager.flush();
    entityManager.clear();

    assertThatOnlyItsDateMovedToTheFirstListingAndItsOrderSettledByAbsence(
        brokerRefsExecution, clientRefOwner);
  }

  @Test
  void replay_resumedAfterALiveRunSettledTheOrderLater_movesItsSettlementBackToItsFirstAbsence() {
    InvestmentReport latest =
        givenAReplayStoppedBeforeTheFirstAbsenceAndALiveRunSettledOnTheLatest();

    ReplaySummary resumed =
        replayerAt(SEB_LISTS_NOTHING_MORE_BY)
            .replayOldestFirst(report.getReportDate(), FIRST_ABSENCE, LOCK_HELD);
    entityManager.flush();
    entityManager.clear();

    assertThat(settlementRepository.findByOrderId(order.getId()).orElseThrow().getReportDate())
        .isEqualTo(FIRST_ABSENCE);
    assertThat(orderRepository.findById(order.getId()).orElseThrow().getOrderStatus())
        .isEqualTo(SETTLED);
    assertThat(auditEventRepository.findByOrderIdAndEventType(order.getId(), "SETTLEMENT_REDATED"))
        .singleElement()
        .satisfies(
            redated -> {
              assertThat(redated.getPayload())
                  .containsEntry("reportDate", FIRST_ABSENCE.toString());
              assertThat(settlementSnapshot(redated, "before"))
                  .containsEntry("reportDate", latest.getReportDate().toString());
              assertThat(settlementSnapshot(redated, "after"))
                  .containsEntry("reportDate", FIRST_ABSENCE.toString());
            });
    assertThat(resumed.redatedSettlements()).isEqualTo(1);
    assertThat(resumed.newSettlements()).isZero();
  }

  @Test
  void replay_runAgainOverTheSameReports_leavesTheMovedSettlementAsItIs() {
    givenAReplayStoppedBeforeTheFirstAbsenceAndALiveRunSettledOnTheLatest();
    replayerAt(SEB_LISTS_NOTHING_MORE_BY)
        .replayOldestFirst(report.getReportDate(), FIRST_ABSENCE, LOCK_HELD);
    entityManager.flush();
    TransactionSettlement moved = settlementRepository.findByOrderId(order.getId()).orElseThrow();
    Instant movedSettledAt = moved.getSettledAt();
    entityManager.clear();

    replayerAt(SEB_LISTS_NOTHING_MORE_BY)
        .replayOldestFirst(report.getReportDate(), report.getReportDate(), LOCK_HELD);
    entityManager.flush();
    entityManager.clear();

    assertThat(settlementRepository.findByOrderId(order.getId()).orElseThrow())
        .extracting(TransactionSettlement::getReportDate, TransactionSettlement::getSettledAt)
        .containsExactly(FIRST_ABSENCE, movedSettledAt);
    assertThat(auditEventRepository.findByOrderIdAndEventType(order.getId(), "SETTLEMENT_REDATED"))
        .hasSize(1);
  }

  @Test
  void replay_settlementAlreadyEarlierThanTheAbsence_isLeftAsItIs() {
    TransactionOrder stillOpen = givenOrderLeavesAfterFirstReportWhileAnotherStays();
    reportWhoseFirstRowIs(FIRST_ABSENCE, stillOpen);
    entityManager.flush();
    reconciliationService.replay(report, alert -> {}, listings, LOCK_HELD);
    settlementService.recordSettlement(order, LocalDate.of(2026, 2, 14));
    entityManager.flush();

    replayerAt(SEB_LISTS_NOTHING_MORE_BY)
        .replayOldestFirst(report.getReportDate(), FIRST_ABSENCE, LOCK_HELD);
    entityManager.flush();
    entityManager.clear();

    assertThat(settlementRepository.findByOrderId(order.getId()).orElseThrow().getReportDate())
        .isEqualTo(LocalDate.of(2026, 2, 14));
    assertThat(auditEventRepository.findByOrderIdAndEventType(order.getId(), "SETTLEMENT_REDATED"))
        .isEmpty();
  }

  @Test
  void replay_orderNotFullyFilledByWhatTheReplaySawListed_keepsItsLaterSettlement() {
    TransactionOrder stillOpen = givenOrderLeavesAfterFirstReportWhileAnotherStays();
    reportWhoseFirstRowIs(FIRST_ABSENCE, stillOpen);
    entityManager.flush();
    reconciliationService.replay(report, alert -> {}, listings, LOCK_HELD);
    order.setOrderAmount(new BigDecimal("200000.00"));
    orderRepository.save(order);
    settlementService.recordSettlement(order, LocalDate.of(2026, 2, 17));
    entityManager.flush();

    replayerAt(SEB_LISTS_NOTHING_MORE_BY)
        .replayOldestFirst(report.getReportDate(), FIRST_ABSENCE, LOCK_HELD);
    entityManager.flush();
    entityManager.clear();

    assertThat(settlementRepository.findByOrderId(order.getId()).orElseThrow().getReportDate())
        .isEqualTo(LocalDate.of(2026, 2, 17));
    assertThat(auditEventRepository.findByOrderIdAndEventType(order.getId(), "SETTLEMENT_REDATED"))
        .isEmpty();
  }

  @Test
  void replay_orderStillListedByTheReport_keepsTheLaterSettlementALiveRunGaveIt() {
    TransactionOrder stillOpen = givenOrderLeavesAfterFirstReportWhileAnotherStays();
    reportListingTheFixtureOrderOn(FIRST_ABSENCE);
    InvestmentReport latest = reportWhoseFirstRowIs(LocalDate.of(2026, 2, 17), stillOpen);
    entityManager.flush();
    reconciliationService.replay(report, alert -> {}, listings, LOCK_HELD);
    reconciliationService.reconcile(latest, LOCK_HELD);
    entityManager.flush();

    replayerAt(SEB_LISTS_NOTHING_MORE_BY)
        .replayOldestFirst(report.getReportDate(), FIRST_ABSENCE, LOCK_HELD);
    entityManager.flush();
    entityManager.clear();

    assertThat(settlementRepository.findByOrderId(order.getId()).orElseThrow().getReportDate())
        .isEqualTo(latest.getReportDate());
    assertThat(auditEventRepository.findByOrderIdAndEventType(order.getId(), "SETTLEMENT_REDATED"))
        .isEmpty();
  }

  @Test
  void replay_orderTheResumedReplayHasNotSeenListed_keepsTheLaterSettlementALiveRunGaveIt() {
    InvestmentReport latest =
        givenAReplayStoppedBeforeTheFirstAbsenceAndALiveRunSettledOnTheLatest();

    replayerAt(SEB_LISTS_NOTHING_MORE_BY)
        .replayOldestFirst(FIRST_ABSENCE, FIRST_ABSENCE, LOCK_HELD);
    entityManager.flush();
    entityManager.clear();

    assertThat(settlementRepository.findByOrderId(order.getId()).orElseThrow().getReportDate())
        .isEqualTo(latest.getReportDate());
    assertThat(auditEventRepository.findByOrderIdAndEventType(order.getId(), "SETTLEMENT_REDATED"))
        .isEmpty();
  }

  @Test
  void reconcile_settlementLaterThanTheLatestReport_isNeverMovedByALiveRun() {
    TransactionOrder stillOpen = givenOrderLeavesAfterFirstReportWhileAnotherStays();
    InvestmentReport latest = reportWhoseFirstRowIs(LocalDate.of(2026, 2, 17), stillOpen);
    entityManager.flush();
    reconciliationService.replay(report, alert -> {}, listings, LOCK_HELD);
    settlementService.recordSettlement(order, LocalDate.of(2026, 2, 20));
    entityManager.flush();

    reconciliationService.reconcile(latest, LOCK_HELD);
    entityManager.flush();
    entityManager.clear();

    assertThat(settlementRepository.findByOrderId(order.getId()).orElseThrow().getReportDate())
        .isEqualTo(LocalDate.of(2026, 2, 20));
    assertThat(auditEventRepository.findByOrderIdAndEventType(order.getId(), "SETTLEMENT_REDATED"))
        .isEmpty();
  }

  @Test
  void
      replay_twiceOverAShortListingSebCorrectsLater_settlesOnTheAbsenceAfterTheCorrectionBothTimes() {
    TransactionOrder hundredUnits = givenAHundredUnitRedemptionBesideOneSebKeepsListing();
    List<InvestmentReport> history =
        List.of(
            redemptionReportOn(SHORT_LISTING, hundredUnitsListedAs("40"), keeperListed()),
            redemptionReportOn(SHORT_LISTING.plusDays(1), keeperListed()),
            redemptionReportOn(CORRECTED_LISTING, hundredUnitsListedAs("100"), keeperListed()),
            redemptionReportOn(ABSENCE_AFTER_THE_CORRECTION, keeperListed()));
    entityManager.flush();

    for (int run = 0; run < 2; run++) {
      SebReplayListings freshListings = new SebReplayListings();
      history.forEach(
          stored -> reconciliationService.replay(stored, alert -> {}, freshListings, LOCK_HELD));
      entityManager.flush();
      assertThat(
              settlementRepository
                  .findByOrderId(hundredUnits.getId())
                  .orElseThrow()
                  .getReportDate())
          .isEqualTo(ABSENCE_AFTER_THE_CORRECTION);
    }
    assertThat(
            auditEventRepository.findByOrderIdAndEventType(
                hundredUnits.getId(), "SETTLEMENT_REDATED"))
        .isEmpty();
  }

  @Test
  void
      replay_resumedAfterALiveRunSettledTheCorrectedOrder_leavesItOnTheAbsenceAfterTheCorrection() {
    TransactionOrder hundredUnits = givenAHundredUnitRedemptionBesideOneSebKeepsListing();
    InvestmentReport shortListing =
        redemptionReportOn(SHORT_LISTING, hundredUnitsListedAs("40"), keeperListed());
    redemptionReportOn(SHORT_LISTING.plusDays(1), keeperListed());
    InvestmentReport corrected =
        redemptionReportOn(CORRECTED_LISTING, hundredUnitsListedAs("100"), keeperListed());
    entityManager.flush();
    reconciliationService.replay(shortListing, alert -> {}, listings, LOCK_HELD);
    reconciliationService.reconcile(corrected, LOCK_HELD);
    InvestmentReport latest = redemptionReportOn(ABSENCE_AFTER_THE_CORRECTION, keeperListed());
    entityManager.flush();
    reconciliationService.reconcile(latest, LOCK_HELD);
    entityManager.flush();
    assertThat(
            settlementRepository.findByOrderId(hundredUnits.getId()).orElseThrow().getReportDate())
        .isEqualTo(ABSENCE_AFTER_THE_CORRECTION);

    replayerAt(AFTER_THE_REDEMPTION_REPORTS)
        .replayOldestFirst(SHORT_LISTING, SHORT_LISTING.plusDays(1), LOCK_HELD);
    entityManager.flush();
    entityManager.clear();

    assertThat(
            settlementRepository.findByOrderId(hundredUnits.getId()).orElseThrow().getReportDate())
        .isEqualTo(ABSENCE_AFTER_THE_CORRECTION);
    assertThat(
            auditEventRepository.findByOrderIdAndEventType(
                hundredUnits.getId(), "SETTLEMENT_REDATED"))
        .isEmpty();
  }

  @Test
  void replay_resumedAfterALiveRunStoredTheCorrection_settlesOnTheAbsenceAfterTheCorrection() {
    TransactionOrder hundredUnits = givenAHundredUnitRedemptionBesideOneSebKeepsListing();
    InvestmentReport shortListing =
        redemptionReportOn(SHORT_LISTING, hundredUnitsListedAs("40"), keeperListed());
    redemptionReportOn(SHORT_LISTING.plusDays(1), keeperListed());
    InvestmentReport corrected =
        redemptionReportOn(CORRECTED_LISTING, hundredUnitsListedAs("100"), keeperListed());
    entityManager.flush();
    reconciliationService.replay(shortListing, alert -> {}, listings, LOCK_HELD);
    reconciliationService.reconcile(corrected, LOCK_HELD);
    redemptionReportOn(ABSENCE_AFTER_THE_CORRECTION, keeperListed());
    entityManager.flush();
    assertThat(settlementRepository.findByOrderId(hundredUnits.getId())).isEmpty();

    replayerAt(AFTER_THE_REDEMPTION_REPORTS)
        .replayOldestFirst(SHORT_LISTING, SHORT_LISTING.plusDays(1), LOCK_HELD);
    entityManager.flush();
    entityManager.clear();

    assertThat(
            settlementRepository.findByOrderId(hundredUnits.getId()).orElseThrow().getReportDate())
        .isEqualTo(ABSENCE_AFTER_THE_CORRECTION);
  }

  @Test
  void replay_absenceAfterAListingWithoutItsQuantity_leavesTheStoredFillForALaterReport() {
    TransactionOrder hundredUnits = givenAHundredUnitRedemptionBesideOneSebKeepsListing();
    hundredUnits.setOrderStatus(EXECUTED);
    orderRepository.save(hundredUnits);
    executionRepository.save(
        TransactionExecution.builder()
            .orderId(hundredUnits.getId())
            .brokerTransactionId("DLA2000000")
            .executionTimestamp(Instant.parse("2026-08-24T09:48:09Z"))
            .executedQuantity(new BigDecimal("100"))
            .source("HISTORICAL_IMPORT")
            .reportedDate(SHORT_LISTING.minusDays(1))
            .build());
    Map<String, Object> withoutItsQuantity = hundredUnitsListedAs("100");
    withoutItsQuantity.remove("Quantity");
    List<InvestmentReport> history =
        List.of(
            redemptionReportOn(SHORT_LISTING, withoutItsQuantity, keeperListed()),
            redemptionReportOn(SHORT_LISTING.plusDays(1), keeperListed()));
    entityManager.flush();

    history.forEach(
        stored -> reconciliationService.replay(stored, alert -> {}, listings, LOCK_HELD));
    entityManager.flush();
    entityManager.clear();

    assertThat(settlementRepository.findByOrderId(hundredUnits.getId())).isEmpty();
  }

  @Test
  void replay_absenceAfterASplitWhoseClosingPieceSebRounded_settlesTheRedemption() {
    TransactionOrder split = givenASplitRedemptionWhoseClosingPieceSebRounds();
    List<InvestmentReport> history =
        List.of(
            redemptionReportOn(
                SHORT_LISTING,
                redemptionRow(SPLIT_REF, "DLA3000001", "10000000"),
                redemptionRow(SPLIT_REF, "DLA3000002", "8811874.1"),
                keeperListed(),
                secondKeeperPieceListed()),
            redemptionReportOn(
                SHORT_LISTING.plusDays(1), keeperListed(), secondKeeperPieceListed()));
    entityManager.flush();

    history.forEach(
        stored -> reconciliationService.replay(stored, alert -> {}, listings, LOCK_HELD));
    entityManager.flush();
    entityManager.clear();

    assertThat(executionRepository.findAllByOrderId(split.getId()))
        .extracting(TransactionExecution::getExecutedQuantity)
        .usingElementComparator(BigDecimal::compareTo)
        .containsExactlyInAnyOrder(new BigDecimal("10000000"), new BigDecimal("8811874.096"));
    assertThat(settlementRepository.findByOrderId(split.getId()).orElseThrow().getReportDate())
        .isEqualTo(SHORT_LISTING.plusDays(1));
  }

  @Test
  void replay_resumedAfterALiveRunSettledARoundedSplitLater_movesItBackToItsFirstAbsence() {
    TransactionOrder split = givenASplitRedemptionWhoseClosingPieceSebRounds();
    InvestmentReport listingTheSplit =
        redemptionReportOn(
            SHORT_LISTING,
            redemptionRow(SPLIT_REF, "DLA3000001", "10000000"),
            redemptionRow(SPLIT_REF, "DLA3000002", "8811874.1"),
            keeperListed(),
            secondKeeperPieceListed());
    redemptionReportOn(SHORT_LISTING.plusDays(1), keeperListed(), secondKeeperPieceListed());
    InvestmentReport latest =
        redemptionReportOn(CORRECTED_LISTING, keeperListed(), secondKeeperPieceListed());
    entityManager.flush();
    reconciliationService.replay(listingTheSplit, alert -> {}, listings, LOCK_HELD);
    reconciliationService.reconcile(latest, LOCK_HELD);
    entityManager.flush();
    assertThat(settlementRepository.findByOrderId(split.getId()).orElseThrow().getReportDate())
        .isEqualTo(CORRECTED_LISTING);

    replayerAt(AFTER_THE_REDEMPTION_REPORTS)
        .replayOldestFirst(SHORT_LISTING, SHORT_LISTING.plusDays(1), LOCK_HELD);
    entityManager.flush();
    entityManager.clear();

    assertThat(settlementRepository.findByOrderId(split.getId()).orElseThrow().getReportDate())
        .isEqualTo(SHORT_LISTING.plusDays(1));
    assertThat(auditEventRepository.findByOrderIdAndEventType(split.getId(), "SETTLEMENT_REDATED"))
        .hasSize(1);
  }

  @Test
  void replay_absenceAfterAListingSebRoundedToTenDigits_stillSettlesTheRedemption() {
    givenAHundredUnitRedemptionBesideOneSebKeepsListing();
    UUID roundedRef = UUID.fromString("55555555-5555-5555-5555-555555555555");
    TransactionOrder rounded = redemptionOrder(roundedRef, "18811874.096");
    List<InvestmentReport> history =
        List.of(
            redemptionReportOn(
                SHORT_LISTING,
                redemptionRow(roundedRef, "DLA1116935", "18811874.1"),
                keeperListed()),
            redemptionReportOn(SHORT_LISTING.plusDays(1), keeperListed()));
    entityManager.flush();

    history.forEach(
        stored -> reconciliationService.replay(stored, alert -> {}, listings, LOCK_HELD));
    entityManager.flush();
    entityManager.clear();

    assertThat(executionRepository.findAllByOrderId(rounded.getId()))
        .singleElement()
        .extracting(TransactionExecution::getExecutedQuantity)
        .satisfies(stored -> assertThat(stored).isEqualByComparingTo("18811874.096"));
    assertThat(settlementRepository.findByOrderId(rounded.getId()).orElseThrow().getReportDate())
        .isEqualTo(SHORT_LISTING.plusDays(1));
  }

  @Test
  void reconcile_sameReportsOldestFirst_settlesOnlyOnTheLatestReportsDate() {
    TransactionOrder stillOpen = givenOrderLeavesAfterFirstReportWhileAnotherStays();
    InvestmentReport missingFrom = reportWhoseFirstRowIs(LocalDate.of(2026, 2, 16), stillOpen);
    InvestmentReport latest = reportWhoseFirstRowIs(LocalDate.of(2026, 2, 20), stillOpen);

    reconciliationService.reconcile(report, LOCK_HELD);
    reconciliationService.reconcile(missingFrom, LOCK_HELD);
    reconciliationService.reconcile(latest, LOCK_HELD);
    entityManager.flush();
    entityManager.clear();

    assertThat(settlementRepository.findByOrderId(order.getId()).orElseThrow().getReportDate())
        .isEqualTo(LocalDate.of(2026, 2, 20));
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

    reconciliationService.reconcile(report, LOCK_HELD);
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
                .metadata(Map.of("source", "fixture", "asOfDate", "2026-06-23"))
                .createdAt(Instant.now())
                .build());

    reconciliationService.reconcile(splitReport, LOCK_HELD);
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
                .metadata(Map.of("source", "fixture", "asOfDate", "2026-06-23"))
                .createdAt(Instant.now())
                .build());

    reconciliationService.reconcile(report, LOCK_HELD);

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

  @Test
  void reconcile_fundRedemptionRoundedBySeb_storesOrderedQuantityAndRaisesNoMismatch(
      ApplicationEvents events) {
    UUID redemptionRef = UUID.randomUUID();
    TransactionBatch batch =
        batchRepository.save(TransactionBatch.builder().fund(TKF100).createdBy("test").build());
    TransactionOrder redemption =
        orderRepository.save(
            TransactionOrder.builder()
                .batch(batch)
                .fund(TKF100)
                .instrumentIsin("IE0009FT4LX4")
                .transactionType(SELL)
                .instrumentType(FUND)
                .orderQuantity(new BigDecimal("18811874.096"))
                .orderVenue(OrderVenue.SEB)
                .orderUuid(redemptionRef)
                .orderStatus(SENT)
                .build());

    InvestmentReport redemptionReport =
        reportRepository.save(
            InvestmentReport.builder()
                .provider(SEB)
                .reportType(PENDING_TRANSACTIONS)
                .reportDate(LocalDate.of(2026, 8, 26))
                .rawData(List.of(redemptionRow(redemptionRef, "DLA1116935", "18811874.1")))
                .metadata(Map.of("source", "fixture", "asOfDate", "2026-08-26"))
                .createdAt(Instant.now())
                .build());

    reconciliationService.reconcile(redemptionReport, LOCK_HELD);
    entityManager.flush();
    entityManager.clear();

    assertThat(events.stream(QuantityAmountMismatchEvent.class)).isEmpty();
    TransactionExecution execution =
        executionRepository.findAllByOrderId(redemption.getId()).getFirst();
    assertThat(execution.getExecutedQuantity()).isEqualByComparingTo("18811874.096");
  }

  @Test
  void reconcile_fundRedemptionRoundedBySeb_auditEventKeepsSebReportedQuantity() {
    UUID redemptionRef = UUID.randomUUID();
    TransactionBatch batch =
        batchRepository.save(TransactionBatch.builder().fund(TKF100).createdBy("test").build());
    TransactionOrder redemption =
        orderRepository.save(
            TransactionOrder.builder()
                .batch(batch)
                .fund(TKF100)
                .instrumentIsin("IE0009FT4LX4")
                .transactionType(SELL)
                .instrumentType(FUND)
                .orderQuantity(new BigDecimal("18811874.096"))
                .orderVenue(OrderVenue.SEB)
                .orderUuid(redemptionRef)
                .orderStatus(SENT)
                .build());

    InvestmentReport redemptionReport =
        reportRepository.save(
            InvestmentReport.builder()
                .provider(SEB)
                .reportType(PENDING_TRANSACTIONS)
                .reportDate(LocalDate.of(2026, 8, 26))
                .rawData(List.of(redemptionRow(redemptionRef, "DLA1116935", "18811874.1")))
                .metadata(Map.of("source", "fixture", "asOfDate", "2026-08-26"))
                .createdAt(Instant.now())
                .build());

    reconciliationService.reconcile(redemptionReport, LOCK_HELD);
    entityManager.flush();
    entityManager.clear();

    Map<String, Object> payload =
        auditEventRepository
            .findByOrderIdAndEventType(redemption.getId(), "EXECUTION_MATCHED")
            .getFirst()
            .getPayload();
    assertThat(new BigDecimal(payload.get("sebReportedQuantity").toString()))
        .isEqualByComparingTo("18811874.1");
    assertThat(new BigDecimal(payload.get("quantity").toString()))
        .isEqualByComparingTo("18811874.096");
    assertThat(payload.get("quantitySubstitutionReason")).isEqualTo("SEB_REPORTED_PRECISION");
  }

  @Test
  void reconcile_fundRedemptionGenuinelyOverfilled_stillRaisesMismatch(ApplicationEvents events) {
    UUID redemptionRef = UUID.randomUUID();
    TransactionBatch batch =
        batchRepository.save(TransactionBatch.builder().fund(TKF100).createdBy("test").build());
    orderRepository.save(
        TransactionOrder.builder()
            .batch(batch)
            .fund(TKF100)
            .instrumentIsin("IE0009FT4LX4")
            .transactionType(SELL)
            .instrumentType(FUND)
            .orderQuantity(new BigDecimal("18811874.096"))
            .orderVenue(OrderVenue.SEB)
            .orderUuid(redemptionRef)
            .orderStatus(SENT)
            .build());

    InvestmentReport redemptionReport =
        reportRepository.save(
            InvestmentReport.builder()
                .provider(SEB)
                .reportType(PENDING_TRANSACTIONS)
                .reportDate(LocalDate.of(2026, 8, 26))
                .rawData(List.of(redemptionRow(redemptionRef, "DLA1116935", "18811974.096")))
                .metadata(Map.of("source", "fixture", "asOfDate", "2026-08-26"))
                .createdAt(Instant.now())
                .build());

    reconciliationService.reconcile(redemptionReport, LOCK_HELD);

    assertThat(events.stream(QuantityAmountMismatchEvent.class)).hasSize(1);
  }

  private static Map<String, Object> redemptionRow(UUID clientRef, String ourRef, String quantity) {
    BigDecimal qty = new BigDecimal(quantity);
    BigDecimal unitPrice = new BigDecimal("17.314");
    Map<String, Object> raw = new HashMap<>();
    raw.put("ISIN", "IE0009FT4LX4");
    raw.put("Price", unitPrice);
    raw.put("Total", qty.multiply(unitPrice));
    raw.put("Account", "VP68958");
    raw.put("Our ref", ourRef);
    raw.put("Buy/Sell", "Sell");
    raw.put("Quantity", qty);
    raw.put("Broker fee", new BigDecimal("0.00"));
    raw.put("Client ref", clientRef.toString());
    raw.put("Trade date", "2026-08-24T09:48:09Z");
    raw.put("Settlement date", "2026-08-27");
    raw.put("Settlement amount", qty.multiply(unitPrice));
    raw.put("Client name", "Tuleva Täiendav Kogumisfond");
    raw.put("Instrument name", "CCF Developed World (ESG Screened) Index Fund Class X0");
    return raw;
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

  private TransactionOrder seedExecutedOrderAbsentFromReport(
      LocalDate reportedDate, String executionSource) {
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
                .orderTimestamp(Instant.parse("2026-02-10T10:00:00Z"))
                .build());
    executionRepository.save(
        TransactionExecution.builder()
            .orderId(absentOrder.getId())
            .brokerTransactionId("DLA0000099")
            .executionTimestamp(Instant.parse("2026-02-10T10:00:00Z"))
            .executedQuantity(new BigDecimal("100"))
            .source(executionSource)
            .reportedDate(reportedDate)
            .build());
    entityManager.flush();
    return absentOrder;
  }

  private TransactionOrder givenOrderLeavesAfterFirstReportWhileAnotherStays() {
    order.setOrderTimestamp(Instant.parse("2026-02-10T15:00:00Z"));
    orderRepository.save(order);
    TransactionOrder stillOpen =
        orderRepository.save(
            TransactionOrder.builder()
                .batch(order.getBatch())
                .fund(TKF100)
                .instrumentIsin("IE00BFG1TM61")
                .transactionType(BUY)
                .instrumentType(FUND)
                .orderQuantity(new BigDecimal("2670"))
                .orderVenue(OrderVenue.SEB)
                .orderUuid(UUID.fromString("22222222-2222-2222-2222-222222222222"))
                .orderStatus(SENT)
                .orderTimestamp(Instant.parse("2026-02-12T15:00:00Z"))
                .build());
    entityManager.flush();
    return stillOpen;
  }

  private static final LocalDate SHEET_IMPORT_LACKED_BY_THE_FIRST_REPORT =
      LocalDate.of(2026, 2, 12);
  private static final Instant SEB_LISTS_NOTHING_MORE_BY = Instant.parse("2026-02-20T20:00:00Z");

  private TransactionExecution givenASheetImportALiveRunFlippedToSeb() {
    TransactionOrder stillOpen = givenOrderLeavesAfterFirstReportWhileAnotherStays();
    TransactionExecution imported =
        executionRepository.save(
            TransactionExecution.builder()
                .orderId(order.getId())
                .brokerTransactionId("DLA0000000")
                .executionTimestamp(Instant.parse("2026-02-10T16:06:58Z"))
                .executedQuantity(new BigDecimal("2669.9"))
                .source("HISTORICAL_IMPORT")
                .reportedDate(LocalDate.of(2026, 2, 10))
                .build());
    order.setOrderStatus(EXECUTED);
    orderRepository.save(order);
    reportWhoseFirstRowIs(SHEET_IMPORT_LACKED_BY_THE_FIRST_REPORT, stillOpen);
    reportWhoseFirstRowIs(LocalDate.of(2026, 2, 16), stillOpen);
    entityManager.flush();
    reconciliationService.reconcile(report, LOCK_HELD);
    entityManager.flush();
    TransactionExecution flipped = executionRepository.findById(imported.getId()).orElseThrow();
    assertThat(flipped.getSource()).isEqualTo("SEB_OOTEL");
    assertThat(flipped.getReportedDate()).isEqualTo(LocalDate.of(2026, 2, 10));
    assertThat(settlementRepository.findByOrderId(order.getId())).isEmpty();
    return imported;
  }

  private InvestmentReport reportCorrectingTheFixtureOrderToItsAmountOn(LocalDate reportDate) {
    List<Map<String, Object>> rawData = loadFixtureRawData();
    rawData.get(0).put("Client ref", FIXTURE_CLIENT_REF.toString());
    rawData.get(0).put("Total", new BigDecimal("50000.00"));
    rawData.get(0).put("Settlement amount", new BigDecimal("50000.00"));
    return reportRepository.save(
        InvestmentReport.builder()
            .provider(SEB)
            .reportType(PENDING_TRANSACTIONS)
            .reportDate(reportDate)
            .rawData(rawData)
            .metadata(Map.of("source", "fixture", "asOfDate", reportDate.minusDays(1).toString()))
            .createdAt(Instant.now())
            .build());
  }

  private SebPendingReportReplayer replayerAt(Instant now) {
    return new SebPendingReportReplayer(
        Clock.fixed(now, ZoneId.of("Europe/Tallinn")),
        reportService,
        reconciliationService,
        executionRepository,
        settlementRepository,
        auditEventRepository,
        new SebReplayWindow("07:45", "18:15"),
        new JobTriggerLease());
  }

  private InvestmentReport reportWhoseFirstRowIs(LocalDate reportDate, TransactionOrder owner) {
    List<Map<String, Object>> rawData = loadFixtureRawData();
    rawData.get(0).put("Client ref", owner.getOrderUuid().toString());
    rawData.get(0).put("Our ref", "DLA0000001");
    return reportRepository.save(
        InvestmentReport.builder()
            .provider(SEB)
            .reportType(PENDING_TRANSACTIONS)
            .reportDate(reportDate)
            .rawData(rawData)
            .metadata(Map.of("source", "fixture", "asOfDate", reportDate.minusDays(1).toString()))
            .createdAt(Instant.now())
            .build());
  }

  private TransactionExecution givenTheFixtureOrderExecutedUnderOurRefListedLate() {
    order.setOrderStatus(EXECUTED);
    orderRepository.save(order);
    return executionRepository.save(
        TransactionExecution.builder()
            .orderId(order.getId())
            .brokerTransactionId("DLA0000000")
            .executionTimestamp(Instant.parse("2026-02-10T16:06:58Z"))
            .executedQuantity(new BigDecimal("2669.9"))
            .totalConsideration(new BigDecimal("91782.00"))
            .source("HISTORICAL_IMPORT")
            .reportedDate(LocalDate.of(2026, 3, 1))
            .build());
  }

  private void assertThatOnlyItsDateMovedToTheFirstListingAndItsOrderSettledByAbsence(
      TransactionExecution brokerRefsExecution, TransactionOrder clientRefOwner) {
    TransactionExecution dated =
        executionRepository.findById(brokerRefsExecution.getId()).orElseThrow();
    assertThat(dated.getOrderId()).isEqualTo(order.getId());
    assertThat(dated.getReportedDate()).isEqualTo(LocalDate.of(2026, 2, 12));
    assertThat(dated.getExecutedQuantity()).isEqualByComparingTo("2669.9");
    assertThat(dated.getTotalConsideration()).isEqualByComparingTo("91782.00");
    assertThat(dated.getUnitPrice()).isNull();
    assertThat(dated.getSource()).isEqualTo("HISTORICAL_IMPORT");
    List<TransactionAuditEvent> updates =
        auditEventRepository.findByOrderIdAndEventType(order.getId(), "EXECUTION_UPDATED");
    assertThat(updates).hasSize(1);
    Map<String, Object> onlyTheDateMoved =
        new HashMap<>(executionSnapshot(updates.get(0), "before"));
    onlyTheDateMoved.put("reportedDate", "2026-02-12");
    assertThat(executionSnapshot(updates.get(0), "after")).isEqualTo(onlyTheDateMoved);
    assertThat(settlementRepository.findByOrderId(order.getId()).orElseThrow().getReportDate())
        .isEqualTo(LocalDate.of(2026, 2, 16));
    assertThat(
            auditEventRepository.findByOrderIdAndEventType(
                clientRefOwner.getId(), "EXECUTION_UPDATED"))
        .isEmpty();
    assertThat(executionRepository.findAllByOrderId(clientRefOwner.getId()))
        .extracting(TransactionExecution::getBrokerTransactionId)
        .doesNotContain("DLA0000000");
  }

  private static final UUID HUNDRED_UNITS_REF =
      UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID KEEPER_REF = UUID.fromString("44444444-4444-4444-4444-444444444444");
  private static final LocalDate SHORT_LISTING = LocalDate.of(2026, 8, 25);
  private static final LocalDate CORRECTED_LISTING = LocalDate.of(2026, 8, 27);
  private static final LocalDate ABSENCE_AFTER_THE_CORRECTION = LocalDate.of(2026, 8, 28);
  private static final Instant AFTER_THE_REDEMPTION_REPORTS = Instant.parse("2026-08-30T20:00:00Z");

  private TransactionOrder givenAHundredUnitRedemptionBesideOneSebKeepsListing() {
    redemptionOrder(KEEPER_REF, "1000");
    return redemptionOrder(HUNDRED_UNITS_REF, "100");
  }

  private TransactionOrder redemptionOrder(UUID clientRef, String quantity) {
    return orderRepository.save(
        TransactionOrder.builder()
            .batch(order.getBatch())
            .fund(TKF100)
            .instrumentIsin("IE0009FT4LX4")
            .transactionType(SELL)
            .instrumentType(FUND)
            .orderQuantity(new BigDecimal(quantity))
            .orderVenue(OrderVenue.SEB)
            .orderUuid(clientRef)
            .orderStatus(SENT)
            .orderTimestamp(Instant.parse("2026-08-24T08:00:00Z"))
            .build());
  }

  private static Map<String, Object> hundredUnitsListedAs(String quantity) {
    return redemptionRow(HUNDRED_UNITS_REF, "DLA2000000", quantity);
  }

  private static final UUID SPLIT_REF = UUID.fromString("66666666-6666-6666-6666-666666666666");

  private TransactionOrder givenASplitRedemptionWhoseClosingPieceSebRounds() {
    redemptionOrder(KEEPER_REF, "1000");
    return redemptionOrder(SPLIT_REF, "18811874.096");
  }

  private static Map<String, Object> secondKeeperPieceListed() {
    return redemptionRow(KEEPER_REF, "DLA2000003", "10");
  }

  private static Map<String, Object> keeperListed() {
    return redemptionRow(KEEPER_REF, "DLA2000001", "10");
  }

  @SafeVarargs
  private InvestmentReport redemptionReportOn(LocalDate reportDate, Map<String, Object>... rows) {
    return reportRepository.save(
        InvestmentReport.builder()
            .provider(SEB)
            .reportType(PENDING_TRANSACTIONS)
            .reportDate(reportDate)
            .rawData(List.of(rows))
            .metadata(Map.of("source", "fixture", "asOfDate", reportDate.minusDays(1).toString()))
            .createdAt(Instant.now())
            .build());
  }

  private static final LocalDate FIRST_ABSENCE = LocalDate.of(2026, 2, 16);

  private InvestmentReport givenAReplayStoppedBeforeTheFirstAbsenceAndALiveRunSettledOnTheLatest() {
    TransactionOrder stillOpen = givenOrderLeavesAfterFirstReportWhileAnotherStays();
    reportWhoseFirstRowIs(FIRST_ABSENCE, stillOpen);
    InvestmentReport latest = reportWhoseFirstRowIs(LocalDate.of(2026, 2, 17), stillOpen);
    entityManager.flush();
    reconciliationService.replay(report, alert -> {}, listings, LOCK_HELD);
    reconciliationService.reconcile(latest, LOCK_HELD);
    entityManager.flush();
    assertThat(settlementRepository.findByOrderId(order.getId()).orElseThrow().getReportDate())
        .isEqualTo(latest.getReportDate());
    return latest;
  }

  private InvestmentReport reportListingTheFixtureOrderOn(LocalDate reportDate) {
    List<Map<String, Object>> rawData = loadFixtureRawData();
    rawData.get(0).put("Client ref", FIXTURE_CLIENT_REF.toString());
    return reportRepository.save(
        InvestmentReport.builder()
            .provider(SEB)
            .reportType(PENDING_TRANSACTIONS)
            .reportDate(reportDate)
            .rawData(rawData)
            .metadata(Map.of("source", "fixture", "asOfDate", reportDate.minusDays(1).toString()))
            .createdAt(Instant.now())
            .build());
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> settlementSnapshot(
      TransactionAuditEvent redated, String side) {
    return (Map<String, Object>) redated.getPayload().get(side);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> executionSnapshot(TransactionAuditEvent update, String side) {
    return (Map<String, Object>) update.getPayload().get(side);
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
