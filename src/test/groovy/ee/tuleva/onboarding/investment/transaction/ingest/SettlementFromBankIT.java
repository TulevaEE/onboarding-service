package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SETTLED;
import static ee.tuleva.onboarding.investment.transaction.SettlementSource.BANK_STATEMENT;
import static ee.tuleva.onboarding.investment.transaction.SettlementSource.PENDING_REPORT_ABSENCE;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static ee.tuleva.onboarding.investment.transaction.ingest.ReconciliationAuditRecorder.SETTLEMENT_FROM_BANK;
import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_INVESTMENT_CASH_CLEARING;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;

import ee.tuleva.onboarding.banking.StoredTradeReferences;
import ee.tuleva.onboarding.banking.seb.SebIntegrationTest;
import ee.tuleva.onboarding.investment.event.ReportImportCompleted;
import ee.tuleva.onboarding.investment.event.trigger.JobTriggerQueue;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportRepository;
import ee.tuleva.onboarding.investment.transaction.OrderVenue;
import ee.tuleva.onboarding.investment.transaction.SettlementSource;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEventRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionBatch;
import ee.tuleva.onboarding.investment.transaction.TransactionBatchRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionSettlement;
import ee.tuleva.onboarding.investment.transaction.TransactionSettlementRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionType;
import ee.tuleva.onboarding.ledger.FundBankLedger;
import ee.tuleva.onboarding.ledger.LedgerTransaction;
import ee.tuleva.onboarding.ledger.TradeSettlementDetails;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@SebIntegrationTest
class SettlementFromBankIT {

  private static final String EJAP = "LU1291102447";
  private static final LocalDate SINCE = LocalDate.of(2026, 2, 1);
  private static final BooleanSupplier LOCK_HELD = () -> true;
  private static final Clock AFTER_THE_BOOKINGS =
      Clock.fixed(Instant.parse("2026-02-25T08:00:00Z"), ZoneId.of("Europe/Tallinn"));

  @Autowired private SettlementFromBankService settlementFromBankService;
  @Autowired private FundBankLedger fundBankLedger;
  @Autowired private TransactionBatchRepository batchRepository;
  @Autowired private TransactionOrderRepository orderRepository;
  @Autowired private TransactionExecutionRepository executionRepository;
  @Autowired private TransactionSettlementRepository settlementRepository;
  @Autowired private TransactionAuditEventRepository auditEventRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private InvestmentReportRepository reportRepository;
  @Autowired private JobTriggerQueue jobTriggerQueue;
  @Autowired private StoredTradeReferences storedTradeReferences;
  private final OperationsNotificationService notificationService =
      mock(OperationsNotificationService.class);

  @BeforeEach
  void sebsLatestPendingReportListsNoneOfTheOrders() {
    pendingReport(LocalDate.of(2026, 2, 20), List.of(anotherOrdersRow()));
  }

  @Test
  void anOrderWhoseCashTheBankBooked_settlesOnTheBookingDateWithTheBankAsItsSource() {
    TransactionOrder order = executedOrder(OrderVenue.SEB, "11704");
    TransactionExecution piece = piece(order, "DLA0553690", "11704");
    bankBooks("DLA0553690", EJAP, "-209025.86", LocalDate.of(2026, 2, 12));

    settleFromTheBank();

    TransactionSettlement settlement = settlementOf(order);
    assertThat(settlement.getReportDate()).isEqualTo(LocalDate.of(2026, 2, 12));
    assertThat(settlement.getSource()).isEqualTo(BANK_STATEMENT);
    assertThat(orderRepository.findById(order.getId()).orElseThrow().getOrderStatus())
        .isEqualTo(SETTLED);
    assertThat(executionRepository.findById(piece.getId()).orElseThrow().getNetSettlementAmount())
        .isEqualByComparingTo("209025.86");
    assertThat(auditEventRepository.findByOrderIdAndEventType(order.getId(), SETTLEMENT_FROM_BANK))
        .singleElement()
        .satisfies(
            event -> assertThat(event.getPayload()).containsEntry("reportDate", "2026-02-12"));
  }

  @Test
  void anOrderPlacedWithFlowTraders_settlesFromTheBankToo() {
    TransactionOrder order = executedOrder(OrderVenue.FT, "11704");
    piece(order, "DLA0553690", "11704");
    bankBooks("DLA0553690", EJAP, "-209025.86", LocalDate.of(2026, 2, 12));

    settleFromTheBank();

    assertThat(settlementOf(order).getSource()).isEqualTo(BANK_STATEMENT);
  }

