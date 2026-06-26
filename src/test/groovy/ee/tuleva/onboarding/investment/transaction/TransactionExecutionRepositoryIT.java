package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.SEB;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.fund.TulevaFund;
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
            .actualSettlementDate(LocalDate.of(2026, 5, 13))
            .navDate(LocalDate.of(2026, 5, 12))
            .comment("test execution")
            .source("SEB_OOTEL")
            .sourceFileKey("seb/2026-05-13_pending_transactions.csv")
            .modifiedBy("test-user")
            .build();

    TransactionExecution saved = executionRepository.save(execution);
    entityManager.flush();
    entityManager.clear();

    TransactionExecution loaded = executionRepository.findByOrderId(order.getId()).orElseThrow();

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
    assertThat(loaded.getActualSettlementDate()).isEqualTo(LocalDate.of(2026, 5, 13));
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
  void findByOrderId_returnsEmptyWhenMissing() {
    assertThat(executionRepository.findByOrderId(999_999L)).isEmpty();
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

  private TransactionExecution execution(Long orderId, String brokerTxId, Instant timestamp) {
    return TransactionExecution.builder()
        .orderId(orderId)
        .brokerTransactionId(brokerTxId)
        .executionTimestamp(timestamp)
        .source("SEB_OOTEL")
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
        .build();
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
