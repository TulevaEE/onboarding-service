package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.SEB;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.investment.transaction.PortfolioCostBasisService;
import ee.tuleva.onboarding.investment.transaction.PositionReconciliationResult.Compared;
import ee.tuleva.onboarding.investment.transaction.PositionReconciliationResult.HoldingDifference;
import ee.tuleva.onboarding.investment.transaction.TransactionBatch;
import ee.tuleva.onboarding.investment.transaction.TransactionBatchRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.investment.transaction.ingest.PortfolioReconciliationMismatchEvent.MismatchEntry;
import ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisRefresh;
import ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisRefresher;
import ee.tuleva.onboarding.investment.transaction.portfolio.PortfolioBaseline;
import ee.tuleva.onboarding.investment.transaction.portfolio.PortfolioBaselineEntry;
import ee.tuleva.onboarding.investment.transaction.portfolio.PortfolioBaselineRepository;
import ee.tuleva.onboarding.investment.transaction.portfolio.PortfolioCostBasis;
import ee.tuleva.onboarding.investment.transaction.portfolio.PortfolioCostBasisRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import(PortfolioReconciliationIT.TestEventRecorder.class)
class PortfolioReconciliationIT {

  private static final String ISIN_A = "IE00BFG1TM61";
  private static final String ISIN_B = "IE0009FT4LX4";
  private static final LocalDate AS_OF = LocalDate.of(2026, 5, 18);

  @Autowired private PortfolioReconciliationService service;
  @Autowired private PortfolioCostBasisRepository costBasisRepository;
  @Autowired private JdbcClient jdbcClient;
  @Autowired private TestEventRecorder recorder;
  @Autowired private PortfolioCostBasisService costBasisService;
  @Autowired private PortfolioBaselineRepository baselineRepository;
  @Autowired private TransactionBatchRepository batchRepository;
  @Autowired private TransactionOrderRepository orderRepository;
  @Autowired private TransactionExecutionRepository executionRepository;
  @Autowired private FundPositionRepository fundPositionRepository;

  @BeforeEach
  void cleanRecorderAndData() {
    recorder.events.clear();
    recorder.skippedEvents.clear();
    deleteNavRows();
    deleteCostBasisRows();
  }

  @AfterEach
  void cleanup() {
    deleteNavRows();
    deleteCostBasisRows();
  }

  @Test
  void quantitiesAgree_noEvent() {
    seedCostBasis(ISIN_A, "10000.0000");
    seedNavReport(ISIN_A, new BigDecimal("10000.0000"));

    service.reconcile(TUK75, AS_OF, CostBasisRefresh.refreshed());

    assertThat(recorder.events).isEmpty();
  }

  @Test
  void ourQuantityDiffers_emitsMismatch() {
    seedCostBasis(ISIN_A, "10005.0000");
    seedNavReport(ISIN_A, new BigDecimal("10000.0000"));

    service.reconcile(TUK75, AS_OF, CostBasisRefresh.refreshed());

    assertThat(recorder.events).hasSize(1);
    PortfolioReconciliationMismatchEvent event = recorder.events.get(0);
    assertThat(event.fund()).isEqualTo(TUK75);
    assertThat(event.asOfDate()).isEqualTo(AS_OF);
    assertThat(event.mismatches()).hasSize(1);
    MismatchEntry entry = event.mismatches().get(0);
    assertThat(entry.isin()).isEqualTo(ISIN_A);
    assertThat(entry.ourQuantity()).isEqualByComparingTo("10005.0000");
    assertThat(entry.theirQuantity()).isEqualByComparingTo("10000.0000");
    assertThat(entry.delta()).isEqualByComparingTo("5.0000");
  }

  @Test
  void navReportRowMissing_emitsMismatch() {
    seedCostBasis(ISIN_A, "10005.0000");
    seedCostBasis(ISIN_B, "250.0000");
    seedNavReport(ISIN_B, new BigDecimal("250.0000"));

    service.reconcile(TUK75, AS_OF, CostBasisRefresh.refreshed());

    assertThat(recorder.events).hasSize(1);
    assertThat(recorder.events.get(0).mismatches()).hasSize(1);
    MismatchEntry entry = recorder.events.get(0).mismatches().get(0);
    assertThat(entry.isin()).isEqualTo(ISIN_A);
    assertThat(entry.ourQuantity()).isEqualByComparingTo("10005.0000");
    assertThat(entry.theirQuantity()).isNull();
  }

  @Test
  void costBasisRowMissing_emitsMismatch() {
    UUID calculationId = UUID.randomUUID();
    seedCostBasis(ISIN_A, "10000.0000");
    seedNavReport(ISIN_A, new BigDecimal("10000.0000"), calculationId);
    seedNavReport(ISIN_B, new BigDecimal("250.0000"), calculationId);

    service.reconcile(TUK75, AS_OF, CostBasisRefresh.refreshed());

    assertThat(recorder.events).hasSize(1);
    assertThat(recorder.events.get(0).mismatches()).hasSize(1);
    MismatchEntry entry = recorder.events.get(0).mismatches().get(0);
    assertThat(entry.isin()).isEqualTo(ISIN_B);
    assertThat(entry.ourQuantity()).isNull();
    assertThat(entry.theirQuantity()).isEqualByComparingTo("250.0000");
  }

