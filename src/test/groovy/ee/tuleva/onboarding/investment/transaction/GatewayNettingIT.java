package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.epis.R45TransactionType.Direction.INFLOW;
import static ee.tuleva.onboarding.investment.epis.R45TransactionType.Direction.OUTFLOW;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SETTLED;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_INVESTMENT_CASH_CLEARING;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.context.annotation.FilterType.REGEX;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.investment.epis.R45Row;
import ee.tuleva.onboarding.ledger.FundBankLedger;
import ee.tuleva.onboarding.ledger.TradeSettlementDetails;
import ee.tuleva.onboarding.time.ClockConfig;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
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
  TradeSettlementsInTheBalanceReader.class,
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
  private final Set<UUID> inTodaysReports = new HashSet<>();

  @Autowired GatewayNetting netting;
  @Autowired TradeSettlementsInTheBalanceReader settlementsReader;
  @Autowired PendingOrderImpactService pendingOrderImpactService;
  @MockitoBean PositionPriceResolver positionPriceResolver;
  @Autowired FundBankLedger fundBankLedger;
  @Autowired TransactionBatchRepository batchRepository;
  @Autowired TransactionOrderRepository orderRepository;
  @Autowired TransactionExecutionRepository executionRepository;
  @Autowired EntityManager entityManager;

  @Test
  void theReportsUnsettledTradesDropOnlyWhatTheBankBookedForRegisteredTradesSinceTheReportDate() {
    filled(BUY, "B-1", REPORT_DATE);
    bankBooks(TUK75, "B-1", "-120000.00", TODAY);
    filled(SELL, "S-1", REPORT_DATE);
    bankBooks(TUK75, "S-1", "200000.00", TODAY);
    bankBooks(TUK75, "NO-ORDER", "-30000.00", TODAY);

    var netted =
        settlementsReader
            .read(window())
            .net(new UnsettledTrades(new BigDecimal("300000.00"), new BigDecimal("200000.00")));

    assertThat(netted.after().payables()).isEqualByComparingTo("180000.00");
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
        settlementsReader
            .read(window())
            .net(new UnsettledTrades(new BigDecimal("300000.00"), new BigDecimal("0.00")));

    assertThat(netted.after().payables()).isEqualByComparingTo("300000.00");
  }

  @Test
  void aFundBuyReportedOnTheReportDateIsReservedByItsOrderSoItsSettlementLeavesTheRowsAlone() {
    var order = filled(BUY, "FUND-BUY", REPORT_DATE);
    order.setInstrumentType(InstrumentType.FUND);
    orderRepository.save(order);
    bankBooks(TUK75, "FUND-BUY", "-60000.00", TODAY);

    var netted =
        settlementsReader
            .read(window())
            .net(new UnsettledTrades(new BigDecimal("300000.00"), new BigDecimal("0.00")));

    assertThat(netted.after().payables()).isEqualByComparingTo("300000.00");
  }

  @Test
  void anOrderExecutedTodayWhoseSettlementTheBankBookedBeforeTheRegisterMarkedItIsCountedOnce() {
    filled(BUY, "B-TODAY", TODAY);
    bankBooks(TUK75, "B-TODAY", "-50000.00", TODAY);
    var settlements = settlementsReader.read(window());

    var pendingOrders =
        pendingOrderImpactService.calculate(
            TUK75,
            TODAY,
            REPORT_DATE,
            TradePrepPricingFixture.noResolvedPrices(),
            settlements.carriedByPendingFills());

    assertThat(pendingOrders.pendingBuys()).isEqualByComparingTo("0");
    assertThat(pendingOrders.buysInTheBalance()).isEqualByComparingTo("50000");
    assertThat(
            settlements
                .net(new UnsettledTrades(new BigDecimal("300000.00"), new BigDecimal("0.00")))
                .after()
                .payables())
        .isEqualByComparingTo("300000.00");
  }

  @Test
  void aSettlementBookedTodayFromAReportProcessedAfterTheBalancesIsNotNetted() {
    filled(BUY, "B-1", REPORT_DATE);
    bankBooksAfterTheBalance(TUK75, "B-1", "-120000.00", TODAY);

    var netted =
        settlementsReader
            .read(window())
            .net(new UnsettledTrades(new BigDecimal("300000.00"), new BigDecimal("0.00")));

    assertThat(netted.after().payables()).isEqualByComparingTo("300000.00");
  }

  @Test
  void aSentSaleDueTodayStillReservesItsUnits() {
    sentSale("600", TODAY);

    assertThat(unitsReservedForSales()).isEqualByComparingTo("600");
  }

  @Test
  void anOverdueSentSaleStillReservesItsUnits() {
    sentSale("600", TODAY.minusDays(2));

    assertThat(unitsReservedForSales()).isEqualByComparingTo("600");
  }

  @Test
  void anExecutedSaleWhoseFillTheReportDoesNotShowYetReservesTheFill() {
    saleWithFill(OrderStatus.EXECUTED, "600", "300", TODAY);

    assertThat(unitsReservedForSales()).isEqualByComparingTo("300");
  }

  @Test
  void anExecutedSaleWhoseFillTheReportShowsReservesNothing() {
    saleWithFill(OrderStatus.EXECUTED, "600", "300", REPORT_DATE);

    assertThat(unitsReservedForSales()).isEqualByComparingTo("0");
  }

  @Test
  void aCancelledSaleReservesOnlyTheFillTheReportDoesNotShowYet() {
    saleWithFill(OrderStatus.CANCELLED, "600", "200", TODAY);

    assertThat(unitsReservedForSales()).isEqualByComparingTo("200");
  }

  private void saleWithFill(
      OrderStatus status, String orderQuantity, String fillQuantity, LocalDate reported) {
    TransactionBatch batch =
        batchRepository.save(TransactionBatch.builder().fund(TUK75).createdBy("test").build());
    TransactionOrder order =
        orderRepository.save(
            TransactionOrder.builder()
                .batch(batch)
                .fund(TUK75)
                .instrumentIsin(ISIN)
                .transactionType(SELL)
                .instrumentType(InstrumentType.FUND)
                .orderQuantity(new BigDecimal(orderQuantity))
                .orderVenue(OrderVenue.SEB)
                .orderStatus(status)
                .orderTimestamp(Instant.parse("2026-11-18T08:00:00Z"))
                .expectedSettlementDate(REPORT_DATE)
                .build());
    executionRepository.save(
        TransactionExecution.builder()
            .orderId(order.getId())
            .brokerTransactionId("FILL-" + status)
            .executionTimestamp(Instant.parse("2026-11-18T10:00:00Z"))
            .executedQuantity(new BigDecimal(fillQuantity))
            .totalConsideration(new BigDecimal("10000"))
            .source("SEB_OOTEL")
            .reportedDate(reported)
            .build());
    entityManager.flush();
  }

  private BigDecimal unitsReservedForSales() {
    return pendingOrderImpactService
        .calculate(TUK75, TODAY, REPORT_DATE, TradePrepPricingFixture.noResolvedPrices(), Set.of())
        .unreportedSellQuantities()
        .getOrDefault(ISIN, BigDecimal.ZERO);
  }

  private void sentSale(String quantity, LocalDate expectedSettlement) {
    TransactionBatch batch =
        batchRepository.save(TransactionBatch.builder().fund(TUK75).createdBy("test").build());
    orderRepository.save(
        TransactionOrder.builder()
            .batch(batch)
            .fund(TUK75)
            .instrumentIsin(ISIN)
            .transactionType(SELL)
            .instrumentType(InstrumentType.FUND)
            .orderQuantity(new BigDecimal(quantity))
            .orderVenue(OrderVenue.SEB)
            .orderStatus(OrderStatus.SENT)
            .orderTimestamp(Instant.parse("2026-11-18T08:00:00Z"))
            .expectedSettlementDate(expectedSettlement)
            .build());
    entityManager.flush();
  }

  @Test
  void bookingsOnTheReportDateOrForAnotherFundOrStillPendingAreNotNetted() {
    bankBooks(TUK75, "ON-P", "-10000.00", REPORT_DATE);
    bankBooks(TUK00, "OTHER", "-20000.00", TODAY);
    UUID pending = bankBooks(TUK75, "PENDING", "-30000.00", TODAY);
    var window =
        new NettingWindow(TUK75, REPORT_DATE, TODAY, Set.of(pending), Set.copyOf(inTodaysReports));

    var netted =
        settlementsReader
            .read(window)
            .net(new UnsettledTrades(new BigDecimal("100000.00"), new BigDecimal("0.00")));

    assertThat(netted.after().payables()).isEqualByComparingTo("100000.00");
  }

  @Test
  void aSettlementWithNoRegisteredTradeThatFitsItIsNotNettedButListed() {
    bankBooks(TUK75, "NO-ORDER", "-30000.00", TODAY);
    filled(SELL, "WRONG-SIDE", REPORT_DATE);
    bankBooks(TUK75, "WRONG-SIDE", "-20000.00", TODAY);

    var settlements = settlementsReader.read(window());

    assertThat(
            settlements
                .net(new UnsettledTrades(new BigDecimal("300000.00"), new BigDecimal("0.00")))
                .after()
                .payables())
        .isEqualByComparingTo("300000.00");
    assertThat(settlements.unmatched())
        .extracting(unmatched -> unmatched.booking().brokerReference())
        .containsExactlyInAnyOrder("NO-ORDER", "WRONG-SIDE");
  }

  @Test
  void twoBookingsForOneTradeAreBothUnexplainedSoNeitherClearsAPayable() {
    filled(BUY, "A", REPORT_DATE);
    filled(BUY, "B", REPORT_DATE);
    bankBooks(TUK75, "A", "-50000.00", TODAY);
    bankBooks(TUK75, "A", "-50000.00", TODAY);

    var settlements = settlementsReader.read(window());

    assertThat(
            settlements
                .net(new UnsettledTrades(new BigDecimal("100000.00"), new BigDecimal("0.00")))
                .after()
                .payables())
        .isEqualByComparingTo("100000.00");
    assertThat(settlements.unmatched()).hasSize(2);
  }

  @Test
  void eachSideStopsAtZeroWhenTheBankBookedMoreThanTheReportShowed() {
    filled(BUY, "BIG-BUY", REPORT_DATE);
    filled(SELL, "BIG-SELL", REPORT_DATE);
    bankBooks(TUK75, "BIG-BUY", "-500000.00", TODAY);
    bankBooks(TUK75, "BIG-SELL", "90000.00", TODAY);

    var netted =
        settlementsReader
            .read(window())
            .net(new UnsettledTrades(new BigDecimal("300000.00"), new BigDecimal("50000.00")));

    assertThat(netted.after().payables()).isEqualByComparingTo("0");
    assertThat(netted.after().receivables()).isEqualByComparingTo("0");
  }

  @Test
  void r45RowsSettlingTodayReserveTheirWholeOutflowWhateverWasPaidTodayAndTheirInflowsDoNotCount() {
    var window = new NettingWindow(TUV100, REPORT_DATE, TODAY, Set.of(), Set.of());
    payout(TUV100, "70000.00", TODAY, "38888888888, Osakute lunastamine");
    payout(TUV100, "10000.00", TODAY, "38888888888, Osakute vahetamine");
    payout(TUV100, "5000.00", REPORT_DATE, "38888888888, Osakute lunastamine");
    payout(TUV100, "9000.00", TODAY, "38888888888, Fondipensioni maksete lunastamine");

    var netted =
        netting.r45(
            window,
            List.of(
                new R45Row(OUTFLOW, new BigDecimal("100000.00"), TODAY),
                new R45Row(INFLOW, new BigDecimal("40000.00"), TODAY),
                new R45Row(OUTFLOW, new BigDecimal("30000.00"), TODAY.plusDays(3)),
                new R45Row(INFLOW, new BigDecimal("25000.00"), TODAY.plusDays(3)),
                new R45Row(OUTFLOW, new BigDecimal("8000.00"), null),
                new R45Row(INFLOW, new BigDecimal("6000.00"), null),
                new R45Row(OUTFLOW, new BigDecimal("50000.00"), REPORT_DATE)));

    assertThat(netted.before()).isEqualByComparingTo("67000.00");
    assertThat(netted.after()).isEqualByComparingTo("113000.00");
  }

  @Test
  void laterR45RowsReserveOnlyTheOutflowBeyondTheirInflow() {
    var window = new NettingWindow(TUV100, REPORT_DATE, TODAY, Set.of(), Set.of());

    var netted =
        netting.r45(
            window,
            List.of(
                new R45Row(OUTFLOW, new BigDecimal("30000.00"), TODAY.plusDays(1)),
                new R45Row(INFLOW, new BigDecimal("45000.00"), TODAY.plusDays(2))));

    assertThat(netted.after()).isEqualByComparingTo("0");
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

  private NettingWindow window() {
    return new NettingWindow(TUK75, REPORT_DATE, TODAY, Set.of(), Set.copyOf(inTodaysReports));
  }

  private UUID bankBooks(TulevaFund fund, String brokerReference, String cash, LocalDate day) {
    UUID externalReference = bankBooksAfterTheBalance(fund, brokerReference, cash, day);
    inTodaysReports.add(externalReference);
    return externalReference;
  }

  private UUID bankBooksAfterTheBalance(
      TulevaFund fund, String brokerReference, String cash, LocalDate day) {
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
