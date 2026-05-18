package ee.tuleva.onboarding.investment.transaction.portfolio;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.SEB;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.transaction.PortfolioCostBasisService;
import ee.tuleva.onboarding.investment.transaction.TransactionBatch;
import ee.tuleva.onboarding.investment.transaction.TransactionBatchRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionType;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
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
class PortfolioCostBasisIT {

  private static final String FUND_ISIN = TUK75.getIsin();
  private static final String INSTRUMENT_ISIN = "IE00BFNM3G45";
  private static final LocalDate BASELINE_DATE = LocalDate.of(2026, 4, 30);
  private static final LocalDate TRADE_DATE = LocalDate.of(2026, 5, 1);

  @Autowired private PortfolioCostBasisService service;
  @Autowired private PortfolioCostBasisRepository costBasisRepository;
  @Autowired private PortfolioBaselineRepository baselineRepository;
  @Autowired private TransactionBatchRepository batchRepository;
  @Autowired private TransactionOrderRepository orderRepository;
  @Autowired private TransactionExecutionRepository executionRepository;
  @Autowired private EntityManager entityManager;

  @BeforeEach
  void clean() {
    costBasisRepository.deleteAll();
    baselineRepository.deleteAll();
  }

  @Test
  void runForFundAndDate_appliesBaselinePlusOneBuy_andProducesExpectedCostBasis() {
    seedBaseline();
    seedBuyExecution(new BigDecimal("20000.0000"), new BigDecimal("11.00000000"), "50.00");

    service.runForFundAndDate(TUK75, TRADE_DATE);

    PortfolioCostBasis row =
        costBasisRepository
            .findByFundIsinAndInstrumentIsinAndAsOfDate(FUND_ISIN, INSTRUMENT_ISIN, TRADE_DATE)
            .orElseThrow();

    assertThat(row.getQuantity()).isEqualByComparingTo("120000.0000");
    assertThat(row.getTotalCost()).isEqualByComparingTo("1220050.00");
    assertThat(row.getAvgUnitCost()).isEqualByComparingTo("10.16708333");
    assertThat(row.getDeltaQuantity()).isEqualByComparingTo("20000.0000");
    assertThat(row.getSource()).isEqualTo("DERIVED");
  }

  @Test
  void runForFundAndDate_isIdempotent_whenInvokedTwice() {
    seedBaseline();
    seedBuyExecution(new BigDecimal("20000.0000"), new BigDecimal("11.00000000"), "50.00");

    service.runForFundAndDate(TUK75, TRADE_DATE);
    service.runForFundAndDate(TUK75, TRADE_DATE);

    List<PortfolioCostBasis> rows =
        costBasisRepository.findByFundIsinAndAsOfDate(FUND_ISIN, TRADE_DATE);

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).getQuantity()).isEqualByComparingTo("120000.0000");
  }

  private void seedBaseline() {
    PortfolioBaseline baseline =
        PortfolioBaseline.builder()
            .fundIsin(FUND_ISIN)
            .baselineDate(BASELINE_DATE)
            .loadedBy("test")
            .build();
    baseline.addEntry(
        PortfolioBaselineEntry.builder()
            .instrumentIsin(INSTRUMENT_ISIN)
            .quantity(new BigDecimal("100000.0000"))
            .avgUnitCost(new BigDecimal("10.00000000"))
            .build());
    baselineRepository.save(baseline);
    entityManager.flush();
  }

  private void seedBuyExecution(BigDecimal qty, BigDecimal unitPrice, String commission) {
    TransactionOrder order = persistOrder(BUY);
    TransactionExecution exec =
        TransactionExecution.builder()
            .orderId(order.getId())
            .brokerTransactionId("DLA" + UUID.randomUUID())
            .executionTimestamp(TRADE_DATE.atStartOfDay().toInstant(ZoneOffset.UTC))
            .executedQuantity(qty)
            .unitPrice(unitPrice)
            .totalConsideration(qty.multiply(unitPrice))
            .commissionAmount(new BigDecimal(commission))
            .actualSettlementDate(TRADE_DATE.plusDays(2))
            .source("SEB_OOTEL")
            .build();
    executionRepository.save(exec);
    entityManager.flush();
  }

  private TransactionOrder persistOrder(TransactionType type) {
    TransactionBatch batch =
        batchRepository.save(TransactionBatch.builder().fund(TUK75).createdBy("test").build());
    return orderRepository.save(
        TransactionOrder.builder()
            .batch(batch)
            .fund(TUK75)
            .instrumentIsin(INSTRUMENT_ISIN)
            .transactionType(type)
            .instrumentType(ETF)
            .orderQuantity(20000L)
            .orderVenue(SEB)
            .orderTimestamp(TRADE_DATE.atStartOfDay().toInstant(ZoneOffset.UTC))
            .orderUuid(UUID.randomUUID())
            .build());
  }
}
