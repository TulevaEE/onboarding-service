package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SETTLED;
import static ee.tuleva.onboarding.investment.transaction.SettlementSource.BANK_STATEMENT;
import static ee.tuleva.onboarding.investment.transaction.SettlementSource.PENDING_REPORT_ABSENCE;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.ingest.ReconciliationAuditRecorder.SETTLEMENT_FROM_BANK;
import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_INVESTMENT_CASH_CLEARING;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.banking.seb.SebIntegrationTest;
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
import ee.tuleva.onboarding.ledger.FundBankLedger;
import ee.tuleva.onboarding.ledger.TradeSettlementDetails;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@SebIntegrationTest
class SettlementFromBankIT {

  private static final String EJAP = "LU1291102447";
  private static final LocalDate SINCE = LocalDate.of(2026, 2, 1);

  @Autowired private SettlementFromBankService settlementFromBankService;
  @Autowired private FundBankLedger fundBankLedger;
  @Autowired private TransactionBatchRepository batchRepository;
  @Autowired private TransactionOrderRepository orderRepository;
  @Autowired private TransactionExecutionRepository executionRepository;
  @Autowired private TransactionSettlementRepository settlementRepository;
  @Autowired private TransactionAuditEventRepository auditEventRepository;
  @Autowired private EntityManager entityManager;

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

    BankSettlementRun run = settlementFromBankService.settleOrdersBookedSince(SINCE);

    assertThat(run).isEqualTo(new BankSettlementRun(1, 1, 1, 1));
  }

  private void settleFromTheBank() {
    settlementFromBankService.settleOrdersBookedSince(SINCE);
    entityManager.flush();
    entityManager.clear();
  }

  private TransactionSettlement settlementOf(TransactionOrder order) {
    return settlementRepository.findByOrderId(order.getId()).orElseThrow();
  }

  private TransactionOrder executedOrder(OrderVenue venue, String quantity) {
    TransactionBatch batch =
        batchRepository.save(TransactionBatch.builder().fund(TKF100).createdBy("test").build());
    TransactionOrder order =
        orderRepository.save(
            TransactionOrder.builder()
                .batch(batch)
                .fund(TKF100)
                .instrumentIsin(EJAP)
                .transactionType(BUY)
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

  private void bankBooks(String brokerReference, String isin, String cash, LocalDate bookingDate) {
    BigDecimal amount = new BigDecimal(cash);
    fundBankLedger.recordTradeSettlement(
        TKF100,
        amount,
        new BigDecimal("11704"),
        randomUUID(),
        FUND_INVESTMENT_CASH_CLEARING,
        new TradeSettlementDetails(
            brokerReference, isin, "EJAP", "test instrument", BigDecimal.ONE),
        bookingDate);
    entityManager.flush();
  }
}
