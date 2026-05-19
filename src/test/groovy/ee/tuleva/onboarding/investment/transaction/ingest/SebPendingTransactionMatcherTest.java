package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.CANCELLED;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.SEB;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SebPendingTransactionMatcherTest {

  @Mock private TransactionOrderRepository orderRepository;

  @InjectMocks private SebPendingTransactionMatcher matcher;

  @Test
  void match_byClientRef_returnsOrder() {
    UUID clientRef = UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29");
    TransactionOrder order = sampleOrder(clientRef);
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.of(order));

    SebPendingTransactionRow row = sampleRow(clientRef);
    Optional<TransactionOrder> result = matcher.match(row);

    assertThat(result).contains(order);
  }

  @Test
  void match_missingClientRef_returnsEmpty() {
    SebPendingTransactionRow row = sampleRow(null);
    Optional<TransactionOrder> result = matcher.match(row);

    assertThat(result).isEmpty();
  }

  @Test
  void match_cancelledOrder_returnsEmptyToAvoidResurrection() {
    UUID clientRef = UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29");
    TransactionOrder cancelled =
        TransactionOrder.builder()
            .id(123L)
            .fund(TKF100)
            .instrumentIsin("IE000F60HVH9")
            .transactionType(BUY)
            .instrumentType(ETF)
            .orderVenue(SEB)
            .orderUuid(clientRef)
            .orderStatus(CANCELLED)
            .build();
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.of(cancelled));

    SebPendingTransactionRow row = sampleRow(clientRef);
    Optional<TransactionOrder> result = matcher.match(row);

    assertThat(result).isEmpty();
  }

  @Test
  void match_unknownClientRef_returnsEmpty() {
    UUID clientRef = UUID.fromString("00000000-0000-0000-0000-000000000099");
    given(orderRepository.findByOrderUuid(clientRef)).willReturn(Optional.empty());

    SebPendingTransactionRow row = sampleRow(clientRef);
    Optional<TransactionOrder> result = matcher.match(row);

    assertThat(result).isEmpty();
  }

  private static TransactionOrder sampleOrder(UUID clientRef) {
    return TransactionOrder.builder()
        .id(123L)
        .fund(TKF100)
        .instrumentIsin("IE000F60HVH9")
        .transactionType(BUY)
        .instrumentType(ETF)
        .orderVenue(SEB)
        .orderUuid(clientRef)
        .build();
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
    if (clientRef != null) {
      raw.put("Client ref", clientRef.toString());
    }
    raw.put("Trade date", "2026-05-11T10:26:04Z");
    raw.put("Settlement date", "2026-05-13");
    return SebPendingTransactionRow.fromRawData(raw);
  }
}
