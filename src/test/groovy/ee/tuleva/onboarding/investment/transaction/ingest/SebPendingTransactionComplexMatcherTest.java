package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.CANCELLED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SENT;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.SEB;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.transaction.InstrumentType;
import ee.tuleva.onboarding.investment.transaction.OrderStatus;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SebPendingTransactionComplexMatcherTest {

  @Mock private TransactionOrderRepository orderRepository;
  @Mock private TransactionExecutionRepository executionRepository;

  private SebPendingTransactionComplexMatcher matcher() {
    return new SebPendingTransactionComplexMatcher(
        orderRepository, executionRepository, new SebClientNameToFundResolver());
  }

  @Test
  void match_etfQuantityWithinTolerance_returnsOrder() {
    TransactionOrder order = orderOf(11L, TUK75, "IE00BFNM3G45", BUY, ETF, 13288L, null, SENT);
    givenCandidates("IE00BFNM3G45", List.of(order));

    SebPendingTransactionRow row =
        row("Tuleva Maailma Aktsiate Pensionifond", "IE00BFNM3G45", "Buy", "13288", null);

    assertThat(matcher().match(row)).contains(order);
  }

  @Test
  void match_etfQuantityOutsideTolerance_returnsEmpty() {
    TransactionOrder order = orderOf(11L, TUK75, "IE00BFNM3G45", BUY, ETF, 13288L, null, SENT);
    givenCandidates("IE00BFNM3G45", List.of(order));

    SebPendingTransactionRow row =
        row("Tuleva Maailma Aktsiate Pensionifond", "IE00BFNM3G45", "Buy", "13289", null);

    assertThat(matcher().match(row)).isEmpty();
  }

  @Test
  void match_fundBuyAmountWithinTwoPercent_returnsOrder() {
    TransactionOrder order =
        orderOf(21L, TKF100, "IE00BFG1TM61", BUY, FUND, null, new BigDecimal("90000.00"), SENT);
    givenCandidates("IE00BFG1TM61", List.of(order));

    // SEB total 91782.00 vs order 90000.00 → 1.98% deviation, within 2%
    SebPendingTransactionRow row =
        row("Tuleva Täiendav Kogumisfond", "IE00BFG1TM61", "Buy", "2669.9", "91782.00");

    assertThat(matcher().match(row)).contains(order);
  }

  @Test
  void match_fundBuyAmountOutsideTwoPercent_returnsEmpty() {
    TransactionOrder order =
        orderOf(21L, TKF100, "IE00BFG1TM61", BUY, FUND, null, new BigDecimal("80000.00"), SENT);
    givenCandidates("IE00BFG1TM61", List.of(order));

    SebPendingTransactionRow row =
        row("Tuleva Täiendav Kogumisfond", "IE00BFG1TM61", "Buy", "2669.9", "91782.00");

    assertThat(matcher().match(row)).isEmpty();
  }

  @Test
  void match_fundSellQuantityWithinTolerance_returnsOrder() {
    TransactionOrder order = orderOf(22L, TKF100, "IE00BFG1TM61", SELL, FUND, 2670L, null, SENT);
    givenCandidates("IE00BFG1TM61", List.of(order));

    SebPendingTransactionRow row =
        row("Tuleva Täiendav Kogumisfond", "IE00BFG1TM61", "Sell", "2670", "91782.00");

    assertThat(matcher().match(row)).contains(order);
  }

  @Test
  void match_skipsCancelledOrders() {
    TransactionOrder cancelled =
        orderOf(31L, TUK75, "IE00BFNM3G45", BUY, ETF, 13288L, null, CANCELLED);
    givenCandidates("IE00BFNM3G45", List.of(cancelled));

    SebPendingTransactionRow row =
        row("Tuleva Maailma Aktsiate Pensionifond", "IE00BFNM3G45", "Buy", "13288", null);

    assertThat(matcher().match(row)).isEmpty();
  }

  @Test
  void match_skipsOrdersAlreadyLinkedToExecution() {
    TransactionOrder order = orderOf(41L, TUK75, "IE00BFNM3G45", BUY, ETF, 13288L, null, SENT);
    givenCandidates("IE00BFNM3G45", List.of(order));
    given(executionRepository.findByOrderId(41L))
        .willReturn(
            Optional.of(TransactionExecution.builder().id(99L).orderId(41L).source("X").build()));

    SebPendingTransactionRow row =
        row("Tuleva Maailma Aktsiate Pensionifond", "IE00BFNM3G45", "Buy", "13288", null);

    assertThat(matcher().match(row)).isEmpty();
  }

  @Test
  void match_unknownClientName_returnsEmpty() {
    SebPendingTransactionRow row =
        row("Some Other Bank Fund", "IE00BFNM3G45", "Buy", "13288", null);

    assertThat(matcher().match(row)).isEmpty();
  }

  @Test
  void match_clientNameAndIsinMismatch_returnsEmpty() {
    // Order belongs to TKF100 but row's clientName resolves to TUK75 with a TKF-only ISIN
    TransactionOrder order = orderOf(51L, TKF100, "IE000F60HVH9", BUY, ETF, 15000L, null, SENT);
    givenCandidates("IE000F60HVH9", List.of(order));

    SebPendingTransactionRow row =
        row("Tuleva Maailma Aktsiate Pensionifond", "IE000F60HVH9", "Buy", "15000", null);

    assertThat(matcher().match(row)).isEmpty();
  }

  @Test
  void match_sideMismatch_returnsEmpty() {
    TransactionOrder order = orderOf(61L, TUK75, "IE00BFNM3G45", BUY, ETF, 13288L, null, SENT);
    givenCandidates("IE00BFNM3G45", List.of(order));

    SebPendingTransactionRow row =
        row("Tuleva Maailma Aktsiate Pensionifond", "IE00BFNM3G45", "Sell", "13288", null);

    assertThat(matcher().match(row)).isEmpty();
  }

  @Test
  void match_multipleMatchingOrders_returnsEmptyAmbiguous() {
    TransactionOrder a = orderOf(71L, TUK75, "IE00BFNM3G45", BUY, ETF, 13288L, null, SENT);
    TransactionOrder b = orderOf(72L, TUK75, "IE00BFNM3G45", BUY, ETF, 13288L, null, SENT);
    givenCandidates("IE00BFNM3G45", List.of(a, b));

    SebPendingTransactionRow row =
        row("Tuleva Maailma Aktsiate Pensionifond", "IE00BFNM3G45", "Buy", "13288", null);

    assertThat(matcher().match(row)).isEmpty();
  }

  private void givenCandidates(String isin, List<TransactionOrder> orders) {
    given(orderRepository.findByInstrumentIsin(isin)).willReturn(orders);
  }

  private static TransactionOrder orderOf(
      Long id,
      TulevaFund fund,
      String isin,
      TransactionType side,
      InstrumentType instrumentType,
      Long quantity,
      BigDecimal amount,
      OrderStatus status) {
    return TransactionOrder.builder()
        .id(id)
        .fund(fund)
        .instrumentIsin(isin)
        .transactionType(side)
        .instrumentType(instrumentType)
        .orderQuantity(quantity)
        .orderAmount(amount)
        .orderVenue(SEB)
        .orderStatus(status)
        .build();
  }

  private static SebPendingTransactionRow row(
      String clientName, String isin, String side, String quantity, String total) {
    Map<String, Object> raw = new HashMap<>();
    raw.put("Client name", clientName);
    raw.put("ISIN", isin);
    raw.put("Buy/Sell", side);
    raw.put("Quantity", new BigDecimal(quantity));
    if (total != null) {
      raw.put("Total", new BigDecimal(total));
      raw.put("Settlement amount", new BigDecimal(total));
    }
    raw.put("Our ref", "DLA0000000");
    raw.put("Trade date", Instant.parse("2026-05-11T10:26:04Z").toString());
    raw.put("Settlement date", LocalDate.of(2026, 5, 13).toString());
    return SebPendingTransactionRow.fromRawData(raw);
  }
}
