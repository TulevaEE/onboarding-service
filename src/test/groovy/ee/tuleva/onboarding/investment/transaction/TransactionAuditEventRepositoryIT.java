package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.SEB;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class TransactionAuditEventRepositoryIT {

  @Autowired private TransactionAuditEventRepository auditEventRepository;
  @Autowired private TransactionOrderRepository orderRepository;
  @Autowired private TransactionBatchRepository batchRepository;
  @Autowired private EntityManager entityManager;

  @Test
  void save_orderLevelEventWithoutBatch_roundTripsOrderIdAndPayload() {
    TransactionOrder order = persistOrder();

    TransactionAuditEvent event =
        auditEventRepository.save(
            TransactionAuditEvent.builder()
                .orderId(order.getId())
                .eventType("EXECUTION_MATCHED")
                .actor("system")
                .payload(Map.of("ourRef", "DLA0799512", "reportDate", "2026-05-13"))
                .build());
    entityManager.flush();
    entityManager.clear();

    TransactionAuditEvent loaded = auditEventRepository.findById(event.getId()).orElseThrow();

    assertThat(loaded.getBatch()).isNull();
    assertThat(loaded.getOrderId()).isEqualTo(order.getId());
    assertThat(loaded.getEventType()).isEqualTo("EXECUTION_MATCHED");
    assertThat(loaded.getPayload())
        .isEqualTo(Map.of("ourRef", "DLA0799512", "reportDate", "2026-05-13"));
  }

  @Test
  void save_eventWithoutBatchAndOrder_persists() {
    TransactionAuditEvent event =
        auditEventRepository.save(
            TransactionAuditEvent.builder()
                .eventType("UNMATCHED_SEB_TRANSACTION")
                .actor("system")
                .payload(Map.of("isin", "IE000F60HVH9"))
                .build());
    entityManager.flush();
    entityManager.clear();

    TransactionAuditEvent loaded = auditEventRepository.findById(event.getId()).orElseThrow();

    assertThat(loaded.getBatch()).isNull();
    assertThat(loaded.getOrderId()).isNull();
  }

  @Test
  void findByOrderIdAndEventType_returnsOnlyMatchingEvents() {
    TransactionOrder order = persistOrder();
    TransactionOrder otherOrder = persistOrder();

    auditEventRepository.save(
        TransactionAuditEvent.builder()
            .orderId(order.getId())
            .eventType("SETTLEMENT_DETECTED")
            .actor("system")
            .build());
    auditEventRepository.save(
        TransactionAuditEvent.builder()
            .orderId(order.getId())
            .eventType("EXECUTION_MATCHED")
            .actor("system")
            .build());
    auditEventRepository.save(
        TransactionAuditEvent.builder()
            .orderId(otherOrder.getId())
            .eventType("SETTLEMENT_DETECTED")
            .actor("system")
            .build());

    List<TransactionAuditEvent> found =
        auditEventRepository.findByOrderIdAndEventType(order.getId(), "SETTLEMENT_DETECTED");

    assertThat(found)
        .hasSize(1)
        .allSatisfy(
            event -> {
              assertThat(event.getOrderId()).isEqualTo(order.getId());
              assertThat(event.getEventType()).isEqualTo("SETTLEMENT_DETECTED");
            });
  }

  @Test
  void findByEventType_returnsAllEventsOfType() {
    auditEventRepository.save(
        TransactionAuditEvent.builder()
            .eventType("UNMATCHED_SEB_TRANSACTION")
            .actor("system")
            .payload(Map.of("ourRef", "DLA0000001"))
            .build());
    auditEventRepository.save(
        TransactionAuditEvent.builder()
            .eventType("UNMATCHED_SEB_TRANSACTION")
            .actor("system")
            .payload(Map.of("ourRef", "DLA0000002"))
            .build());

    List<TransactionAuditEvent> found =
        auditEventRepository.findByEventType("UNMATCHED_SEB_TRANSACTION");

    assertThat(found)
        .extracting(event -> event.getPayload().get("ourRef"))
        .containsExactlyInAnyOrder("DLA0000001", "DLA0000002");
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
