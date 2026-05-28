package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.SEB;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.fund.TulevaFund;
import jakarta.persistence.EntityManager;
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
            .orderQuantity(100L)
            .orderVenue(SEB)
            .orderUuid(UUID.randomUUID())
            .build());
  }
}