  @Test
  void zeroLedgerRowTheNavReportOmits_isNotAMismatch() {
    seedCostBasis(ISIN_A, "10000.0000");
    seedCostBasis(ISIN_B, "0.0000");
    seedNavReport(ISIN_A, new BigDecimal("10000.0000"));

    service.reconcile(TUK75, AS_OF, CostBasisRefresh.refreshed());

    assertThat(recorder.events).isEmpty();
    assertThat(recorder.skippedEvents).isEmpty();
  }

  @Test
  void ledgerDustBelowTheToleranceTheNavReportOmits_isNotAMismatch() {
    seedCostBasis(ISIN_A, "10000.0000");
    seedCostBasis(ISIN_B, "0.0040");
    seedNavReport(ISIN_A, new BigDecimal("10000.0000"));

    service.reconcile(TUK75, AS_OF, CostBasisRefresh.refreshed());

    assertThat(recorder.events).isEmpty();
    assertThat(recorder.skippedEvents).isEmpty();
  }

  @Test
  void aBuyTheRegistryHoldsButTheStoredLedgerLacks_isAMismatchUntilTheRefreshRebuildsTheLedger() {
    seedBaseline(AS_OF.minusDays(2), ISIN_A, "10000.0000");
    seedCostBasis(ISIN_A, "10000.0000");
    seedBuy(ISIN_A, AS_OF, "500.0000");
    seedNavReport(ISIN_A, new BigDecimal("10500.0000"));

    service.reconcile(TUK75, AS_OF, CostBasisRefresh.lockNotTaken());
    CostBasisRefresh refresh = refresherOn(AS_OF).refresh(TUK75);
    service.reconcile(TUK75, AS_OF, refresh);

    assertThat(refresh).isEqualTo(CostBasisRefresh.refreshed());
    assertThat(recorder.events)
        .singleElement()
        .satisfies(
            storedLedgerEvent ->
                assertThat(storedLedgerEvent.mismatches())
                    .singleElement()
                    .returns(ISIN_A, MismatchEntry::isin)
                    .satisfies(
                        entry -> assertThat(entry.delta()).isEqualByComparingTo("-500.0000")));
  }

  @Test
  void wholeCostBasisLedgerMissing_emitsSkippedInsteadOfMismatch() {
    UUID calculationId = UUID.randomUUID();
    seedNavReport(ISIN_A, new BigDecimal("10000.0000"), calculationId);
    seedNavReport(ISIN_B, new BigDecimal("250.0000"), calculationId);

    service.reconcile(TUK75, AS_OF, CostBasisRefresh.refreshed());

    assertThat(recorder.events).isEmpty();
    assertThat(recorder.skippedEvents).hasSize(1);
    var skipped = recorder.skippedEvents.get(0);
    assertThat(skipped.fund()).isEqualTo(TUK75);
    assertThat(skipped.asOfDate()).isEqualTo(AS_OF);
    assertThat(skipped.ourQuantities()).isEmpty();
    assertThat(skipped.theirQuantities()).containsOnlyKeys(ISIN_A, ISIN_B);
    assertThat(skipped.theirQuantities().get(ISIN_A)).isEqualByComparingTo("10000.0000");
    assertThat(skipped.theirQuantities().get(ISIN_B)).isEqualByComparingTo("250.0000");
  }

  @Test
  void wholeNavReportMissing_emitsSkippedInsteadOfMismatch() {
    seedCostBasis(ISIN_A, "10000.0000");
    seedCostBasis(ISIN_B, "250.0000");

    service.reconcile(TUK75, AS_OF, CostBasisRefresh.refreshed());

    assertThat(recorder.events).isEmpty();
    assertThat(recorder.skippedEvents).hasSize(1);
    var skipped = recorder.skippedEvents.get(0);
    assertThat(skipped.theirQuantities()).isEmpty();
    assertThat(skipped.ourQuantities()).containsOnlyKeys(ISIN_A, ISIN_B);
  }

  private CostBasisRefresher refresherOn(LocalDate tallinnToday) {
    ZoneId tallinn = ZoneId.of("Europe/Tallinn");
    LockProvider locksAlwaysFree = configuration -> Optional.of(() -> {});
    return new CostBasisRefresher(
        Clock.fixed(tallinnToday.atTime(12, 0).atZone(tallinn).toInstant(), tallinn),
        costBasisService,
        baselineRepository,
        new StaticListableBeanFactory(Map.of("lockProvider", locksAlwaysFree))
            .getBeanProvider(LockProvider.class));
  }

  private void seedBaseline(LocalDate baselineDate, String isin, String quantity) {
    PortfolioBaseline baseline =
        PortfolioBaseline.builder()
            .fundIsin(TUK75.getIsin())
            .baselineDate(baselineDate)
            .loadedBy("test")
            .build();
    baseline.addEntry(
        PortfolioBaselineEntry.builder()
            .instrumentIsin(isin)
            .quantity(new BigDecimal(quantity))
            .avgUnitCost(new BigDecimal("10.00000000"))
            .build());
    baselineRepository.saveAndFlush(baseline);
  }