  @Test
  void anOrderInPieces_settlesOnTheDayTheBankBookedItsLastPiece() {
    TransactionOrder order = executedOrder(OrderVenue.SEB, "11704");
    piece(order, "DLA0553690", "6000");
    piece(order, "DLA0553691", "5704");
    bankBooks("DLA0553690", EJAP, "-107184.00", LocalDate.of(2026, 2, 12));
    bankBooks("DLA0553691", EJAP, "-101841.86", LocalDate.of(2026, 2, 13));

    settleFromTheBank();

    assertThat(settlementOf(order).getReportDate()).isEqualTo(LocalDate.of(2026, 2, 13));
  }

  @Test
  void anOrderWithAPieceTheBankHasNotBooked_staysOpen() {
    TransactionOrder order = executedOrder(OrderVenue.SEB, "11704");
    piece(order, "DLA0553690", "6000");
    piece(order, "DLA0553691", "5704");
    bankBooks("DLA0553690", EJAP, "-107184.00", LocalDate.of(2026, 2, 12));

    settleFromTheBank();

    assertThat(settlementRepository.findByOrderId(order.getId())).isEmpty();
    assertThat(orderRepository.findById(order.getId()).orElseThrow().getOrderStatus())
        .isEqualTo(EXECUTED);
  }

  @Test
  void anOrderWhosePiecesDoNotCoverItYet_staysOpenAlthoughTheBankBookedThem() {
    TransactionOrder order = executedOrder(OrderVenue.SEB, "11704");
    piece(order, "DLA0553690", "6000");
    bankBooks("DLA0553690", EJAP, "-107184.00", LocalDate.of(2026, 2, 12));

    settleFromTheBank();

    assertThat(settlementRepository.findByOrderId(order.getId())).isEmpty();
  }

  @Test
  void aBookingForAnotherInstrument_doesNotSettleTheOrder() {
    TransactionOrder order = executedOrder(OrderVenue.SEB, "11704");
    piece(order, "DLA0553690", "11704");
    bankBooks("DLA0553690", "IE00BJZ2DC62", "-209025.86", LocalDate.of(2026, 2, 12));

    settleFromTheBank();

    assertThat(settlementRepository.findByOrderId(order.getId())).isEmpty();
  }

  @Test
  void aBookingOfAnotherFund_doesNotSettleTheOrder() {
    TransactionOrder order = executedOrder(OrderVenue.SEB, "11704");
    piece(order, "DLA0553690", "11704");
    bankBooks(TUK75, "DLA0553690", EJAP, "-209025.86", LocalDate.of(2026, 2, 12));

    settleFromTheBank();

    assertThat(settlementRepository.findByOrderId(order.getId())).isEmpty();
  }

  @Test
  void anOrderSettledByAbsence_takesTheBanksDateAndSourceAndTheAuditKeepsTheOldOnes() {
    TransactionOrder order = executedOrder(OrderVenue.SEB, "11704");
    piece(order, "DLA0553690", "11704");
    settledEarlier(order, LocalDate.of(2026, 2, 16), PENDING_REPORT_ABSENCE);
    bankBooks("DLA0553690", EJAP, "-209025.86", LocalDate.of(2026, 2, 12));

    settleFromTheBank();

    TransactionSettlement settlement = settlementOf(order);
    assertThat(settlement.getReportDate()).isEqualTo(LocalDate.of(2026, 2, 12));
    assertThat(settlement.getSource()).isEqualTo(BANK_STATEMENT);
    assertThat(auditEventRepository.findByOrderIdAndEventType(order.getId(), SETTLEMENT_FROM_BANK))
        .singleElement()
        .satisfies(
            event ->
                assertThat(event.getPayload())
                    .containsEntry("reportDate", "2026-02-12")
                    .containsEntry("previousReportDate", "2026-02-16")
                    .containsEntry("previousSource", "PENDING_REPORT_ABSENCE"));
  }

  @Test
  void aSettlementTheBankDated_isNeverOverwritten() {
    TransactionOrder order = executedOrder(OrderVenue.SEB, "11704");
    piece(order, "DLA0553690", "11704");
    settledEarlier(order, LocalDate.of(2026, 2, 11), BANK_STATEMENT);
    bankBooks("DLA0553690", EJAP, "-209025.86", LocalDate.of(2026, 2, 12));

    settleFromTheBank();

    assertThat(settlementOf(order).getReportDate()).isEqualTo(LocalDate.of(2026, 2, 11));
    assertThat(auditEventRepository.findByOrderIdAndEventType(order.getId(), SETTLEMENT_FROM_BANK))
        .isEmpty();
  }

