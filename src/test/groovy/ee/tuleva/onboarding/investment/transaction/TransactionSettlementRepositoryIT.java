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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class TransactionSettlementRepositoryIT {

  @Autowired private TransactionSettlementRepository settlementRepository;
  @Autowired private TransactionOrderRepository orderRepository;
  @Autowired private TransactionBatchRepository batchRepository;
  @Autowired private EntityManager entityManager;

  @Test
  void insertAndReadByOrderId_roundTripsAllFields() {
    TransactionOrder order = persistOrder();

    Instant settledAt = Instant.parse("2026-05-13T07:00:00Z");
    TransactionSettlement settlement =
        TransactionSettlement.builder()
            .orderId(order.getId())
            .settledAt(settledAt)
            .reportDate(LocalDate.of(2026, 5, 13))
            .build();

    TransactionSettlement saved = settlementRepository.save(settlement);
    entityManager.flush();
    entityManager.clear();

    TransactionSettlement loaded = settlementRepository.findByOrderId(order.getId()).orElseThrow();

    assertThat(loaded.getId()).isEqualTo(saved.getId());
    assertThat(loaded.getOrderId()).isEqualTo(order.getId());
    assertThat(loaded.getSettledAt()).isEqualTo(settledAt);
    assertThat(loaded.getReportDate()).isEqualTo(LocalDate.of(2026, 5, 13));
    assertThat(loaded.getCreatedAt()).isNotNull();
  }

  @Test
  void existsByOrderId_returnsTrueWhenSettlementExists() {
    TransactionOrder order = persistOrder();

    settlementRepository.save(
        TransactionSettlement.builder()
            .orderId(order.getId())
            .settledAt(Instant.parse("2026-05-13T07:00:00Z"))
            .reportDate(LocalDate.of(2026, 5, 13))
            .build());

    assertThat(settlementRepository.existsByOrderId(order.getId())).isTrue();
  }

  @Test
  void existsByOrderId_returnsFalseWhenMissing() {
    assertThat(settlementRepository.existsByOrderId(999_999L)).isFalse();
  }

  @Test
  void findByOrderId_returnsEmptyWhenMissing() {
    assertThat(settlementRepository.findByOrderId(999_999L)).isEmpty();
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
            .orderQuantity(new BigDecimal("15007"))
            .orderVenue(SEB)
            .orderUuid(UUID.randomUUID())
            .build());
  }
}
