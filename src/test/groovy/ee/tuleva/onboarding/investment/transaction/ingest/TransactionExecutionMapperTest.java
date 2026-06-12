package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.SEB;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransactionExecutionMapperTest {

  private final TransactionExecutionMapper mapper = new TransactionExecutionMapper();

  @Test
  void toExecution_mapsAllFields() {
    UUID clientRef = UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29");
    TransactionOrder order =
        TransactionOrder.builder()
            .id(123L)
            .fund(TKF100)
            .instrumentIsin("IE000F60HVH9")
            .transactionType(BUY)
            .instrumentType(ETF)
            .orderVenue(SEB)
            .orderUuid(clientRef)
            .build();
    SebPendingTransactionRow row = sampleRow(clientRef);

    TransactionExecution execution = mapper.toExecution(row, order);

    assertThat(execution.getOrderId()).isEqualTo(123L);
    assertThat(execution.getBrokerTransactionId()).isEqualTo("DLA0799512");
    assertThat(execution.getExecutedQuantity()).isEqualByComparingTo("15007");
    assertThat(execution.getUnitPrice()).isEqualByComparingTo("4.7255");
    assertThat(execution.getTotalConsideration()).isEqualByComparingTo("70915.58");
    assertThat(execution.getSettlementAmount()).isEqualByComparingTo("70915.58");
    assertThat(execution.getCommissionAmount()).isEqualByComparingTo("0.00");
    assertThat(execution.getActualSettlementDate()).isEqualTo(LocalDate.of(2026, 5, 13));
    assertThat(execution.getExecutionTimestamp()).isEqualTo(Instant.parse("2026-05-11T10:26:04Z"));
    assertThat(execution.getSource()).isEqualTo("SEB_OOTEL");
    assertThat(execution.getModifiedBy()).isEqualTo("system:seb-reconciliation");
  }

  @Test
  void toExecution_appliesNewValuesToExistingExecution() {
    UUID clientRef = UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29");
    TransactionOrder order =
        TransactionOrder.builder()
            .id(123L)
            .fund(TKF100)
            .instrumentIsin("IE000F60HVH9")
            .transactionType(BUY)
            .instrumentType(ETF)
            .orderVenue(SEB)
            .orderUuid(clientRef)
            .build();
    SebPendingTransactionRow row = sampleRow(clientRef);

    TransactionExecution existing =
        TransactionExecution.builder()
            .id(7L)
            .orderId(123L)
            .source("SEB_OOTEL")
            .brokerTransactionId("OLD")
            .executedQuantity(new BigDecimal("1"))
            .build();

    mapper.applyTo(existing, row, order);

    assertThat(existing.getId()).isEqualTo(7L);
    assertThat(existing.getBrokerTransactionId()).isEqualTo("DLA0799512");
    assertThat(existing.getExecutedQuantity()).isEqualByComparingTo("15007");
    assertThat(existing.getUnitPrice()).isEqualByComparingTo("4.7255");
    assertThat(existing.getModifiedBy()).isEqualTo("system:seb-reconciliation");
  }

  private static SebPendingTransactionRow sampleRow(UUID clientRef) {
    Map<String, Object> raw = new HashMap<>();
    raw.put("ISIN", "IE000F60HVH9");
    raw.put("Price", new BigDecimal("4.7255"));
    raw.put("Total", new BigDecimal("70915.58"));
    raw.put("Account", "VP68168");
    raw.put("Our ref", "DLA0799512");
    raw.put("Buy/Sell", "Buy");
    raw.put("Quantity", new BigDecimal("15007"));
    raw.put("Broker fee", new BigDecimal("0.00"));
    raw.put("Client ref", clientRef.toString());
    raw.put("Trade date", "2026-05-11T10:26:04Z");
    raw.put("Settlement date", "2026-05-13");
    raw.put("Settlement amount", new BigDecimal("70915.58"));
    raw.put("Client name", "Tuleva Täiendav Kogumisfond");
    raw.put("Instrument name", "ICAV Amundi MSCI USA Screened UCITS ETF");
    return SebPendingTransactionRow.fromRawData(raw);
  }
}
