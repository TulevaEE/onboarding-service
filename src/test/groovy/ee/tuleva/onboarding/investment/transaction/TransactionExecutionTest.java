package ee.tuleva.onboarding.investment.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransactionExecutionTest {

  @Test
  void builder_appliesFieldsAsProvided() {
    Instant tradeTimestamp = Instant.parse("2026-05-11T10:26:04Z");
    UUID aggregated = UUID.randomUUID();

    TransactionExecution execution =
        TransactionExecution.builder()
            .orderId(42L)
            .brokerTransactionId("DLA0799512")
            .aggregatedOrderId(aggregated)
            .executionTimestamp(tradeTimestamp)
            .executedQuantity(new BigDecimal("15007.0000"))
            .unitPrice(new BigDecimal("4.72550000"))
            .totalConsideration(new BigDecimal("70915.58"))
            .actualSettlementDate(LocalDate.of(2026, 5, 13))
            .source("SEB_OOTEL")
            .build();

    assertThat(execution.getOrderId()).isEqualTo(42L);
    assertThat(execution.getBrokerTransactionId()).isEqualTo("DLA0799512");
    assertThat(execution.getAggregatedOrderId()).isEqualTo(aggregated);
    assertThat(execution.getExecutionTimestamp()).isEqualTo(tradeTimestamp);
    assertThat(execution.getExecutedQuantity()).isEqualByComparingTo("15007.0000");
    assertThat(execution.getUnitPrice()).isEqualByComparingTo("4.72550000");
    assertThat(execution.getTotalConsideration()).isEqualByComparingTo("70915.58");
    assertThat(execution.getActualSettlementDate()).isEqualTo(LocalDate.of(2026, 5, 13));
    assertThat(execution.getSource()).isEqualTo("SEB_OOTEL");
  }

  @Test
  void builder_defaultsTimestampsToNullBeforePersist() {
    TransactionExecution execution =
        TransactionExecution.builder().orderId(1L).source("MANUAL").build();

    assertThat(execution.getCreatedAt()).isNull();
    assertThat(execution.getUpdatedAt()).isNull();
    assertThat(execution.getVersion()).isNull();
  }
}
