package ee.tuleva.onboarding.investment.transaction.portfolio;

import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.SEB;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
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
  private static final String ISIN_BOUGHT_AFTER_THE_BASELINE = "IE000I9HGDZ3";
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

  @Test
  void rebuildFromBaselineCarriesAPositionBoughtBeforeTheSelfHealWindowIntoASellInsideIt() {
    seedBaseline();
    seedExecutionOnDate(ISIN_BOUGHT_AFTER_THE_BASELINE, BUY, LocalDate.of(2026, 5, 4), "8000.0000");
    LocalDate sellDate = LocalDate.of(2026, 6, 10);
    seedExecutionOnDate(ISIN_BOUGHT_AFTER_THE_BASELINE, SELL, sellDate, "5279.0000");
    LocalDate today = LocalDate.of(2026, 6, 15);

    jobOn(today).rebuildFromBaseline();

    assertThat(
            costBasisRepository.findByFundIsinAndInstrumentIsinAndAsOfDate(
                FUND_ISIN, ISIN_BOUGHT_AFTER_THE_BASELINE, sellDate))
        .get()
        .returns(new BigDecimal("2721.0000"), PortfolioCostBasis::getQuantity);
    assertThat(
            costBasisRepository.findByFundIsinAndInstrumentIsinAndAsOfDate(
                FUND_ISIN, INSTRUMENT_ISIN, BASELINE_DATE.plusDays(1)))
        .get()
        .returns(new BigDecimal("100000.0000"), PortfolioCostBasis::getQuantity);
  }

  @Test
  void rebuildFromBaselineFailsTheTriggerWhileTheHistoryStillLacksTheBuyBehindASell() {
    seedBaseline();
    seedExecutionOnDate(
        ISIN_BOUGHT_AFTER_THE_BASELINE, SELL, LocalDate.of(2026, 6, 10), "5279.0000");

    assertThatThrownBy(() -> jobOn(LocalDate.of(2026, 6, 15)).rebuildFromBaseline())
        .isInstanceOf(IllegalStateException.class);
  }

  private PortfolioCostBasisSelfHealJob jobOn(LocalDate today) {
    ZoneId tallinn = ZoneId.of("Europe/Tallinn");
    Clock clock = Clock.fixed(today.atStartOfDay(tallinn).toInstant(), tallinn);
    return new PortfolioCostBasisSelfHealJob(
        clock, service, baselineRepository, configuration -> Optional.of(() -> {}));
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
    seedExecutionOnDate(INSTRUMENT_ISIN, BUY, date, qty, price);
  }

  private void seedExecutionOnDate(String isin, TransactionType type, LocalDate date, String qty) {
    seedExecutionOnDate(isin, type, date, qty, "10.00000000");
  }

  private void seedExecutionOnDate(
      String isin, TransactionType type, LocalDate date, String qty, String price) {
    TransactionBatch batch =
        batchRepository.save(TransactionBatch.builder().fund(TUK75).createdBy("test").build());
    TransactionOrder order =
        orderRepository.save(
            TransactionOrder.builder()
                .batch(batch)
                .fund(TUK75)
                .instrumentIsin(isin)
                .transactionType(type)
                .instrumentType(ETF)
                .orderQuantity(new BigDecimal(qty))
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
            .scheduledSettlementDate(date.plusDays(2))
            .source("SEB_OOTEL")
            .reportedDate(date)
            .build();
    executionRepository.save(exec);
    entityManager.flush();
  }
}