  @Test
  void runningAgain_changesNothingAndAuditsOnce() {
    TransactionOrder order = executedOrder(OrderVenue.SEB, "11704");
    piece(order, "DLA0553690", "11704");
    bankBooks("DLA0553690", EJAP, "-209025.86", LocalDate.of(2026, 2, 12));

    settleFromTheBank();
    settleFromTheBank();

    assertThat(settlementOf(order).getReportDate()).isEqualTo(LocalDate.of(2026, 2, 12));
    assertThat(auditEventRepository.findByOrderIdAndEventType(order.getId(), SETTLEMENT_FROM_BANK))
        .hasSize(1);
  }

  @Test
  void anOrderSebStillLists_staysOpenAlthoughTheBankBookedItsCash() {
    TransactionOrder order = executedOrder(OrderVenue.SEB, "11704");
    piece(order, "DLA0553690", "11704");
    bankBooks("DLA0553690", EJAP, "-209025.86", LocalDate.of(2026, 2, 12));
    pendingReport(
        LocalDate.of(2026, 2, 21), List.of(Map.of("Client ref", order.getOrderUuid().toString())));

    settleFromTheBank();

    assertThat(settlementRepository.findByOrderId(order.getId())).isEmpty();
  }

  @Test
  void aPendingRowWithNeitherReference_holdsBackEveryOrderInItsInstrument() {
    TransactionOrder order = executedOrder(OrderVenue.SEB, "11704");
    piece(order, "DLA0553690", "11704");
    bankBooks("DLA0553690", EJAP, "-209025.86", LocalDate.of(2026, 2, 12));
    pendingReport(LocalDate.of(2026, 2, 21), List.of(Map.of("ISIN", EJAP)));

    settleFromTheBank();

    assertThat(settlementRepository.findByOrderId(order.getId())).isEmpty();
  }

  @Test
  void aBookingAfterTheLatestPendingReport_waitsForTheNextOne() {
    TransactionOrder order = executedOrder(OrderVenue.SEB, "11704");
    piece(order, "DLA0553690", "11704");
    bankBooks("DLA0553690", EJAP, "-209025.86", LocalDate.of(2026, 2, 23));

    settleFromTheBank();

    assertThat(settlementRepository.findByOrderId(order.getId())).isEmpty();
  }

  @Test
  void anEmptyLatestPendingReport_settlesNothing() {
    TransactionOrder order = executedOrder(OrderVenue.SEB, "11704");
    piece(order, "DLA0553690", "11704");
    bankBooks("DLA0553690", EJAP, "-209025.86", LocalDate.of(2026, 2, 12));
    reportRepository.deleteAll();
    pendingReport(LocalDate.of(2026, 2, 21), List.of());

    assertThat(settlementFromBankService.settleOrdersBookedSince(SINCE, LOCK_HELD)).isEmpty();
    assertThat(settlementRepository.findByOrderId(order.getId())).isEmpty();
  }

  @Test
  void aLatestPendingReportThatLostHalfItsRows_settlesNothing() {
    TransactionOrder order = executedOrder(OrderVenue.SEB, "11704");
    piece(order, "DLA0553690", "11704");
    bankBooks("DLA0553690", EJAP, "-209025.86", LocalDate.of(2026, 2, 12));
    pendingReport(
        LocalDate.of(2026, 2, 21),
        List.of(anotherOrdersRow(), anotherOrdersRow(), anotherOrdersRow(), anotherOrdersRow()));
    pendingReport(LocalDate.of(2026, 2, 22), List.of(anotherOrdersRow()));

    assertThat(settlementFromBankService.settleOrdersBookedSince(SINCE, LOCK_HELD)).isEmpty();
    assertThat(settlementRepository.findByOrderId(order.getId())).isEmpty();
  }

