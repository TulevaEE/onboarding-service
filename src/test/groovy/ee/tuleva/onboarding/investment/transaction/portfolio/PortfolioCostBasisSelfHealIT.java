package ee.tuleva.onboarding.investment.transaction.portfolio;

import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.SEB;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellEvents.OVERSELL_CLEARED;
import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellEvents.OVERSELL_FOUND;
import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellEvents.OVERSELL_POSTED;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED;

import ee.tuleva.onboarding.investment.transaction.PortfolioCostBasisService;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEvent;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEventRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionBatch;
import ee.tuleva.onboarding.investment.transaction.TransactionBatchRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionType;
import ee.tuleva.onboarding.investment.transaction.portfolio.RecordingNotificationService.Sent;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
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
  @Autowired private TransactionAuditEventRepository auditEventRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private ObjectProvider<LockProvider> lockProviders;

  private final List<Long> seededBatchIds = new ArrayList<>();
  private final List<Long> seededOrderIds = new ArrayList<>();
  private final List<Long> seededExecutionIds = new ArrayList<>();

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
  @Transactional(propagation = NOT_SUPPORTED)
  void anOversellRollsTheFundBackIsPostedOnceAndClearsWhenAnEarlierBuyIsAddedAfterItsWindow() {
    LocalDate saleDate = LocalDate.of(2026, 5, 3);
    LocalDate windowPastTheSale = saleDate.plusDays(20);
    try {
      seedBaseline();
      seedExecutionOnDate(SELL, saleDate, "150000.0000", "12.00000000");
      Clock clock = Clock.fixed(Instant.parse("2026-05-04T00:30:00Z"), ZoneId.of("Europe/Tallinn"));
      RecordingNotificationService notifications = new RecordingNotificationService();
      PortfolioCostBasisSelfHealJob job =
          new PortfolioCostBasisSelfHealJob(
              clock,
              service,
              baselineRepository,
              new CostBasisOversellAlerter(auditEventRepository, notifications, clock),
              lockProviders);

      job.runForRange(saleDate, saleDate);
      job.runForRange(saleDate, saleDate);
      job.runForRange(windowPastTheSale, windowPastTheSale);

      assertThat(costBasisRepository.findByFundIsinAndAsOfDate(FUND_ISIN, saleDate)).isEmpty();
      assertThat(notifications.sent())
          .containsExactly(
              new Sent(
                  "🔴 TUK75 soetusmaksumus 03.05.2026: IE00BFNM3G45 müük 150000 ühikut, teadaolev"
                      + " positsioon 100000\n"
                      + "Fondi soetusmaksumus on sellest päevast alates vale, kuni see on"
                      + " parandamata\n"
                      + "Leia puuduv ost või vale müügikogus tehinguregistris ja paranda see; öine ümberarvutus parandab ülejäänu",
                  INVESTMENT));
      assertThat(auditEventRepository.findByEventType(OVERSELL_FOUND))
          .extracting(TransactionAuditEvent::getDedupKey, TransactionAuditEvent::getPayload)
          .containsExactly(
              tuple(
                  "TUK75 IE00BFNM3G45 2026-05-03",
                  Map.of(
                      "fund", "TUK75",
                      "instrumentIsin", INSTRUMENT_ISIN,
                      "asOfDate", "2026-05-03",
                      "heldQuantity", "100000",
                      "sellQuantity", "150000")));

      seedExecutionOnDate(BUY, saleDate.minusDays(1), "60000.0000", "11.00000000");
      job.runForRange(windowPastTheSale, windowPastTheSale);
      job.runForRange(windowPastTheSale, windowPastTheSale);

      assertThat(costBasisRepository.findByFundIsinAndAsOfDate(FUND_ISIN, saleDate))
          .extracting(PortfolioCostBasis::getQuantity)
          .containsExactly(new BigDecimal("10000.0000"));
      assertThat(notifications.sent())
          .extracting(Sent::message)
          .containsExactly(
              notifications.sent().getFirst().message(),
              "✅ TUK75 soetusmaksumus 03.05.2026: IE00BFNM3G45 müük ei ületa enam positsiooni,"
                  + " soetusmaksumus on ümber arvutatud");
    } finally {
      deleteCommittedRows(windowPastTheSale);
    }
  }

  @Test
  @Transactional(propagation = NOT_SUPPORTED)
  void aRepairedOversellStaysOpenWhileALaterOneBlocksTheReplayAndClearsWithIt() {
    LocalDate firstSaleDate = LocalDate.of(2026, 5, 3);
    LocalDate secondSaleDate = LocalDate.of(2026, 5, 10);
    LocalDate laterWindow = LocalDate.of(2026, 5, 30);
    try {
      seedBaseline();
      seedExecutionOnDate(SELL, firstSaleDate, "150000.0000", "12.00000000");
      TransactionExecution secondSale =
          seedExecutionOnDate(SELL, secondSaleDate, "50000.0000", "12.00000000");
      Clock clock = Clock.fixed(Instant.parse("2026-05-11T00:30:00Z"), ZoneId.of("Europe/Tallinn"));
      RecordingNotificationService notifications = new RecordingNotificationService();
      PortfolioCostBasisSelfHealJob job =
          new PortfolioCostBasisSelfHealJob(
              clock,
              service,
              baselineRepository,
              new CostBasisOversellAlerter(auditEventRepository, notifications, clock),
              lockProviders);

      job.runForRange(firstSaleDate, secondSaleDate);
      seedExecutionOnDate(BUY, firstSaleDate.minusDays(1), "60000.0000", "11.00000000");
      job.runForRange(laterWindow, laterWindow);

      assertThat(costBasisRepository.findByFundIsinAndAsOfDate(FUND_ISIN, firstSaleDate)).isEmpty();
      assertThat(notifications.sent())
          .extracting(Sent::message)
          .satisfiesExactly(
              first -> assertThat(first).startsWith("🔴 TUK75 soetusmaksumus 03.05.2026:"),
              second -> assertThat(second).startsWith("🔴 TUK75 soetusmaksumus 10.05.2026:"));

      secondSale.setExecutedQuantity(new BigDecimal("5000.0000"));
      executionRepository.save(secondSale);
      job.runForRange(laterWindow, laterWindow);

      assertThat(costBasisRepository.findByFundIsinAndAsOfDate(FUND_ISIN, firstSaleDate))
          .extracting(PortfolioCostBasis::getQuantity)
          .containsExactly(new BigDecimal("10000.0000"));
      assertThat(costBasisRepository.findByFundIsinAndAsOfDate(FUND_ISIN, secondSaleDate))
          .extracting(PortfolioCostBasis::getQuantity)
          .containsExactly(new BigDecimal("5000.0000"));
      assertThat(notifications.sent().subList(2, notifications.sent().size()))
          .extracting(Sent::message)
          .containsExactlyInAnyOrder(
              "✅ TUK75 soetusmaksumus 03.05.2026: IE00BFNM3G45 müük ei ületa enam positsiooni,"
                  + " soetusmaksumus on ümber arvutatud",
              "✅ TUK75 soetusmaksumus 10.05.2026: IE00BFNM3G45 müük ei ületa enam positsiooni,"
                  + " soetusmaksumus on ümber arvutatud");
    } finally {
      deleteCommittedRows(laterWindow);
    }
  }

  private void deleteCommittedRows(LocalDate lastDateRebuilt) {
    auditEventRepository.deleteAll(
        Stream.of(OVERSELL_FOUND, OVERSELL_CLEARED, OVERSELL_POSTED)
            .flatMap(type -> auditEventRepository.findByEventType(type).stream())
            .toList());
    BASELINE_DATE
        .datesUntil(lastDateRebuilt.plusDays(1))
        .forEach(
            date ->
                costBasisRepository.deleteAll(
                    costBasisRepository.findByFundIsinAndAsOfDate(FUND_ISIN, date)));
    baselineRepository.findByFundIsin(FUND_ISIN).ifPresent(baselineRepository::delete);
    executionRepository.deleteAllById(seededExecutionIds);
    orderRepository.deleteAllById(seededOrderIds);
    batchRepository.deleteAllById(seededBatchIds);
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
    baselineRepository.saveAndFlush(baseline);
  }

  private void seedBuyOnDate(LocalDate date, String qty, String price) {
    seedExecutionOnDate(BUY, date, qty, price);
  }

  private TransactionExecution seedExecutionOnDate(
      TransactionType side, LocalDate date, String qty, String price) {
    TransactionBatch batch =
        batchRepository.save(TransactionBatch.builder().fund(TUK75).createdBy("test").build());
    seededBatchIds.add(batch.getId());
    TransactionOrder order =
        orderRepository.save(
            TransactionOrder.builder()
                .batch(batch)
                .fund(TUK75)
                .instrumentIsin(INSTRUMENT_ISIN)
                .transactionType(side)
                .instrumentType(ETF)
                .orderQuantity(new BigDecimal(qty))
                .orderVenue(SEB)
                .orderTimestamp(date.atStartOfDay().toInstant(ZoneOffset.UTC))
                .orderUuid(UUID.randomUUID())
                .build());
    seededOrderIds.add(order.getId());

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
    TransactionExecution saved = executionRepository.saveAndFlush(exec);
    seededExecutionIds.add(saved.getId());
    return saved;
  }
}
