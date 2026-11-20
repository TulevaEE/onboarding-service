package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SETTLED;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_INVESTMENT_CASH_CLEARING;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.context.annotation.FilterType.REGEX;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.investment.epis.PevaRavaCycle;
import ee.tuleva.onboarding.ledger.FundBankLedger;
import ee.tuleva.onboarding.ledger.TradeSettlementDetails;
import ee.tuleva.onboarding.time.ClockConfig;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@Import({
  GatewayNettingIT.LedgerWriting.class,
  GatewayNetting.class,
  PendingOrderImpactService.class,
  ClockConfig.class
})
class GatewayNettingIT {

  @ComponentScan(
      basePackages = "ee.tuleva.onboarding.ledger",
      useDefaultFilters = false,
      includeFilters =
          @ComponentScan.Filter(
              type = REGEX,
              pattern =
                  "ee\\.tuleva\\.onboarding\\.ledger\\.(FundBankLedger|LedgerAccountService"
                      + "|LedgerTransactionService|UserUnitBalanceGuard"
                      + "|RegistrarCashFlowRepository|TradeSettlementBookings)"))
  static class LedgerWriting {}

  private static final String ISIN = "IE00BFG1TM61";
  private static final LocalDate REPORT_DATE = LocalDate.of(2026, 11, 19);
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);
  private static final PevaRavaCycle CYCLE_ENDING_1_SEPTEMBER =
      new PevaRavaCycle(LocalDate.of(2026, 7, 31), LocalDate.of(2026, 9, 1));
  private static final NettingWindow WINDOW =
      new NettingWindow(TUK75, REPORT_DATE, TODAY, Set.of());

  @Autowired GatewayNetting netting;
  @Autowired PendingOrderImpactService pendingOrderImpactService;
  @MockitoBean PositionPriceResolver positionPriceResolver;
  @Autowired FundBankLedger fundBankLedger;
  @Autowired TransactionBatchRepository batchRepository;
  @Autowired TransactionOrderRepository orderRepository;
  @Autowired TransactionExecutionRepository executionRepository;
  @Autowired EntityManager entityManager;

  @Test
  void theReportsUnsettledTradesDropWhatTheBankBookedForThemSinceTheReportDate() {
    filled(BUY, "B-1", REPORT_DATE);
    bankBooks(TUK75, "B-1", "-120000.00", TODAY);
    filled(SELL, "S-1", REPORT_DATE);
    bankBooks(TUK75, "S-1", "200000.00", TODAY);
    bankBooks(TUK75, "NO-ORDER", "-30000.00", TODAY);

    var netted =
        netting.unsettledTrades(
            WINDOW,
            netting.tradeSettlements(WINDOW),
            new UnsettledTrades(new BigDecimal("300000.00"), new BigDecimal("200000.00")));

    assertThat(netted.after().payables()).isEqualByComparingTo("150000.00");
    assertThat(netted.after().receivables()).isEqualByComparingTo("0.00");
    assertThat(netted.before())
        .isEqualTo(new UnsettledTrades(new BigDecimal("300000.00"), new BigDecimal("200000.00")));
  }

  @Test
  void aTradeExecutedAfterTheReportDateIsNotInTheReportsRowsSoItsSettlementLeavesThemAlone() {
    filled(BUY, "B-TODAY", TODAY);
    bankBooks(TUK75, "B-TODAY", "-50000.00", TODAY);
    var settledOrder = filled(BUY, "B-SETTLED", TODAY);
    settledOrder.setOrderStatus(SETTLED);
    orderRepository.save(settledOrder);
    bankBooks(TUK75, "B-SETTLED", "-40000.00", TODAY);

    var netted =
        netting.unsettledTrades(
            WINDOW,
            netting.tradeSettlements(WINDOW),
            new UnsettledTrades(new BigDecimal("300000.00"), new BigDecimal("0.00")));

    assertThat(netted.after().payables()).isEqualByComparingTo("300000.00");
  }

  @Test
  void aFundBuyReportedOnTheReportDateIsReservedByItsOrderSoItsSettlementLeavesTheRowsAlone() {
    var order = filled(BUY, "FUND-BUY", REPORT_DATE);
    order.setInstrumentType(InstrumentType.FUND);
    orderRepository.save(order);
    bankBooks(TUK75, "FUND-BUY", "-60000.00", TODAY);

    var netted =
        netting.unsettledTrades(
            WINDOW,
            netting.tradeSettlements(WINDOW),
            new UnsettledTrades(new BigDecimal("300000.00"), new BigDecimal("0.00")));

    assertThat(netted.after().payables()).isEqualByComparingTo("300000.00");
  }

  @Test
  void anOrderExecutedTodayWhoseSettlementTheBankBookedBeforeTheRegisterMarkedItIsCountedOnce() {
    filled(BUY, "B-TODAY", TODAY);
    bankBooks(TUK75, "B-TODAY", "-50000.00", TODAY);
    var settlements = netting.tradeSettlements(WINDOW);

    var pendingOrders =
        pendingOrderImpactService.calculate(
            TUK75,
            TODAY,
            REPORT_DATE,
            TradePrepPricingFixture.noResolvedPrices(),
            settlements.carriedByPendingOrders());

    assertThat(pendingOrders.pendingBuys()).isEqualByComparingTo("0");
    assertThat(pendingOrders.buysInTheBalance()).isEqualByComparingTo("50000");
    assertThat(
            netting
                .unsettledTrades(
                    WINDOW,
                    settlements,
                    new UnsettledTrades(new BigDecimal("300000.00"), new BigDecimal("0.00")))
                .after()
                .payables())
        .isEqualByComparingTo("300000.00");
  }

  @Test
  void bookingsOnTheReportDateOrForAnotherFundOrStillPendingAreNotNetted() {
    bankBooks(TUK75, "ON-P", "-10000.00", REPORT_DATE);
    bankBooks(TUK00, "OTHER", "-20000.00", TODAY);
    UUID pending = bankBooks(TUK75, "PENDING", "-30000.00", TODAY);
    var window = new NettingWindow(TUK75, REPORT_DATE, TODAY, Set.of(pending));

    var netted =
        netting.unsettledTrades(
            window,
            netting.tradeSettlements(window),
            new UnsettledTrades(new BigDecimal("100000.00"), new BigDecimal("0.00")));

    assertThat(netted.after().payables()).isEqualByComparingTo("100000.00");
  }

  @Test
  void eachSideStopsAtZeroWhenTheBankBookedMoreThanTheReportShowed() {
    bankBooks(TUK75, "BIG-BUY", "-500000.00", TODAY);
    bankBooks(TUK75, "BIG-SELL", "90000.00", TODAY);

    var netted =
        netting.unsettledTrades(
            WINDOW,
            netting.tradeSettlements(WINDOW),
            new UnsettledTrades(new BigDecimal("300000.00"), new BigDecimal("50000.00")));

    assertThat(netted.after().payables()).isEqualByComparingTo("0");
    assertThat(netted.after().receivables()).isEqualByComparingTo("0");
  }

  @Test
  void pevaRavaDropsTheSwitchPikAndExitPayoutsBookedSinceTheCycleStarted() {
    var execDay = LocalDate.of(2026, 9, 1);
    var window = new NettingWindow(TUK75, LocalDate.of(2026, 8, 31), execDay, Set.of());
    payout(TUK75, "150000.00", execDay, "38888888888, Vahetamise osakute lunastamine");
    payout(TUK75, "60000.00", execDay, "38888888888, Vahetamine PIK-i");
    payout(TUK75, "100000.00", execDay, "38888888888, RAVA osakute lunastamine");
    payout(TUK75, "30000.00", execDay, "38888888888, Fondipensioni maksete lunastamine");
    payout(
        TUK75,
        "70000.00",
        LocalDate.of(2026, 7, 30),
        "38888888888, Vahetamise osakute lunastamine");

    var netted =
        netting.pevaRava(
            window,
            new PevaRavaForecast(
                new BigDecimal("500000.00"),
                new BigDecimal("500000.00"),
                CYCLE_ENDING_1_SEPTEMBER));

    assertThat(netted.after()).isEqualByComparingTo("190000.00");
    assertThat(netted.before()).isEqualByComparingTo("500000.00");
  }

  @Test
  void pevaRavaStopsAtZeroWhenMoreWasPaidThanForecast() {
    var execDay = LocalDate.of(2026, 9, 1);
    var window = new NettingWindow(TUK75, LocalDate.of(2026, 8, 31), execDay, Set.of());
    payout(TUK75, "650000.00", execDay, "38888888888, Vahetamise osakute lunastamine");

    var netted =
        netting.pevaRava(
            window,
            new PevaRavaForecast(
                new BigDecimal("500000.00"),
                new BigDecimal("500000.00"),
                CYCLE_ENDING_1_SEPTEMBER));

    assertThat(netted.after()).isEqualByComparingTo("0");
  }

  @Test
  void aSwitchingInflowDueTodayOrEarlierIsNotCountedBecauseTheBalanceShowsItOnceLanded() {
    var execDay = LocalDate.of(2026, 9, 1);

    var onTheExecutionDay =
        netting.pevaRava(
            new NettingWindow(TUK75, LocalDate.of(2026, 8, 31), execDay, Set.of()),
            new PevaRavaForecast(
                new BigDecimal("400000.00"),
                new BigDecimal("500000.00"),
                CYCLE_ENDING_1_SEPTEMBER));
    var theDayBefore =
        netting.pevaRava(
            new NettingWindow(
                TUK75, LocalDate.of(2026, 8, 28), LocalDate.of(2026, 8, 31), Set.of()),
            new PevaRavaForecast(
                new BigDecimal("400000.00"),
                new BigDecimal("500000.00"),
                CYCLE_ENDING_1_SEPTEMBER));

    assertThat(onTheExecutionDay.after()).isEqualByComparingTo("500000.00");
    assertThat(theDayBefore.after()).isEqualByComparingTo("400000.00");
  }

  private void payout(TulevaFund fund, String amount, LocalDate day, String remittance) {
    fundBankLedger.recordRegistrarPayout(
        fund, new BigDecimal(amount).negate(), randomUUID(), day, remittance);
    entityManager.flush();
  }

  private TransactionOrder filled(
      TransactionType side, String brokerReference, LocalDate reported) {
    TransactionBatch batch =
        batchRepository.save(TransactionBatch.builder().fund(TUK75).createdBy("test").build());
    TransactionOrder order =
        orderRepository.save(
            TransactionOrder.builder()
                .batch(batch)
                .fund(TUK75)
                .instrumentIsin(ISIN)
                .transactionType(side)
                .instrumentType(ETF)
                .orderQuantity(new BigDecimal("1000"))
                .orderVenue(OrderVenue.SEB)
                .orderUuid(UUID.randomUUID())
                .orderStatus(EXECUTED)
                .orderTimestamp(reported.atStartOfDay(java.time.ZoneOffset.UTC).toInstant())
                .expectedSettlementDate(reported.plusDays(2))
                .build());
    executionRepository.save(
        TransactionExecution.builder()
            .orderId(order.getId())
            .brokerTransactionId(brokerReference)
            .executionTimestamp(Instant.parse("2026-11-19T10:00:00Z"))
            .executedQuantity(new BigDecimal("1000"))
            .totalConsideration(new BigDecimal("50000"))
            .source("SEB_OOTEL")
            .reportedDate(reported)
            .build());
    entityManager.flush();
    return order;
  }

  private UUID bankBooks(TulevaFund fund, String brokerReference, String cash, LocalDate day) {
    UUID externalReference = randomUUID();
    fundBankLedger.recordTradeSettlement(
        fund,
        new BigDecimal(cash),
        new BigDecimal("1000"),
        externalReference,
        FUND_INVESTMENT_CASH_CLEARING,
        new TradeSettlementDetails(
            brokerReference, ISIN, "TICK", "test instrument", BigDecimal.ONE),
        day);
    entityManager.flush();
    return externalReference;
  }
}
