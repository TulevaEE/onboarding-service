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
import static ee.tuleva.onboarding.investment.transaction.ingest.QuantityAmountMismatchEvent.MismatchKind.ETF_QUANTITY;
import static ee.tuleva.onboarding.investment.transaction.ingest.QuantityAmountMismatchEvent.MismatchKind.FUND_BUY_AMOUNT;
import static ee.tuleva.onboarding.investment.transaction.ingest.QuantityAmountMismatchEvent.MismatchKind.FUND_SELL_QUANTITY;
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
    return matcher(new TransactionMatchingProperties(null, null, null, null));
  }

  private SebPendingTransactionComplexMatcher matcher(TransactionMatchingProperties properties) {
    return new SebPendingTransactionComplexMatcher(
        orderRepository,
        executionRepository,
        new SebClientNameToFundResolver(),
        new QuantityAmountValidator(properties));
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
  void match_fundBuyAmountDeltaExactlyTwoPercentOfOrderedAmount_returnsEmpty() {
    TransactionOrder order =
        orderOf(24L, TKF100, "IE00BFG1TM61", BUY, FUND, null, new BigDecimal("100000.00"), SENT);
    givenCandidates("IE00BFG1TM61", List.of(order));

    // delta 2000.00: /ordered = 2.000% (>= 2% → no match), /executed = 1.961% (would match)
    SebPendingTransactionRow row =
        row("Tuleva Täiendav Kogumisfond", "IE00BFG1TM61", "Buy", "2900", "102000.00");

    assertThat(matcher().match(row)).isEmpty();
  }

  @Test
  void match_fundBuyAmountWithinTwoPercentOfOrderedAmountOnly_returnsOrder() {
    TransactionOrder order =
        orderOf(25L, TKF100, "IE00BFG1TM61", BUY, FUND, null, new BigDecimal("100000.00"), SENT);
    givenCandidates("IE00BFG1TM61", List.of(order));

    // delta 1970.00: /ordered = 1.970% (< 2% → match), /executed = 2.010% (would not match)
    SebPendingTransactionRow row =
        row("Tuleva Täiendav Kogumisfond", "IE00BFG1TM61", "Buy", "2850", "98030.00");

    assertThat(matcher().match(row)).contains(order);
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

  @Test
  void match_etfQuantityWithinConfiguredWiderTolerance_returnsOrder() {
    TransactionOrder order = orderOf(12L, TUK75, "IE00BFNM3G45", BUY, ETF, 13288L, null, SENT);
    givenCandidates("IE00BFNM3G45", List.of(order));

    SebPendingTransactionRow row =
        row("Tuleva Maailma Aktsiate Pensionifond", "IE00BFNM3G45", "Buy", "13289", null);

    TransactionMatchingProperties widerTolerance =
        new TransactionMatchingProperties(new BigDecimal("2"), null, null, null);

    assertThat(matcher(widerTolerance).match(row)).contains(order);
  }

  @Test
  void match_fundBuyAmountWithinConfiguredWiderTolerance_returnsOrder() {
    TransactionOrder order =
        orderOf(23L, TKF100, "IE00BFG1TM61", BUY, FUND, null, new BigDecimal("80000.00"), SENT);
    givenCandidates("IE00BFG1TM61", List.of(order));

    SebPendingTransactionRow row =
        row("Tuleva Täiendav Kogumisfond", "IE00BFG1TM61", "Buy", "2669.9", "91782.00");

    TransactionMatchingProperties widerTolerance =
        new TransactionMatchingProperties(null, new BigDecimal("0.15"), null, null);

    assertThat(matcher(widerTolerance).match(row)).contains(order);
  }

  @Test
  void findNearMiss_usesConfiguredNearMissMultiplier() {
    TransactionOrder order = orderOf(13L, TUK75, "IE00BFNM3G45", BUY, ETF, 13288L, null, SENT);
    givenCandidates("IE00BFNM3G45", List.of(order));

    SebPendingTransactionRow row =
        row("Tuleva Maailma Aktsiate Pensionifond", "IE00BFNM3G45", "Buy", "13288.0008", null);

    TransactionMatchingProperties widerMultiplier =
        new TransactionMatchingProperties(null, null, null, new BigDecimal("10"));

    assertThat(matcher().findNearMiss(row)).isEmpty();
    assertThat(matcher(widerMultiplier).findNearMiss(row)).isPresent();
  }

  @Test
  void findNearMiss_inToleranceMatch_returnsEmpty() {
    TransactionOrder order = orderOf(81L, TUK75, "IE00BFNM3G45", BUY, ETF, 13288L, null, SENT);
    givenCandidates("IE00BFNM3G45", List.of(order));

    SebPendingTransactionRow row =
        row("Tuleva Maailma Aktsiate Pensionifond", "IE00BFNM3G45", "Buy", "13288", null);

    assertThat(matcher().findNearMiss(row)).isEmpty();
  }

  @Test
  void findNearMiss_etfQuantityOutsideToleranceButWithinFiveX_returnsNearMiss() {
    TransactionOrder order = orderOf(82L, TUK75, "IE00BFNM3G45", BUY, ETF, 13288L, null, SENT);
    givenCandidates("IE00BFNM3G45", List.of(order));

    // Tolerance = 0.0001, 5x = 0.0005. Diff 0.0003 is outside tolerance but inside 5x.
    SebPendingTransactionRow row =
        row("Tuleva Maailma Aktsiate Pensionifond", "IE00BFNM3G45", "Buy", "13288.0003", null);

    Optional<QuantityAmountMismatchEvent> nearMiss = matcher().findNearMiss(row);
    assertThat(nearMiss).isPresent();
    QuantityAmountMismatchEvent event = nearMiss.get();
    assertThat(event.kind()).isEqualTo(ETF_QUANTITY);
    assertThat(event.order()).isEqualTo(order);
    assertThat(event.expected()).isEqualByComparingTo("13288");
    assertThat(event.actual()).isEqualByComparingTo("13288.0003");
    assertThat(event.delta()).isEqualByComparingTo("0.0003");
    assertThat(event.row()).isEqualTo(row);
  }

  @Test
  void findNearMiss_etfQuantityOutsideFiveXTolerance_returnsEmpty() {
    TransactionOrder order = orderOf(83L, TUK75, "IE00BFNM3G45", BUY, ETF, 13288L, null, SENT);
    givenCandidates("IE00BFNM3G45", List.of(order));

    // Tolerance × 5 = 0.0005. Diff of 1 is way outside.
    SebPendingTransactionRow row =
        row("Tuleva Maailma Aktsiate Pensionifond", "IE00BFNM3G45", "Buy", "13289", null);

    assertThat(matcher().findNearMiss(row)).isEmpty();
  }

  @Test
  void findNearMiss_multipleCandidatesWithinFiveX_returnsEmpty() {
    TransactionOrder a = orderOf(84L, TUK75, "IE00BFNM3G45", BUY, ETF, 13288L, null, SENT);
    TransactionOrder b = orderOf(85L, TUK75, "IE00BFNM3G45", BUY, ETF, 13288L, null, SENT);
    givenCandidates("IE00BFNM3G45", List.of(a, b));

    SebPendingTransactionRow row =
        row("Tuleva Maailma Aktsiate Pensionifond", "IE00BFNM3G45", "Buy", "13288.0003", null);

    assertThat(matcher().findNearMiss(row)).isEmpty();
  }

  @Test
  void findNearMiss_fundBuyAmountOutsideToleranceButWithinFiveX_returnsNearMiss() {
    TransactionOrder order =
        orderOf(86L, TKF100, "IE00BFG1TM61", BUY, FUND, null, new BigDecimal("90000.00"), SENT);
    givenCandidates("IE00BFG1TM61", List.of(order));

    // Tolerance 2%, 5x = 10%. delta 8000 / ordered 90000 = 8.89% — outside 2%, within 10%.
    SebPendingTransactionRow row =
        row("Tuleva Täiendav Kogumisfond", "IE00BFG1TM61", "Buy", "2700", "98000.00");

    Optional<QuantityAmountMismatchEvent> nearMiss = matcher().findNearMiss(row);
    assertThat(nearMiss).isPresent();
    QuantityAmountMismatchEvent event = nearMiss.get();
    assertThat(event.kind()).isEqualTo(FUND_BUY_AMOUNT);
    assertThat(event.order()).isEqualTo(order);
    assertThat(event.expected()).isEqualByComparingTo("90000.00");
    assertThat(event.actual()).isEqualByComparingTo("98000.00");
  }

  @Test
  void findNearMiss_fundBuyAmountDeltaExactlyTenPercentOfOrderedAmount_returnsEmpty() {
    TransactionOrder order =
        orderOf(95L, TKF100, "IE00BFG1TM61", BUY, FUND, null, new BigDecimal("90000.00"), SENT);
    givenCandidates("IE00BFG1TM61", List.of(order));

    // delta 9000: /ordered = 10.000% (>= 5×2% → outside band), /executed = 9.09% (would pass)
    SebPendingTransactionRow row =
        row("Tuleva Täiendav Kogumisfond", "IE00BFG1TM61", "Buy", "2700", "99000.00");

    assertThat(matcher().findNearMiss(row)).isEmpty();
  }

  @Test
  void findNearMiss_fundSellQuantityOutsideToleranceButWithinFiveX_returnsNearMiss() {
    TransactionOrder order = orderOf(87L, TKF100, "IE00BFG1TM61", SELL, FUND, 2670L, null, SENT);
    givenCandidates("IE00BFG1TM61", List.of(order));

    SebPendingTransactionRow row =
        row("Tuleva Täiendav Kogumisfond", "IE00BFG1TM61", "Sell", "2670.0003", "91782.00");

    Optional<QuantityAmountMismatchEvent> nearMiss = matcher().findNearMiss(row);
    assertThat(nearMiss).isPresent();
    QuantityAmountMismatchEvent event = nearMiss.get();
    assertThat(event.kind()).isEqualTo(FUND_SELL_QUANTITY);
    assertThat(event.order()).isEqualTo(order);
  }

  @Test
  void findNearMiss_excludesCancelledOrders() {
    TransactionOrder cancelled =
        orderOf(88L, TUK75, "IE00BFNM3G45", BUY, ETF, 13288L, null, CANCELLED);
    givenCandidates("IE00BFNM3G45", List.of(cancelled));

    SebPendingTransactionRow row =
        row("Tuleva Maailma Aktsiate Pensionifond", "IE00BFNM3G45", "Buy", "13288.0003", null);

    assertThat(matcher().findNearMiss(row)).isEmpty();
  }

  @Test
  void findNearMiss_excludesOrdersAlreadyLinkedToExecution() {
    TransactionOrder order = orderOf(89L, TUK75, "IE00BFNM3G45", BUY, ETF, 13288L, null, SENT);
    givenCandidates("IE00BFNM3G45", List.of(order));
    given(executionRepository.findByOrderId(89L))
        .willReturn(
            Optional.of(TransactionExecution.builder().id(99L).orderId(89L).source("X").build()));

    SebPendingTransactionRow row =
        row("Tuleva Maailma Aktsiate Pensionifond", "IE00BFNM3G45", "Buy", "13288.0003", null);

    assertThat(matcher().findNearMiss(row)).isEmpty();
  }

  @Test
  void findNearMiss_unknownClientName_returnsEmpty() {
    SebPendingTransactionRow row =
        row("Some Other Bank Fund", "IE00BFNM3G45", "Buy", "13288.0003", null);

    assertThat(matcher().findNearMiss(row)).isEmpty();
  }

  @Test
  void hasNearMissCandidate_withinFiveX_returnsTrue() {
    TransactionOrder order = orderOf(91L, TUK75, "IE00BFNM3G45", BUY, ETF, 13288L, null, SENT);
    givenCandidates("IE00BFNM3G45", List.of(order));

    SebPendingTransactionRow row =
        row("Tuleva Maailma Aktsiate Pensionifond", "IE00BFNM3G45", "Buy", "13288.0003", null);

    assertThat(matcher().hasNearMissCandidate(row)).isTrue();
  }

  @Test
  void hasNearMissCandidate_outsideFiveX_returnsFalse() {
    TransactionOrder order = orderOf(92L, TUK75, "IE00BFNM3G45", BUY, ETF, 13288L, null, SENT);
    givenCandidates("IE00BFNM3G45", List.of(order));

    SebPendingTransactionRow row =
        row("Tuleva Maailma Aktsiate Pensionifond", "IE00BFNM3G45", "Buy", "13289", null);

    assertThat(matcher().hasNearMissCandidate(row)).isFalse();
  }

  @Test
  void hasNearMissCandidate_ambiguousMultipleCandidates_returnsTrueEvenThoughFindNearMissIsEmpty() {
    TransactionOrder a = orderOf(93L, TUK75, "IE00BFNM3G45", BUY, ETF, 13288L, null, SENT);
    TransactionOrder b = orderOf(94L, TUK75, "IE00BFNM3G45", BUY, ETF, 13288L, null, SENT);
    givenCandidates("IE00BFNM3G45", List.of(a, b));

    SebPendingTransactionRow row =
        row("Tuleva Maailma Aktsiate Pensionifond", "IE00BFNM3G45", "Buy", "13288.0003", null);

    assertThat(matcher().hasNearMissCandidate(row)).isTrue();
    assertThat(matcher().findNearMiss(row)).isEmpty();
  }

  @Test
  void hasNearMissCandidate_unknownClientName_returnsFalse() {
    SebPendingTransactionRow row =
        row("Some Other Bank Fund", "IE00BFNM3G45", "Buy", "13288.0003", null);

    assertThat(matcher().hasNearMissCandidate(row)).isFalse();
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
        .orderQuantity(quantity == null ? null : BigDecimal.valueOf(quantity))
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
