package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.CANCELLED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SENT;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SETTLED;
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
class TransactionOrderRepositoryIT {

  @Autowired private TransactionOrderRepository orderRepository;
  @Autowired private TransactionBatchRepository batchRepository;
  @Autowired private EntityManager entityManager;

  @Test
  void save_roundTripsComment() {
    TransactionOrder order = persistOrder(TUK75);
    order.setComment("operator note: partial fill expected");
    orderRepository.save(order);

    entityManager.flush();
    entityManager.clear();

    TransactionOrder loaded = orderRepository.findById(order.getId()).orElseThrow();

    assertThat(loaded.getComment()).isEqualTo("operator note: partial fill expected");
  }

  @Test
  void findByFund_returnsOnlyOrdersBelongingToTheGivenFund() {
    TransactionOrder tuk75Order1 = persistOrder(TUK75);
    TransactionOrder tuk75Order2 = persistOrder(TUK75);
    persistOrder(TUK00);

    entityManager.flush();
    entityManager.clear();

    List<TransactionOrder> tuk75Orders = orderRepository.findByFund(TUK75);

    assertThat(tuk75Orders)
        .extracting(TransactionOrder::getId)
        .containsExactlyInAnyOrder(tuk75Order1.getId(), tuk75Order2.getId());
  }

  @Test
  void findByFund_returnsEmptyWhenNoOrdersExistForFund() {
    persistOrder(TUK75);

    assertThat(orderRepository.findByFund(TUK00)).isEmpty();
  }

  @Test
  void findUnsettledOrders_includesSentAndExecutedOrdersPendingSettlement() {
    LocalDate asOfDate = LocalDate.of(2026, 6, 1);
    TransactionOrder sent = persistOrder(TUK75, SENT, asOfDate.plusDays(2), null);
    TransactionOrder executedFuture = persistOrder(TUK75, EXECUTED, asOfDate.plusDays(1), null);
    TransactionOrder executedSettlingToday = persistOrder(TUK75, EXECUTED, asOfDate, null);
    persistOrder(TUK75, EXECUTED, asOfDate.minusDays(1), null);
    persistOrder(TUK75, SETTLED, asOfDate.plusDays(1), null);
    persistOrder(TUK75, CANCELLED, asOfDate.plusDays(1), null);

    List<TransactionOrder> unsettled = orderRepository.findUnsettledOrders(TUK75, asOfDate);

    assertThat(unsettled)
        .extracting(TransactionOrder::getId)
        .containsExactlyInAnyOrder(
            sent.getId(), executedFuture.getId(), executedSettlingToday.getId());
  }

  @Test
  void findUnsettledOrdersAsOf_includesExecutedOrdersAndRespectsCreatedBefore() {
    LocalDate asOfDate = LocalDate.of(2026, 6, 1);
    Instant createdBefore = Instant.parse("2026-06-01T00:00:00Z");
    Instant beforeCutoff = Instant.parse("2026-05-30T10:00:00Z");
    Instant afterCutoff = Instant.parse("2026-06-02T10:00:00Z");

    TransactionOrder sent = persistOrder(TUK75, SENT, asOfDate.plusDays(2), beforeCutoff);
    TransactionOrder executed = persistOrder(TUK75, EXECUTED, asOfDate.plusDays(1), beforeCutoff);
    persistOrder(TUK75, EXECUTED, asOfDate.plusDays(1), afterCutoff);
    persistOrder(TUK75, EXECUTED, asOfDate.minusDays(1), beforeCutoff);
    persistOrder(TUK75, SETTLED, asOfDate.plusDays(1), beforeCutoff);

    List<TransactionOrder> unsettled =
        orderRepository.findUnsettledOrdersAsOf(TUK75, asOfDate, createdBefore);

    assertThat(unsettled)
        .extracting(TransactionOrder::getId)
        .containsExactlyInAnyOrder(sent.getId(), executed.getId());
  }

  private TransactionOrder persistOrder(TulevaFund fund) {
    return persistOrder(fund, SENT, null, null);
  }

  private TransactionOrder persistOrder(
      TulevaFund fund,
      OrderStatus orderStatus,
      LocalDate expectedSettlementDate,
      Instant createdAt) {
    TransactionBatch batch =
        batchRepository.save(TransactionBatch.builder().fund(fund).createdBy("test-user").build());
    return orderRepository.save(
        TransactionOrder.builder()
            .batch(batch)
            .fund(fund)
            .instrumentIsin("IE000F60HVH9")
            .transactionType(BUY)
            .instrumentType(ETF)
            .orderQuantity(new BigDecimal("100"))
            .orderVenue(SEB)
            .orderUuid(UUID.randomUUID())
            .orderStatus(orderStatus)
            .expectedSettlementDate(expectedSettlementDate)
            .createdAt(createdAt)
            .build());
  }
}
