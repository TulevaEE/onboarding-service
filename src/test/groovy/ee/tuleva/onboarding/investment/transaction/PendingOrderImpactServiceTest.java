package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PendingOrderImpactServiceTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 2, 10);
  private static final LocalDate POSITION_DATE = AS_OF_DATE.minusDays(1);

  @Mock private TransactionOrderRepository orderRepository;
  @Mock private TransactionExecutionRepository executionRepository;
  @Mock private PositionPriceResolver positionPriceResolver;

  @InjectMocks private PendingOrderImpactService service;

  @Test
  void withNoUnsettledOrders_hasNoImpact() {
    given(orderRepository.findUnsettledOrders(TUV100, AS_OF_DATE)).willReturn(List.of());

    var impact = service.calculate(TUV100, AS_OF_DATE, POSITION_DATE);

    assertThat(impact).isEqualTo(PendingOrderImpact.none());
  }

  @Test
  void valuesASentEtfBuyAtTheLatestPriceAndReservesItsCash() {
    given(orderRepository.findUnsettledOrders(TUV100, AS_OF_DATE))
        .willReturn(
            List.of(
                order(
                    1L,
                    "IE00A",
                    TransactionType.BUY,
                    InstrumentType.ETF,
                    OrderStatus.SENT,
                    new BigDecimal("100"),
                    new BigDecimal("4000"))));
    given(executionRepository.findByOrderIdIn(List.of(1L))).willReturn(List.of());
    given(positionPriceResolver.resolve("IE00A", AS_OF_DATE))
        .willReturn(Optional.of(ResolvedPrice.builder().usedPrice(new BigDecimal("50")).build()));

    var impact = service.calculate(TUV100, AS_OF_DATE, POSITION_DATE);

    assertThat(impact.unreportedPositionValues())
        .containsExactly(Map.entry("IE00A", new BigDecimal("5000")));
    assertThat(impact.unreportedPositionQuantities())
        .containsExactly(Map.entry("IE00A", new BigDecimal("100")));
    assertThat(impact.pendingBuys()).isEqualByComparingTo(new BigDecimal("5000"));
    assertThat(impact.pendingSells()).isEqualByComparingTo(ZERO);
  }

  @Test
  void valuesASentFundBuyAtItsOrderAmount() {
    given(orderRepository.findUnsettledOrders(TUV100, AS_OF_DATE))
        .willReturn(
            List.of(
                order(
                    1L,
                    "IE00FUND",
                    TransactionType.BUY,
                    InstrumentType.FUND,
                    OrderStatus.SENT,
                    null,
                    new BigDecimal("53000"))));
    given(executionRepository.findByOrderIdIn(List.of(1L))).willReturn(List.of());

    var impact = service.calculate(TUV100, AS_OF_DATE, POSITION_DATE);

    assertThat(impact.unreportedPositionValues())
        .containsExactly(Map.entry("IE00FUND", new BigDecimal("53000")));
    assertThat(impact.unreportedPositionQuantities()).isEmpty();
    assertThat(impact.pendingBuys()).isEqualByComparingTo(new BigDecimal("53000"));
  }

  @Test
  void recordsASentSellAsANegativePositionValueAndAnIncomingReceipt() {
    given(orderRepository.findUnsettledOrders(TUV100, AS_OF_DATE))
        .willReturn(
            List.of(
                order(
                    1L,
                    "IE00A",
                    TransactionType.SELL,
                    InstrumentType.ETF,
                    OrderStatus.SENT,
                    new BigDecimal("20"),
                    new BigDecimal("1000"))));
    given(executionRepository.findByOrderIdIn(List.of(1L))).willReturn(List.of());
    given(positionPriceResolver.resolve("IE00A", AS_OF_DATE))
        .willReturn(Optional.of(ResolvedPrice.builder().usedPrice(new BigDecimal("50")).build()));

    var impact = service.calculate(TUV100, AS_OF_DATE, POSITION_DATE);

    assertThat(impact.unreportedPositionValues())
        .containsExactly(Map.entry("IE00A", new BigDecimal("-1000")));
    assertThat(impact.unreportedPositionQuantities())
        .containsExactly(Map.entry("IE00A", new BigDecimal("-20")));
    assertThat(impact.pendingSells()).isEqualByComparingTo(new BigDecimal("1000"));
    assertThat(impact.pendingBuys()).isEqualByComparingTo(ZERO);
  }

  @Test
  void aSentOrderTradedBeforeThePositionReportIsAlreadyInItAndIsNotSynthesizedAgain() {
    given(orderRepository.findUnsettledOrders(TUV100, AS_OF_DATE))
        .willReturn(
            List.of(
                order(
                    1L,
                    "IE00A",
                    TransactionType.BUY,
                    InstrumentType.ETF,
                    OrderStatus.SENT,
                    new BigDecimal("100"),
                    new BigDecimal("5000"),
                    POSITION_DATE)));
    given(executionRepository.findByOrderIdIn(List.of(1L))).willReturn(List.of());

    var impact = service.calculate(TUV100, AS_OF_DATE, POSITION_DATE);

    assertThat(impact.unreportedPositionValues()).isEmpty();
    assertThat(impact.unreportedPositionQuantities()).isEmpty();
    assertThat(impact.pendingBuys()).isEqualByComparingTo(new BigDecimal("5000"));
  }

  @Test
  void anExecutedOrderTradedAfterThePositionReportIsSynthesizedLikeAnySentOne() {
    given(orderRepository.findUnsettledOrders(TUV100, AS_OF_DATE))
        .willReturn(
            List.of(
                order(
                    1L,
                    "IE00FUND",
                    TransactionType.BUY,
                    InstrumentType.FUND,
                    OrderStatus.EXECUTED,
                    null,
                    new BigDecimal("300000"),
                    AS_OF_DATE)));
    given(executionRepository.findByOrderIdIn(List.of(1L)))
        .willReturn(
            List.of(
                TransactionExecution.builder()
                    .orderId(1L)
                    .executedQuantity(new BigDecimal("15000"))
                    .totalConsideration(new BigDecimal("300000"))
                    .build()));

    var impact = service.calculate(TUV100, AS_OF_DATE, POSITION_DATE);

    assertThat(impact.unreportedPositionValues())
        .containsExactly(Map.entry("IE00FUND", new BigDecimal("300000")));
    assertThat(impact.pendingBuys()).isEqualByComparingTo(new BigDecimal("300000"));
  }

  @Test
  void anExecutedOrderReservesItsActualConsiderationAndIsLeftToTheSebPositionReport() {
    given(orderRepository.findUnsettledOrders(TUV100, AS_OF_DATE))
        .willReturn(
            List.of(
                order(
                    1L,
                    "IE00A",
                    TransactionType.BUY,
                    InstrumentType.ETF,
                    OrderStatus.EXECUTED,
                    new BigDecimal("100"),
                    new BigDecimal("4000"),
                    POSITION_DATE)));
    given(executionRepository.findByOrderIdIn(List.of(1L)))
        .willReturn(
            List.of(
                TransactionExecution.builder()
                    .orderId(1L)
                    .executedQuantity(new BigDecimal("100"))
                    .totalConsideration(new BigDecimal("4123.45"))
                    .build()));

    var impact = service.calculate(TUV100, AS_OF_DATE, POSITION_DATE);

    assertThat(impact.pendingBuys()).isEqualByComparingTo(new BigDecimal("4123.45"));
    assertThat(impact.unreportedPositionValues()).isEmpty();
    assertThat(impact.unreportedPositionQuantities()).isEmpty();
  }

  @Test
  void sumsPartialExecutionsOfTheSameOrder() {
    given(orderRepository.findUnsettledOrders(TUV100, AS_OF_DATE))
        .willReturn(
            List.of(
                order(
                    1L,
                    "IE00A",
                    TransactionType.BUY,
                    InstrumentType.ETF,
                    OrderStatus.EXECUTED,
                    new BigDecimal("100"),
                    new BigDecimal("4000"))));
    given(executionRepository.findByOrderIdIn(List.of(1L)))
        .willReturn(
            List.of(
                TransactionExecution.builder()
                    .orderId(1L)
                    .executedQuantity(new BigDecimal("60"))
                    .totalConsideration(new BigDecimal("2000"))
                    .build(),
                TransactionExecution.builder()
                    .orderId(1L)
                    .executedQuantity(new BigDecimal("40"))
                    .totalConsideration(new BigDecimal("1500"))
                    .build()));

    var impact = service.calculate(TUV100, AS_OF_DATE, POSITION_DATE);

    assertThat(impact.pendingBuys()).isEqualByComparingTo(new BigDecimal("3500"));
  }

  @Test
  void aPartiallyFilledEtfBuyStillReservesTheUnfilledRemainder() {
    given(orderRepository.findUnsettledOrders(TUV100, AS_OF_DATE))
        .willReturn(
            List.of(
                order(
                    1L,
                    "IE00A",
                    TransactionType.BUY,
                    InstrumentType.ETF,
                    OrderStatus.EXECUTED,
                    new BigDecimal("100"),
                    new BigDecimal("5000"))));
    given(executionRepository.findByOrderIdIn(List.of(1L)))
        .willReturn(
            List.of(
                TransactionExecution.builder()
                    .orderId(1L)
                    .executedQuantity(new BigDecimal("30"))
                    .totalConsideration(new BigDecimal("1500"))
                    .build()));
    given(positionPriceResolver.resolve("IE00A", AS_OF_DATE))
        .willReturn(Optional.of(ResolvedPrice.builder().usedPrice(new BigDecimal("50")).build()));

    var impact = service.calculate(TUV100, AS_OF_DATE, POSITION_DATE);

    assertThat(impact.pendingBuys()).isEqualByComparingTo(new BigDecimal("5000"));
  }

  @Test
  void aPartiallyFilledFundBuyStillReservesTheUnfilledRemainder() {
    given(orderRepository.findUnsettledOrders(TUV100, AS_OF_DATE))
        .willReturn(
            List.of(
                order(
                    1L,
                    "IE00FUND",
                    TransactionType.BUY,
                    InstrumentType.FUND,
                    OrderStatus.EXECUTED,
                    null,
                    new BigDecimal("100000"))));
    given(executionRepository.findByOrderIdIn(List.of(1L)))
        .willReturn(
            List.of(
                TransactionExecution.builder()
                    .orderId(1L)
                    .totalConsideration(new BigDecimal("30000"))
                    .build()));

    var impact = service.calculate(TUV100, AS_OF_DATE, POSITION_DATE);

    assertThat(impact.pendingBuys()).isEqualByComparingTo(new BigDecimal("100000"));
  }

  @Test
  void fallsBackToTheOrderAmountWhenNoPriceIsAvailable() {
    given(orderRepository.findUnsettledOrders(TUV100, AS_OF_DATE))
        .willReturn(
            List.of(
                order(
                    1L,
                    "IE00A",
                    TransactionType.BUY,
                    InstrumentType.ETF,
                    OrderStatus.SENT,
                    new BigDecimal("100"),
                    new BigDecimal("4000"))));
    given(executionRepository.findByOrderIdIn(List.of(1L))).willReturn(List.of());
    lenient().when(positionPriceResolver.resolve(any(), any())).thenReturn(Optional.empty());

    var impact = service.calculate(TUV100, AS_OF_DATE, POSITION_DATE);

    assertThat(impact.unreportedPositionValues())
        .containsExactly(Map.entry("IE00A", new BigDecimal("4000")));
  }

  @Test
  void anEtfOrderPlacedInEurosIsValuedAtItsAmountAndContributesNoQuantity() {
    given(orderRepository.findUnsettledOrders(TUV100, AS_OF_DATE))
        .willReturn(
            List.of(
                order(
                    1L,
                    "IE00A",
                    TransactionType.BUY,
                    InstrumentType.ETF,
                    OrderStatus.SENT,
                    null,
                    new BigDecimal("4000"))));
    given(executionRepository.findByOrderIdIn(List.of(1L))).willReturn(List.of());

    var impact = service.calculate(TUV100, AS_OF_DATE, POSITION_DATE);

    assertThat(impact.unreportedPositionValues())
        .containsExactly(Map.entry("IE00A", new BigDecimal("4000")));
    assertThat(impact.unreportedPositionQuantities()).isEmpty();
  }

  @Test
  void anExecutionWithoutAConsiderationDoesNotDisplaceTheEstimate() {
    given(orderRepository.findUnsettledOrders(TUV100, AS_OF_DATE))
        .willReturn(
            List.of(
                order(
                    1L,
                    "IE00FUND",
                    TransactionType.BUY,
                    InstrumentType.FUND,
                    OrderStatus.SENT,
                    null,
                    new BigDecimal("7000"))));
    given(executionRepository.findByOrderIdIn(List.of(1L)))
        .willReturn(List.of(TransactionExecution.builder().orderId(1L).build()));

    var impact = service.calculate(TUV100, AS_OF_DATE, POSITION_DATE);

    assertThat(impact.pendingBuys()).isEqualByComparingTo(new BigDecimal("7000"));
  }

  // A zero consideration is a placeholder, not a free trade — the cash still has to be reserved.
  @Test
  void aZeroConsiderationFallsBackToTheEstimatedValue() {
    given(orderRepository.findUnsettledOrders(TUV100, AS_OF_DATE))
        .willReturn(
            List.of(
                order(
                    1L,
                    "IE00FUND",
                    TransactionType.BUY,
                    InstrumentType.FUND,
                    OrderStatus.SENT,
                    null,
                    new BigDecimal("7000"))));
    given(executionRepository.findByOrderIdIn(List.of(1L)))
        .willReturn(
            List.of(TransactionExecution.builder().orderId(1L).totalConsideration(ZERO).build()));

    var impact = service.calculate(TUV100, AS_OF_DATE, POSITION_DATE);

    assertThat(impact.pendingBuys()).isEqualByComparingTo(new BigDecimal("7000"));
  }

  @Test
  void anOrderWithNeitherQuantityNorAmountReservesNothing() {
    given(orderRepository.findUnsettledOrders(TUV100, AS_OF_DATE))
        .willReturn(
            List.of(
                order(
                    1L,
                    "IE00FUND",
                    TransactionType.BUY,
                    InstrumentType.FUND,
                    OrderStatus.SENT,
                    null,
                    null)));
    given(executionRepository.findByOrderIdIn(List.of(1L))).willReturn(List.of());

    var impact = service.calculate(TUV100, AS_OF_DATE, POSITION_DATE);

    assertThat(impact.pendingBuys()).isEqualByComparingTo(ZERO);
    assertThat(impact.unreportedPositionValues()).containsExactly(Map.entry("IE00FUND", ZERO));
  }

  // A resolver that answers zero has not priced the instrument; the order amount is the better
  // estimate.
  @Test
  void aNonPositiveResolvedPriceIsNotUsedForValuation() {
    given(orderRepository.findUnsettledOrders(TUV100, AS_OF_DATE))
        .willReturn(
            List.of(
                order(
                    1L,
                    "IE00A",
                    TransactionType.BUY,
                    InstrumentType.ETF,
                    OrderStatus.SENT,
                    new BigDecimal("100"),
                    new BigDecimal("4000"))));
    given(executionRepository.findByOrderIdIn(List.of(1L))).willReturn(List.of());
    given(positionPriceResolver.resolve("IE00A", AS_OF_DATE))
        .willReturn(Optional.of(ResolvedPrice.builder().usedPrice(ZERO).build()));

    var impact = service.calculate(TUV100, AS_OF_DATE, POSITION_DATE);

    assertThat(impact.unreportedPositionValues())
        .containsExactly(Map.entry("IE00A", new BigDecimal("4000")));
  }

  private TransactionOrder order(
      Long id,
      String isin,
      TransactionType type,
      InstrumentType instrumentType,
      OrderStatus status,
      BigDecimal quantity,
      BigDecimal amount) {
    return order(id, isin, type, instrumentType, status, quantity, amount, AS_OF_DATE);
  }

  private TransactionOrder order(
      Long id,
      String isin,
      TransactionType type,
      InstrumentType instrumentType,
      OrderStatus status,
      BigDecimal quantity,
      BigDecimal amount,
      LocalDate tradeDate) {
    return TransactionOrder.builder()
        .id(id)
        .fund(TUV100)
        .instrumentIsin(isin)
        .transactionType(type)
        .instrumentType(instrumentType)
        .orderStatus(status)
        .orderQuantity(quantity)
        .orderAmount(amount)
        .orderVenue(OrderVenue.SEB)
        .orderTimestamp(tradeDate.atTime(16, 30).atZone(TALLINN).toInstant())
        .expectedSettlementDate(AS_OF_DATE.plusDays(2))
        .build();
  }
}
