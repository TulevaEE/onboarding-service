package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.SEB;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationAuditRecorder.PRICING_DATE_SET;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.transaction.TransactionAuditEventRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionBatch;
import ee.tuleva.onboarding.investment.transaction.TransactionBatchRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class SebFundConfirmationPricingDateWriterIT {

  private static final String REFERENCE = "DLA0000001";
  private static final LocalDate PRICING_DATE = LocalDate.of(2026, 1, 6);

  @Autowired private TransactionExecutionRepository executionRepository;
  @Autowired private TransactionOrderRepository orderRepository;
  @Autowired private TransactionBatchRepository batchRepository;
  @Autowired private TransactionAuditEventRepository auditEventRepository;
  @Autowired private EntityManager entityManager;

  private SebFundConfirmationPricingDateWriter writer;

  @BeforeEach
  void setUp() {
    writer =
        new SebFundConfirmationPricingDateWriter(
            executionRepository,
            new SebFundConfirmationAuditRecorder(
                auditEventRepository,
                Clock.fixed(Instant.parse("2026-01-08T10:00:00Z"), ZoneOffset.UTC)));
  }

  @Test
  void write_setsThePricingDateOnTheExecutionWithTheConfirmationsTradeReference() {
    TransactionExecution execution = persistExecution(null);

    writer.write(confirmation());

    assertThat(reloaded(execution).getPricingDate()).isEqualTo(PRICING_DATE);
    assertThat(auditEventRepository.findByEventTypeAndDedupKey(PRICING_DATE_SET, REFERENCE))
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getOrderId()).isEqualTo(execution.getOrderId());
              assertThat(event.getPayload())
                  .containsEntry("executionId", String.valueOf(execution.getId()))
                  .containsEntry("pricingDate", "2026-01-06")
                  .doesNotContainKey("previousPricingDate");
            });
  }

  @Test
  void write_replacesADifferentPricingDateAndRecordsTheOneItReplaced() {
    TransactionExecution execution = persistExecution(LocalDate.of(2026, 1, 5));

    writer.write(confirmation());

    assertThat(reloaded(execution).getPricingDate()).isEqualTo(PRICING_DATE);
    assertThat(auditEventRepository.findByEventTypeAndDedupKey(PRICING_DATE_SET, REFERENCE))
        .singleElement()
        .satisfies(
            event ->
                assertThat(event.getPayload()).containsEntry("previousPricingDate", "2026-01-05"));
  }

  @Test
  void write_leavesAnExecutionThatAlreadyHasThePricingDateUntouched() {
    TransactionExecution execution = persistExecution(PRICING_DATE);
    Long version = execution.getVersion();

    writer.write(confirmation());

    assertThat(reloaded(execution).getVersion()).isEqualTo(version);
    assertThat(auditEventRepository.findByEventTypeAndDedupKey(PRICING_DATE_SET, REFERENCE))
        .isEmpty();
  }

  @Test
  void write_waitsUntilTheTradeHasAnExecution() {
    writer.write(confirmation());

    assertThat(executionRepository.findByBrokerTransactionId(REFERENCE)).isEmpty();
    assertThat(auditEventRepository.findByEventTypeAndDedupKey(PRICING_DATE_SET, REFERENCE))
        .isEmpty();
  }

  private TransactionExecution persistExecution(@Nullable LocalDate pricingDate) {
    TransactionBatch batch =
        batchRepository.save(TransactionBatch.builder().fund(TUK75).createdBy("test-user").build());
    TransactionOrder order =
        orderRepository.save(
            TransactionOrder.builder()
                .batch(batch)
                .fund(TUK75)
                .instrumentIsin("IE00BKPTWY98")
                .transactionType(BUY)
                .instrumentType(FUND)
                .orderQuantity(new BigDecimal("1000"))
                .orderVenue(SEB)
                .orderUuid(UUID.randomUUID())
                .build());
    TransactionExecution execution =
        executionRepository.save(
            TransactionExecution.builder()
                .orderId(order.getId())
                .brokerTransactionId(REFERENCE)
                .executionTimestamp(Instant.parse("2026-01-05T12:48:00Z"))
                .pricingDate(pricingDate)
                .source("SEB_OOTEL")
                .build());
    entityManager.flush();
    entityManager.clear();
    return execution;
  }

  private TransactionExecution reloaded(TransactionExecution execution) {
    entityManager.flush();
    entityManager.clear();
    return executionRepository.findById(execution.getId()).orElseThrow();
  }

  private static SebFundConfirmation confirmation() {
    return new SebFundConfirmation(
        TUK75,
        REFERENCE,
        Instant.parse("2026-01-05T09:48:17Z"),
        PRICING_DATE,
        LocalDate.of(2026, 1, 9),
        "IE00BKPTWY98",
        BUY,
        new BigDecimal("1000.00"),
        new BigDecimal("12.4"),
        new BigDecimal("12400.00"),
        new BigDecimal("0.00"),
        new BigDecimal("12400.00"));
  }
}
