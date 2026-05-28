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
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
class PortfolioCostBasisSelfHealIT {

  private static final String FUND_ISIN = TUK75.getIsin();
  private static final String INSTRUMENT_ISIN = "IE00BFNM3G45";
  private static final LocalDate BASELINE_DATE = LocalDate.of(2026, 4, 30);

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
  void rebuildRange_reconstructsMiddleRowsAfterDeletion() {
    seedBaseline();
    seedBuyOnDate(LocalDate.of(2026, 5, 1), "1000.0000", "10.00000000");
    seedBuyOnDate(LocalDate.of(2026, 5, 2), "500.0000", "12.00000000");
    seedBuyOnDate(LocalDate.of(2026, 5, 3), "200.0000", "13.00000000");
    seedBuyOnDate(LocalDate.of(2026, 5, 4), "100.0000", "14.00000000");
    seedBuyOnDate(LocalDate.of(2026, 5, 5), "50.0000", "15.00000000");

    LocalDate from = LocalDate.of(2026, 5, 1);
    LocalDate to = LocalDate.of(2026, 5, 5);

    service.rebuildRange(TUK75, from, to);

    PortfolioCostBasis day3Before =
        costBasisRepository
            .findByFundIsinAndInstrumentIsinAndAsOfDate(
                FUND_ISIN, INSTRUMENT_ISIN, LocalDate.of(2026, 5, 3))
            .orElseThrow();
    BigDecimal day3QtyBefore = day3Before.getQuantity();
    BigDecimal day3CostBefore = day3Before.getTotalCost();

    costBasisRepository.delete(day3Before);
    entityManager.flush();

    service.rebuildRange(TUK75, from, to);

    PortfolioCostBasis day3After =
        costBasisRepository
            .findByFundIsinAndInstrumentIsinAndAsOfDate(
                FUND_ISIN, INSTRUMENT_ISIN, LocalDate.of(2026, 5, 3))
            .orElseThrow();

    assertThat(day3After.getQuantity()).isEqualByComparingTo(day3QtyBefore);
    assertThat(day3After.getTotalCost()).isEqualByComparingTo(day3CostBefore);
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

  private void seedBuyOnDate(LocalDate date, String qty, String price) {
    TransactionBatch batch =
        batchRepository.save(TransactionBatch.builder().fund(TUK75).createdBy("test").build());
    TransactionOrder order =
        orderRepository.save(
            TransactionOrder.builder()
                .batch(batch)
                .fund(TUK75)
                .instrumentIsin(INSTRUMENT_ISIN)
                .transactionType(BUY)
                .instrumentType(ETF)
                .orderQuantity(Long.parseLong(qty.split("\\.")[0]))
                .orderVenue(SEB)
                .orderTimestamp(date.atStartOfDay().toInstant(ZoneOffset.UTC))
                .orderUuid(UUID.randomUUID())
                .build());

    TransactionExecution exec =
        TransactionExecution.builder()
            .orderId(order.getId())
            .brokerTransactionId("DLA-" + date + "-" + UUID.randomUUID())
            .executionTimestamp(date.atStartOfDay().toInstant(ZoneOffset.UTC))
            .executedQuantity(new BigDecimal(qty))
            .unitPrice(new BigDecimal(price))
            .totalConsideration(new BigDecimal(qty).multiply(new BigDecimal(price)))
            .commissionAmount(BigDecimal.ZERO)
            .actualSettlementDate(date.plusDays(2))
            .source("SEB_OOTEL")
            .build();
    executionRepository.save(exec);
    entityManager.flush();
  }
}
