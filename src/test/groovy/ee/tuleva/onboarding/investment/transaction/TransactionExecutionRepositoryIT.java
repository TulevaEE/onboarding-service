package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.SEB;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static org.assertj.core.api.Assertions.assertThat;

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

  private TransactionExecution execution(Long orderId, String brokerTxId, Instant timestamp) {
    return TransactionExecution.builder()
        .orderId(orderId)
        .brokerTransactionId(brokerTxId)
        .executionTimestamp(timestamp)
        .source("SEB_OOTEL")
        .build();
  }

  private TransactionOrder persistOrder() {
    TransactionBatch batch =
        batchRepository.save(TransactionBatch.builder().fund(TUK75).createdBy("test-user").build());
    return orderRepository.save(
        TransactionOrder.builder()
            .batch(batch)
            .fund(TUK75)
            .instrumentIsin("IE000F60HVH9")
            .transactionType(BUY)
            .instrumentType(ETF)
            .orderQuantity(15007L)
            .orderVenue(SEB)
            .orderUuid(UUID.randomUUID())
            .build());
  }
}
