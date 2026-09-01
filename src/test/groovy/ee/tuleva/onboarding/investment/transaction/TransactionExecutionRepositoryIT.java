package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.CANCELLED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SENT;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.SEB;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class TransactionExecutionRepositoryIT {

  @Autowired private TransactionExecutionRepository executionRepository;
  @Autowired private TransactionOrderRepository orderRepository;
  @Autowired private TransactionBatchRepository batchRepository;
  @Autowired private EntityManager entityManager;

  @Test
  void insertAndReadByOrderId_roundTripsAllFields() {
    TransactionOrder order = persistOrder();

    Instant executionTimestamp = Instant.parse("2026-05-11T10:26:04Z");
    TransactionExecution execution =
        TransactionExecution.builder()
            .orderId(order.getId())
            .brokerTransactionId("DLA0799512")
            .aggregatedOrderId(UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29"))
            .executionTimestamp(executionTimestamp)
            .executedQuantity(new BigDecimal("15007.0000"))
            .unitPrice(new BigDecimal("4.72550000"))
            .totalConsideration(new BigDecimal("70915.58"))
            .settlementAmount(new BigDecimal("70915.58"))
            .commissionAmount(new BigDecimal("0.00"))
            .settlementFeeAmount(new BigDecimal("0.00"))
            .settlementPenalty(new BigDecimal("0.00"))
            .netSettlementAmount(new BigDecimal("70915.58"))
            .scheduledSettlementDate(LocalDate.of(2026, 5, 13))
            .reportedDate(LocalDate.of(2026, 5, 12))
            .navDate(LocalDate.of(2026, 5, 12))
            .comment("test execution")
            .source("SEB_OOTEL")
            .sourceFileKey("seb/2026-05-13_pending_transactions.csv")
            .modifiedBy("test-user")
            .build();

    TransactionExecution saved = executionRepository.save(execution);
    entityManager.flush();
    entityManager.clear();

    var executions = executionRepository.findAllByOrderId(order.getId());
    assertThat(executions).hasSize(1);
    TransactionExecution loaded = executions.getFirst();

    assertThat(loaded.getId()).isEqualTo(saved.getId());
    assertThat(loaded.getOrderId()).isEqualTo(order.getId());
    assertThat(loaded.getBrokerTransactionId()).isEqualTo("DLA0799512");
    assertThat(loaded.getAggregatedOrderId())
        .isEqualTo(UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29"));
    assertThat(loaded.getExecutionTimestamp()).isEqualTo(executionTimestamp);
    assertThat(loaded.getExecutedQuantity()).isEqualByComparingTo("15007.0000");
    assertThat(loaded.getUnitPrice()).isEqualByComparingTo("4.72550000");
    assertThat(loaded.getTotalConsideration()).isEqualByComparingTo("70915.58");
    assertThat(loaded.getSettlementAmount()).isEqualByComparingTo("70915.58");
    assertThat(loaded.getCommissionAmount()).isEqualByComparingTo("0.00");
    assertThat(loaded.getSettlementFeeAmount()).isEqualByComparingTo("0.00");
    assertThat(loaded.getSettlementPenalty()).isEqualByComparingTo("0.00");
    assertThat(loaded.getNetSettlementAmount()).isEqualByComparingTo("70915.58");
    assertThat(loaded.getScheduledSettlementDate()).isEqualTo(LocalDate.of(2026, 5, 13));
    assertThat(loaded.getReportedDate()).isEqualTo(LocalDate.of(2026, 5, 12));
    assertThat(loaded.getNavDate()).isEqualTo(LocalDate.of(2026, 5, 12));
    assertThat(loaded.getComment()).isEqualTo("test execution");
    assertThat(loaded.getSource()).isEqualTo("SEB_OOTEL");
    assertThat(loaded.getSourceFileKey()).isEqualTo("seb/2026-05-13_pending_transactions.csv");
    assertThat(loaded.getModifiedBy()).isEqualTo("test-user");
    assertThat(loaded.getCreatedAt()).isNotNull();
    assertThat(loaded.getUpdatedAt()).isNotNull();
    assertThat(loaded.getVersion()).isNotNull();
  }

  @Test
  void findByBrokerTransactionId_returnsMatchingRow() {
    TransactionOrder order = persistOrder();

    executionRepository.save(
        TransactionExecution.builder()
            .orderId(order.getId())
            .brokerTransactionId("DLA0000001")
            .source("SEB_OOTEL")
            .reportedDate(LocalDate.of(2026, 5, 11))
            .build());

    TransactionExecution loaded =
        executionRepository.findByBrokerTransactionId("DLA0000001").orElseThrow();

    assertThat(loaded.getBrokerTransactionId()).isEqualTo("DLA0000001");
    assertThat(loaded.getOrderId()).isEqualTo(order.getId());
  }

  @Test
  void findByBrokerTransactionId_returnsEmptyWhenMissing() {
    assertThat(executionRepository.findByBrokerTransactionId("NOPE")).isEmpty();
  }

  @Test
  void findAllByOrderId_returnsEmptyWhenMissing() {
    assertThat(executionRepository.findAllByOrderId(999_999L)).isEmpty();
  }

  @Test
  void findByOrderIdInAndExecutionTimestampInRange_returnsOnlyMatchingOrdersWithinHalfOpenWindow() {
    TransactionOrder withinOrder1 = persistOrder();
    TransactionOrder withinOrder2 = persistOrder();
    TransactionOrder beforeOrder = persistOrder();
    TransactionOrder boundaryOrder = persistOrder();
    TransactionOrder unrelatedOrder = persistOrder();

    Instant inside = Instant.parse("2026-05-11T10:00:00Z");
    Instant fromInclusive = Instant.parse("2026-05-11T00:00:00Z");
    Instant toExclusive = Instant.parse("2026-05-12T00:00:00Z");
    Instant beforeWindow = Instant.parse("2026-05-10T23:59:59Z");
    Instant atUpperBoundary = toExclusive;

    TransactionExecution withinForOrder1 =
        executionRepository.save(execution(withinOrder1.getId(), "DLA_W1", inside));
    TransactionExecution withinForOrder2 =
        executionRepository.save(execution(withinOrder2.getId(), "DLA_W2", inside));
    executionRepository.save(execution(beforeOrder.getId(), "DLA_BEFORE", beforeWindow));
    executionRepository.save(execution(boundaryOrder.getId(), "DLA_BOUNDARY", atUpperBoundary));
    executionRepository.save(execution(unrelatedOrder.getId(), "DLA_UNRELATED", inside));

    entityManager.flush();
    entityManager.clear();

    List<TransactionExecution> matches =
        executionRepository.findByOrderIdInAndExecutionTimestampInRange(
            List.of(
                withinOrder1.getId(),
                withinOrder2.getId(),
                beforeOrder.getId(),
                boundaryOrder.getId()),
            fromInclusive,
            toExclusive);

    assertThat(matches)
        .extracting(TransactionExecution::getId)
        .containsExactlyInAnyOrder(withinForOrder1.getId(), withinForOrder2.getId());
  }

  @Test
  void findByOrderIdInAndExecutionTimestampInRange_returnsEmptyForEmptyOrderIds() {
    assertThat(
            executionRepository.findByOrderIdInAndExecutionTimestampInRange(
                List.of(), Instant.EPOCH, Instant.parse("2099-01-01T00:00:00Z")))
        .isEmpty();
  }

  @Test
  void sumCommissionsForFundAndPeriod_sumsFeesForFundTradesWithinHalfOpenWindow() {
    TransactionOrder fundOrder1 = persistOrder(TUK75);
    TransactionOrder fundOrder2 = persistOrder(TUK75);
    TransactionOrder fundOrderBefore = persistOrder(TUK75);
    TransactionOrder fundOrderAtUpperBound = persistOrder(TUK75);
    TransactionOrder otherFundOrder = persistOrder(TUV100);

    Instant fromInclusive = Instant.parse("2026-05-01T00:00:00Z");
    Instant toExclusive = Instant.parse("2026-06-01T00:00:00Z");
    Instant inside = Instant.parse("2026-05-11T10:00:00Z");

    executionRepository.save(
        executionWithFees(fundOrder1.getId(), "DLA_F1", inside, "10.00", "2.50"));
    // settlement fee null exercises the COALESCE in the sum
    executionRepository.save(executionWithFees(fundOrder2.getId(), "DLA_F2", inside, "5.00", null));
    executionRepository.save(
        executionWithFees(
            fundOrderBefore.getId(),
            "DLA_BEFORE",
            fromInclusive.minusSeconds(1),
            "99.00",
            "99.00"));
    executionRepository.save(
        executionWithFees(
            fundOrderAtUpperBound.getId(), "DLA_BOUND", toExclusive, "99.00", "99.00"));
    executionRepository.save(
        executionWithFees(otherFundOrder.getId(), "DLA_OTHER", inside, "77.00", "77.00"));

    entityManager.flush();
    entityManager.clear();

    BigDecimal sum =
        executionRepository.sumCommissionsForFundAndPeriod(
            TUK75.getCode(), fromInclusive, toExclusive);

    assertThat(sum).isEqualByComparingTo("17.50");
  }

  @Test
  void sumCommissionsForFundAndPeriod_returnsZeroWhenNoTradesInWindow() {
    TransactionOrder order = persistOrder(TUK75);
    executionRepository.save(
        executionWithFees(
            order.getId(), "DLA_OUT", Instant.parse("2026-01-15T10:00:00Z"), "10.00", "10.00"));
    entityManager.flush();

    BigDecimal sum =
        executionRepository.sumCommissionsForFundAndPeriod(
            TUK75.getCode(),
            Instant.parse("2026-05-01T00:00:00Z"),
            Instant.parse("2026-06-01T00:00:00Z"));

    assertThat(sum).isEqualByComparingTo("0");
  }

  @Test
  void sumExecutedQuantitiesByIsin_keepsBuysAndSellsSeparatePerInstrument() {
    LocalDate fromExclusive = LocalDate.of(2026, 5, 1);
    LocalDate toInclusive = LocalDate.of(2026, 5, 31);
    LocalDate inside = LocalDate.of(2026, 5, 11);

    TransactionOrder buy = persistOrder(TUK75, "IE0009FT4LX4", BUY, SENT);
    TransactionOrder sell = persistOrder(TUK75, "IE0009FT4LX4", SELL, SENT);
    TransactionOrder otherInstrument = persistOrder(TUK75, "IE00BFG1TM61", BUY, SENT);

    executionRepository.save(executionWithQuantity(buy.getId(), "DLA_B1", inside, "1000"));
    executionRepository.save(executionWithQuantity(buy.getId(), "DLA_B2", inside, "500"));
    executionRepository.save(executionWithQuantity(sell.getId(), "DLA_S1", inside, "200"));
    executionRepository.save(
        executionWithQuantity(otherInstrument.getId(), "DLA_O1", inside, "900"));

    entityManager.flush();
    entityManager.clear();

    var summaries =
        executionRepository.sumExecutedQuantitiesByIsin(
            TUK75.getCode(), fromExclusive, toInclusive);

    assertThat(summaries)
        .extracting(ExecutedQuantitySummary::getIsin)
        .containsExactlyInAnyOrder("IE0009FT4LX4", "IE00BFG1TM61");
    assertThat(summaryFor(summaries, "IE0009FT4LX4").getBought()).isEqualByComparingTo("1500");
    assertThat(summaryFor(summaries, "IE0009FT4LX4").getSold()).isEqualByComparingTo("200");
    assertThat(summaryFor(summaries, "IE00BFG1TM61").getBought()).isEqualByComparingTo("900");
    assertThat(summaryFor(summaries, "IE00BFG1TM61").getSold()).isEqualByComparingTo("0");
  }

  private ExecutedQuantitySummary summaryFor(List<ExecutedQuantitySummary> summaries, String isin) {
    return summaries.stream()
        .filter(summary -> summary.getIsin().equals(isin))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void sumExecutedQuantitiesByIsin_excludesOtherFundsCancelledOrdersAndTradesOutsideWindow() {
    LocalDate fromExclusive = LocalDate.of(2026, 5, 1);
    LocalDate toInclusive = LocalDate.of(2026, 5, 31);
    LocalDate inside = LocalDate.of(2026, 5, 11);

    TransactionOrder otherFund = persistOrder(TUV100, "IE0009FT4LX4", BUY, SENT);
    TransactionOrder cancelled = persistOrder(TUK75, "IE0009FT4LX4", BUY, CANCELLED);
    TransactionOrder inWindow = persistOrder(TUK75, "IE0009FT4LX4", BUY, SENT);

    executionRepository.save(executionWithQuantity(otherFund.getId(), "DLA_OF", inside, "700"));
    executionRepository.save(executionWithQuantity(cancelled.getId(), "DLA_CX", inside, "600"));
    executionRepository.save(executionWithQuantity(inWindow.getId(), "DLA_IN", inside, "100"));
    executionRepository.save(
        executionWithQuantity(inWindow.getId(), "DLA_BEFORE", LocalDate.of(2026, 5, 1), "800"));
    executionRepository.save(
        executionWithQuantity(inWindow.getId(), "DLA_AFTER", LocalDate.of(2026, 6, 1), "900"));

    entityManager.flush();
    entityManager.clear();

    var summaries =
        executionRepository.sumExecutedQuantitiesByIsin(
            TUK75.getCode(), fromExclusive, toInclusive);

    assertThat(summaries)
        .singleElement()
        .satisfies(
            summary -> {
              assertThat(summary.getIsin()).isEqualTo("IE0009FT4LX4");
              assertThat(summary.getBought()).isEqualByComparingTo("100");
              assertThat(summary.getSold()).isEqualByComparingTo("0");
            });
  }

  @Test
  void sumExecutedQuantitiesByIsin_windowsOnWhenTheCustodianReportedNotOnSettlementOrTradeDate() {
    LocalDate fromExclusive = LocalDate.of(2026, 5, 1);
    LocalDate toInclusive = LocalDate.of(2026, 5, 31);

    TransactionOrder order = persistOrder(TUK75, "IE0009FT4LX4", BUY, SENT);

    executionRepository.save(
        executionWithQuantity(
            order.getId(),
            "DLA_REPORTED_IN_WINDOW",
            LocalDate.of(2026, 5, 11),
            "100",
            LocalDate.of(2026, 6, 15),
            Instant.parse("2026-04-02T10:00:00Z")));
    executionRepository.save(
        executionWithQuantity(
            order.getId(),
            "DLA_REPORTED_BEFORE_WINDOW",
            LocalDate.of(2026, 4, 20),
            "700",
            LocalDate.of(2026, 5, 11),
            Instant.parse("2026-04-20T10:00:00Z")));

    entityManager.flush();
    entityManager.clear();

    var summaries =
        executionRepository.sumExecutedQuantitiesByIsin(
            TUK75.getCode(), fromExclusive, toInclusive);

    assertThat(summaries)
        .singleElement()
        .satisfies(summary -> assertThat(summary.getBought()).isEqualByComparingTo("100"));
  }

  @Test
  void sumExecutedQuantitiesByIsin_ignoresHistoricalImportRowsThatNeverCameFromACustodianReport() {
    LocalDate fromExclusive = LocalDate.of(2026, 5, 1);
    LocalDate toInclusive = LocalDate.of(2026, 5, 31);
    LocalDate inside = LocalDate.of(2026, 5, 11);

    TransactionOrder order = persistOrder(TUK75, "IE0009FT4LX4", BUY, SENT);

    executionRepository.save(executionWithQuantity(order.getId(), "DLA_SEB", inside, "100"));
    executionRepository.save(
        TransactionExecution.builder()
            .orderId(order.getId())
            .brokerTransactionId("DLA_UNDATEABLE")
            .executedQuantity(new BigDecimal("900"))
            .reportedDate(inside)
            .source("HISTORICAL_IMPORT")
            .build());

    entityManager.flush();
    entityManager.clear();

    var summaries =
        executionRepository.sumExecutedQuantitiesByIsin(
            TUK75.getCode(), fromExclusive, toInclusive);

    assertThat(summaries)
        .singleElement()
        .satisfies(summary -> assertThat(summary.getBought()).isEqualByComparingTo("100"));
  }

  private TransactionExecution executionWithQuantity(
      Long orderId, String brokerTxId, LocalDate reportedDate, String quantity) {
    return executionWithQuantity(
        orderId,
        brokerTxId,
        reportedDate,
        quantity,
        null,
        reportedDate.atStartOfDay(UTC).toInstant());
  }

  private TransactionExecution executionWithQuantity(
      Long orderId,
      String brokerTxId,
      LocalDate reportedDate,
      String quantity,
      LocalDate scheduledSettlementDate,
      Instant executionTimestamp) {
    return TransactionExecution.builder()
        .orderId(orderId)
        .brokerTransactionId(brokerTxId)
        .executionTimestamp(executionTimestamp)
        .executedQuantity(new BigDecimal(quantity))
        .scheduledSettlementDate(scheduledSettlementDate)
        .reportedDate(reportedDate)
        .source("SEB_OOTEL")
        .build();
  }

  private TransactionOrder persistOrder(
      TulevaFund fund, String isin, TransactionType side, OrderStatus status) {
    TransactionBatch batch =
        batchRepository.save(TransactionBatch.builder().fund(fund).createdBy("test-user").build());
    return orderRepository.save(
        TransactionOrder.builder()
            .batch(batch)
            .fund(fund)
            .instrumentIsin(isin)
            .transactionType(side)
            .instrumentType(ETF)
            .orderQuantity(new BigDecimal("15007"))
            .orderVenue(SEB)
            .orderStatus(status)
            .orderUuid(UUID.randomUUID())
            .build());
  }

  private TransactionExecution execution(Long orderId, String brokerTxId, Instant timestamp) {
    return TransactionExecution.builder()
        .orderId(orderId)
        .brokerTransactionId(brokerTxId)
        .executionTimestamp(timestamp)
        .source("SEB_OOTEL")
        .reportedDate(LocalDate.ofInstant(timestamp, java.time.ZoneOffset.UTC))
        .build();
  }

  private TransactionExecution executionWithFees(
      Long orderId, String brokerTxId, Instant timestamp, String commission, String settlementFee) {
    return TransactionExecution.builder()
        .orderId(orderId)
        .brokerTransactionId(brokerTxId)
        .executionTimestamp(timestamp)
        .commissionAmount(new BigDecimal(commission))
        .settlementFeeAmount(settlementFee == null ? null : new BigDecimal(settlementFee))
        .source("SEB_OOTEL")
        .reportedDate(LocalDate.ofInstant(timestamp, java.time.ZoneOffset.UTC))
        .build();
  }

  @Test
  void insert_stampsCreatedAndUpdatedTimestamps() {
    TransactionOrder order = persistOrder();

    TransactionExecution saved =
        executionRepository.save(
            TransactionExecution.builder()
                .orderId(order.getId())
                .executedQuantity(new BigDecimal("100"))
                .source("SEB_OOTEL")
                .reportedDate(LocalDate.of(2026, 5, 12))
                .build());
    entityManager.flush();

    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
  }

  @Test
  void insert_keepsTimestampsThatWereSetExplicitly() {
    TransactionOrder order = persistOrder();
    Instant createdAt = Instant.parse("2026-05-11T10:26:04Z");
    Instant updatedAt = Instant.parse("2026-05-12T11:00:00Z");

    TransactionExecution saved =
        executionRepository.save(
            TransactionExecution.builder()
                .orderId(order.getId())
                .executedQuantity(new BigDecimal("100"))
                .source("SEB_OOTEL")
                .reportedDate(LocalDate.of(2026, 5, 12))
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build());
    entityManager.flush();

    assertThat(saved.getCreatedAt()).isEqualTo(createdAt);
    assertThat(saved.getUpdatedAt()).isEqualTo(updatedAt);
  }

  @Test
  void update_movesUpdatedAtButLeavesCreatedAtAndReportedDateAlone() {
    TransactionOrder order = persistOrder();
    Instant createdAt = Instant.parse("2026-05-11T10:26:04Z");
    LocalDate reportedDate = LocalDate.of(2026, 5, 12);

    TransactionExecution saved =
        executionRepository.save(
            TransactionExecution.builder()
                .orderId(order.getId())
                .executedQuantity(new BigDecimal("100"))
                .source("SEB_OOTEL")
                .reportedDate(reportedDate)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build());
    entityManager.flush();
    entityManager.clear();

    TransactionExecution loaded = executionRepository.findById(saved.getId()).orElseThrow();
    loaded.setExecutedQuantity(new BigDecimal("150"));
    executionRepository.save(loaded);
    entityManager.flush();
    entityManager.clear();

    TransactionExecution updated = executionRepository.findById(saved.getId()).orElseThrow();
    assertThat(updated.getExecutedQuantity()).isEqualByComparingTo(new BigDecimal("150"));
    assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
    assertThat(updated.getReportedDate()).isEqualTo(reportedDate);
    assertThat(updated.getUpdatedAt()).isAfter(createdAt);
  }

  private TransactionOrder persistOrder() {
    return persistOrder(TUK75);
  }

  private TransactionOrder persistOrder(TulevaFund fund) {
    TransactionBatch batch =
        batchRepository.save(TransactionBatch.builder().fund(fund).createdBy("test-user").build());
    return orderRepository.save(
        TransactionOrder.builder()
            .batch(batch)
            .fund(fund)
            .instrumentIsin("IE000F60HVH9")
            .transactionType(BUY)
            .instrumentType(ETF)
            .orderQuantity(new BigDecimal("15007"))
            .orderVenue(SEB)
            .orderUuid(UUID.randomUUID())
            .build());
  }
}