  @Test
  void aLatestPendingReportWithoutAnAsOfDate_settlesNothing() {
    TransactionOrder order = executedOrder(OrderVenue.SEB, "11704");
    piece(order, "DLA0553690", "11704");
    bankBooks("DLA0553690", EJAP, "-209025.86", LocalDate.of(2026, 2, 12));
    reportRepository.save(
        InvestmentReport.builder()
            .provider(SEB)
            .reportType(PENDING_TRANSACTIONS)
            .reportDate(LocalDate.of(2026, 2, 22))
            .rawData(List.of(anotherOrdersRow()))
            .metadata(Map.of())
            .createdAt(Instant.now())
            .build());
    entityManager.flush();

    assertThat(settlementFromBankService.settleOrdersBookedSince(SINCE, LOCK_HELD)).isEmpty();
  }

  @Test
  void aSaleWhoseCashCameIn_settles() {
    TransactionOrder order = executedOrder(OrderVenue.SEB, "11704", SELL);
    piece(order, "DLA0553690", "11704");
    bankBooks("DLA0553690", EJAP, "209025.86", LocalDate.of(2026, 2, 12));

    settleFromTheBank();

    assertThat(settlementOf(order).getReportDate()).isEqualTo(LocalDate.of(2026, 2, 12));
  }

  @Test
  void aBuyWhoseCashCameIn_doesNotSettle() {
    TransactionOrder order = executedOrder(OrderVenue.SEB, "11704");
    piece(order, "DLA0553690", "11704");
    bankBooks("DLA0553690", EJAP, "209025.86", LocalDate.of(2026, 2, 12));

    settleFromTheBank();

    assertThat(settlementRepository.findByOrderId(order.getId())).isEmpty();
  }

  @Test
  void aReferenceTheBankBookedTwice_settlesNothing() {
    TransactionOrder order = executedOrder(OrderVenue.SEB, "11704");
    piece(order, "DLA0553690", "11704");
    bankBooks("DLA0553690", EJAP, "-209025.86", LocalDate.of(2026, 2, 12));
    bankBooks("DLA0553690", EJAP, "-209025.86", LocalDate.of(2026, 2, 13));

    settleFromTheBank();

    assertThat(settlementRepository.findByOrderId(order.getId())).isEmpty();
  }

  @Test
  void aRun_countsTheOrdersItSettledConfirmedRedatedAndLeftWithoutABankDate() {
    TransactionOrder settles = executedOrder(OrderVenue.SEB, "11704");
    piece(settles, "DLA0553690", "11704");
    bankBooks("DLA0553690", EJAP, "-209025.86", LocalDate.of(2026, 2, 12));
    TransactionOrder redated = executedOrder(OrderVenue.SEB, "11704");
    piece(redated, "DLA0553692", "11704");
    settledEarlier(redated, LocalDate.of(2026, 2, 16), PENDING_REPORT_ABSENCE);
    bankBooks("DLA0553692", EJAP, "-209025.86", LocalDate.of(2026, 2, 12));
    TransactionOrder confirmed = executedOrder(OrderVenue.SEB, "11704");
    piece(confirmed, "DLA0553694", "11704");
    settledEarlier(confirmed, LocalDate.of(2026, 2, 12), PENDING_REPORT_ABSENCE);
    bankBooks("DLA0553694", EJAP, "-209025.86", LocalDate.of(2026, 2, 12));
    TransactionOrder open = executedOrder(OrderVenue.SEB, "11704");
    piece(open, "DLA0553693", "11704");

    assertThat(settlementFromBankService.settleOrdersBookedSince(SINCE, LOCK_HELD))
        .contains(new BankSettlementRun(1, 1, 1, 1, 0));
  }

  @Test
  void aBookingWithoutACashLeg_failsItsOrderAndTheOtherOrdersStillSettle() {
    TransactionOrder withoutCash = executedOrder(OrderVenue.SEB, "11704");
    piece(withoutCash, "DLA0553690", "11704");
    UUID bookingWithoutCash =
        bankBooks("DLA0553690", EJAP, "-209025.86", LocalDate.of(2026, 2, 12));
    dropTheCashLegs(bookingWithoutCash);
    TransactionOrder settles = executedOrder(OrderVenue.SEB, "11704");
    piece(settles, "DLA0553691", "11704");
    bankBooks("DLA0553691", EJAP, "-209025.86", LocalDate.of(2026, 2, 12));

    assertThat(settlementFromBankService.settleOrdersBookedSince(SINCE, LOCK_HELD))
        .contains(new BankSettlementRun(1, 0, 0, 0, 1));
    entityManager.flush();
    entityManager.clear();
    assertThat(settlementRepository.findByOrderId(withoutCash.getId())).isEmpty();
    assertThat(settlementOf(settles).getSource()).isEqualTo(BANK_STATEMENT);
  }