  private void seedBuy(String isin, LocalDate tradeDate, String quantity) {
    TransactionBatch batch =
        batchRepository.save(TransactionBatch.builder().fund(TUK75).createdBy("test").build());
    TransactionOrder order =
        orderRepository.save(
            TransactionOrder.builder()
                .batch(batch)
                .fund(TUK75)
                .instrumentIsin(isin)
                .transactionType(BUY)
                .instrumentType(ETF)
                .orderQuantity(new BigDecimal(quantity))
                .orderVenue(SEB)
                .orderTimestamp(tradeDate.atStartOfDay().toInstant(ZoneOffset.UTC))
                .orderUuid(UUID.randomUUID())
                .build());
    executionRepository.saveAndFlush(
        TransactionExecution.builder()
            .orderId(order.getId())
            .brokerTransactionId("DLA-" + tradeDate + "-" + UUID.randomUUID())
            .executionTimestamp(tradeDate.atStartOfDay().toInstant(ZoneOffset.UTC))
            .executedQuantity(new BigDecimal(quantity))
            .unitPrice(new BigDecimal("10.00000000"))
            .totalConsideration(new BigDecimal(quantity).multiply(new BigDecimal("10.00000000")))
            .commissionAmount(BigDecimal.ZERO)
            .scheduledSettlementDate(tradeDate.plusDays(2))
            .source("SEB_OOTEL")
            .reportedDate(tradeDate)
            .build());
  }

  @Test
  void againstThePositionReport_comparesTheRegistryWithTheSebReportRowsOfThatDate() {
    seedCostBasis(ISIN_A, "1000.0000");
    seedCostBasis(ISIN_B, "500.0000");
    seedPositionReport(ISIN_A, "990.000000");
    seedPositionReport(ISIN_B, "500.400000");
    seedNavReport(ISIN_A, new BigDecimal("1000.0000"));

    assertThat(service.againstPositionReport(TUK75, AS_OF, Set.of()))
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(
            new Compared(
                List.of(
                    new HoldingDifference(ISIN_A, new BigDecimal("1000"), new BigDecimal("990")))));
    assertThat(recorder.events).isEmpty();
    assertThat(recorder.skippedEvents).isEmpty();
  }

  private void seedPositionReport(String isin, String quantity) {
    fundPositionRepository.save(
        FundPosition.builder()
            .navDate(AS_OF)
            .fund(TUK75)
            .accountType(SECURITY)
            .accountName(isin)
            .accountId(isin)
            .quantity(new BigDecimal(quantity))
            .marketValue(BigDecimal.ONE)
            .currency("EUR")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build());
  }

  private void seedCostBasis(String isin, String quantity) {
    costBasisRepository.save(
        PortfolioCostBasis.builder()
            .fundIsin(TUK75.getIsin())
            .instrumentIsin(isin)
            .asOfDate(AS_OF)
            .quantity(new BigDecimal(quantity))
            .avgUnitCost(BigDecimal.ZERO)
            .totalCost(BigDecimal.ZERO)
            .source("IT_SEED")
            .build());
  }

  private void seedNavReport(String isin, BigDecimal quantity) {
    seedNavReport(isin, quantity, UUID.randomUUID());
  }

  private void seedNavReport(String isin, BigDecimal quantity, UUID calculationId) {
    jdbcClient
        .sql(
            """
            INSERT INTO nav_report
              (nav_date, fund_code, account_type, account_name, account_id, quantity,
               currency, calculation_id, published_at)
            VALUES (:navDate, :fundCode, 'SECURITY', :name, :isin, :quantity,
               'EUR', :calcId, now())
            """)
        .param("navDate", AS_OF)
        .param("fundCode", TUK75.getCode())
        .param("name", TUK75.getCode() + "-" + isin)
        .param("isin", isin)
        .param("quantity", quantity)
        .param("calcId", calculationId)
        .update();
  }

  private void deleteNavRows() {
    jdbcClient
        .sql("DELETE FROM nav_report WHERE account_id IN (:isins)")
        .param("isins", List.of(ISIN_A, ISIN_B))
        .update();
  }

  private void deleteCostBasisRows() {
    jdbcClient
        .sql(
            "DELETE FROM investment_portfolio_cost_basis WHERE instrument_isin IN (:isins)"
                + " AND fund_isin = :fundIsin")
        .param("isins", List.of(ISIN_A, ISIN_B))
        .param("fundIsin", TUK75.getIsin())
        .update();
  }

  public static class TestEventRecorder {
    final List<PortfolioReconciliationMismatchEvent> events = new ArrayList<>();
    final List<PortfolioReconciliationSkippedEvent> skippedEvents = new ArrayList<>();

    @EventListener
    void onMismatch(PortfolioReconciliationMismatchEvent event) {
      events.add(event);
    }

    @EventListener
    void onSkipped(PortfolioReconciliationSkippedEvent event) {
      skippedEvents.add(event);
    }
  }
}