  @Test
  void anImportWhoseBookingHasNoCashLeg_warnsTheInvestmentChannelUntilTheBookingIsRepaired() {
    TransactionOrder order = executedOrder(OrderVenue.SEB, "11704");
    piece(order, "DLA0553690", "11704");
    UUID broken = bankBooks("DLA0553690", EJAP, "-209025.86", LocalDate.of(2026, 2, 12));
    dropTheCashLegs(broken);
    SettlementFromBankListener listener = listenerLockedBy(RecordingLockProvider.free());
    ReportImportCompleted imported =
        new ReportImportCompleted(SEB, PENDING_TRANSACTIONS, LocalDate.of(2026, 2, 21), 1);

    listener.onReportImportCompleted(imported);

    then(notificationService).should().sendMessage(anyString(), eq(INVESTMENT), eq(WARNING));

    reset(notificationService);
    deleteTheBooking(broken);
    bankBooks("DLA0553690", EJAP, "-209025.86", LocalDate.of(2026, 2, 12));

    listener.onReportImportCompleted(imported);

    then(notificationService).shouldHaveNoInteractions();
    entityManager.flush();
    entityManager.clear();
    assertThat(settlementOf(order).getSource()).isEqualTo(BANK_STATEMENT);
  }

  @Test
  void anImportWhileTheLockIsHeld_leavesTheOrderOpenUntilItsTriggerFindsTheLockFree() {
    TransactionOrder order = executedOrder(OrderVenue.SEB, "11704");
    piece(order, "DLA0553690", "11704");
    bankBooks("DLA0553690", EJAP, "-209025.86", LocalDate.of(2026, 2, 12));

    listenerLockedBy(RecordingLockProvider.heldElsewhere())
        .onReportImportCompleted(
            new ReportImportCompleted(SEB, PENDING_TRANSACTIONS, LocalDate.of(2026, 2, 21), 1));
    entityManager.flush();

    assertThat(settlementRepository.findByOrderId(order.getId())).isEmpty();
    assertThat(pendingTriggers()).containsExactly("SettlementFromBankJob");

    listenerLockedBy(RecordingLockProvider.free()).onSettlementFromBankRequested();
    entityManager.flush();
    entityManager.clear();

    assertThat(settlementOf(order).getSource()).isEqualTo(BANK_STATEMENT);
  }

  @Test
  void aHistoryRunWhileTheLockIsHeld_failsAndLeavesTheOrderOpenUntilRunWithTheLockFree() {
    TransactionOrder order = executedOrder(OrderVenue.SEB, "11704");
    piece(order, "DLA0553690", "11704");
    bankBooks("DLA0553690", EJAP, "-209025.86", LocalDate.of(2026, 2, 12));
    SettlementFromBankHistoryJob lockedOut =
        historyJobLockedBy(RecordingLockProvider.heldElsewhere());

    assertThatThrownBy(lockedOut::run).isInstanceOf(IllegalStateException.class);
    assertThat(settlementRepository.findByOrderId(order.getId())).isEmpty();

    historyJobLockedBy(RecordingLockProvider.free()).run();
    entityManager.flush();
    entityManager.clear();

    assertThat(settlementOf(order).getSource()).isEqualTo(BANK_STATEMENT);
  }

  private SettlementFromBankListener listenerLockedBy(RecordingLockProvider lockProvider) {
    return new SettlementFromBankListener(
        AFTER_THE_BOOKINGS,
        settlementFromBankService,
        lockProvider.reconciliationLockAt(AFTER_THE_BOOKINGS),
        jobTriggerQueue,
        notificationService);
  }

  private SettlementFromBankHistoryJob historyJobLockedBy(RecordingLockProvider lockProvider) {
    return new SettlementFromBankHistoryJob(
        storedTradeReferences,
        settlementFromBankService,
        notificationService,
        lockProvider.reconciliationLockAt(AFTER_THE_BOOKINGS));
  }

  @SuppressWarnings("unchecked")
  private List<String> pendingTriggers() {
    return entityManager
        .createNativeQuery(
            "SELECT job_name FROM investment_job_trigger WHERE status = 'PENDING' ORDER BY id")
        .getResultList();
  }

  private void settleFromTheBank() {
    settlementFromBankService.settleOrdersBookedSince(SINCE, LOCK_HELD);
    entityManager.flush();
    entityManager.clear();
  }

  private TransactionSettlement settlementOf(TransactionOrder order) {
    return settlementRepository.findByOrderId(order.getId()).orElseThrow();
  }

  private TransactionOrder executedOrder(OrderVenue venue, String quantity) {
    return executedOrder(venue, quantity, BUY);
  }

  private TransactionOrder executedOrder(
      OrderVenue venue, String quantity, TransactionType transactionType) {
    TransactionBatch batch =
        batchRepository.save(TransactionBatch.builder().fund(TKF100).createdBy("test").build());
    TransactionOrder order =
        orderRepository.save(
            TransactionOrder.builder()
                .batch(batch)
                .fund(TKF100)
                .instrumentIsin(EJAP)
                .transactionType(transactionType)
                .instrumentType(ETF)
                .orderQuantity(new BigDecimal(quantity))
                .orderVenue(venue)
                .orderUuid(UUID.randomUUID())
                .orderStatus(EXECUTED)
                .orderTimestamp(Instant.parse("2026-02-10T10:00:00Z"))
                .build());
    entityManager.flush();
    return order;
  }

  private TransactionExecution piece(
      TransactionOrder order, String brokerReference, String quantity) {
    TransactionExecution execution =
        executionRepository.save(
            TransactionExecution.builder()
                .orderId(order.getId())
                .brokerTransactionId(brokerReference)
                .executionTimestamp(Instant.parse("2026-02-10T11:00:00Z"))
                .executedQuantity(new BigDecimal(quantity))
                .source("SEB_OOTEL")
                .reportedDate(LocalDate.of(2026, 2, 10))
                .build());
    entityManager.flush();
    return execution;
  }

  private static Map<String, Object> anotherOrdersRow() {
    return Map.of("Client ref", UUID.randomUUID().toString());
  }

  private void pendingReport(LocalDate asOfDate, List<Map<String, Object>> rows) {
    reportRepository.save(
        InvestmentReport.builder()
            .provider(SEB)
            .reportType(PENDING_TRANSACTIONS)
            .reportDate(asOfDate.plusDays(1))
            .rawData(rows)
            .metadata(Map.of("asOfDate", asOfDate.toString()))
            .createdAt(Instant.now())
            .build());
    entityManager.flush();
  }

  private void settledEarlier(
      TransactionOrder order, LocalDate reportDate, SettlementSource source) {
    settlementRepository.save(
        TransactionSettlement.builder()
            .orderId(order.getId())
            .settledAt(Instant.parse("2026-02-16T09:00:00Z"))
            .reportDate(reportDate)
            .source(source)
            .build());
    order.setOrderStatus(SETTLED);
    orderRepository.save(order);
    entityManager.flush();
  }

  private UUID bankBooks(String brokerReference, String isin, String cash, LocalDate bookingDate) {
    return bankBooks(TKF100, brokerReference, isin, cash, bookingDate);
  }

  private UUID bankBooks(
      TulevaFund fund, String brokerReference, String isin, String cash, LocalDate bookingDate) {
    BigDecimal amount = new BigDecimal(cash);
    LedgerTransaction booking =
        fundBankLedger.recordTradeSettlement(
            fund,
            amount,
            new BigDecimal("11704"),
            randomUUID(),
            FUND_INVESTMENT_CASH_CLEARING,
            new TradeSettlementDetails(
                brokerReference, isin, "EJAP", "test instrument", BigDecimal.ONE),
            bookingDate);
    entityManager.flush();
    return booking.getId();
  }

  private void deleteTheBooking(UUID bookingId) {
    entityManager
        .createNativeQuery("DELETE FROM ledger.entry WHERE transaction_id = :bookingId")
        .setParameter("bookingId", bookingId)
        .executeUpdate();
    entityManager
        .createNativeQuery("DELETE FROM ledger.transaction WHERE id = :bookingId")
        .setParameter("bookingId", bookingId)
        .executeUpdate();
    entityManager.clear();
  }

  private void dropTheCashLegs(UUID bookingId) {
    entityManager
        .createNativeQuery(
            "DELETE FROM ledger.entry WHERE transaction_id = :bookingId AND account_id IN"
                + " (SELECT id FROM ledger.account WHERE asset_type = 'EUR')")
        .setParameter("bookingId", bookingId)
        .executeUpdate();
    entityManager.clear();
  }
}
