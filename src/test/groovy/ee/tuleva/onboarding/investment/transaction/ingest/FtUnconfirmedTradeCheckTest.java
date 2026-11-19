package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.FtConfirmationType.CANCELLATION;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.CONFIRMED;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.ERROR;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.OK;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.PRICE_MISSING;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.UNCHECKED;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.UNCONFIRMED;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.CANCELLED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.comparisons.fundvalue.ValidationStatus;
import ee.tuleva.onboarding.investment.calendar.Target2Calendar;
import ee.tuleva.onboarding.investment.transaction.FtConfirmation;
import ee.tuleva.onboarding.investment.transaction.FtConfirmationResult;
import ee.tuleva.onboarding.investment.transaction.FtConfirmationType;
import ee.tuleva.onboarding.investment.transaction.OrderVenue;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FtUnconfirmedTradeCheckTest {

  private static final String ISIN = "IE000F60HVH9";
  private static final LocalDate TRADE_DATE = LocalDate.of(2026, 6, 8);
  private static final LocalDate ALERTS_FROM = LocalDate.of(2026, 6, 1);
  private static final Instant SINCE_ALERTS_FROM = Instant.parse("2026-06-01T00:00:00Z");
  private static final Instant END_OF_THE_NEXT_BUSINESS_DAY = Instant.parse("2026-06-09T20:00:00Z");
  private static final Instant TWO_DAYS_AFTER_TRADE = Instant.parse("2026-06-10T08:00:00Z");
  private static final String ACTOR = "ft-confirmation-import-job";
  private static final int LOOKBACK_DAYS = 14;

  @Mock private TransactionOrderRepository orderRepository;
  @Mock private TransactionExecutionRepository executionRepository;
  @Mock private PositionPriceResolver positionPriceResolver;
  @Mock private FtConfirmationAuditRecorder auditRecorder;
  @Mock private FtConfirmationDigest digest;

  private long nextOrderId = 1;

  private FtUnconfirmedTradeCheck check(Instant now) {
    return check(now, LOOKBACK_DAYS);
  }

  private FtUnconfirmedTradeCheck check(Instant now, int lookbackDays) {
    Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    return new FtUnconfirmedTradeCheck(
        orderRepository,
        executionRepository,
        new FtCancellationCheck(new Target2Calendar(), auditRecorder, clock),
        new FtSebExecutionCheck(
            new FtPriceCheck(
                positionPriceResolver,
                new PriceValidator(),
                new Target2Calendar(),
                clock,
                ALERTS_FROM),
            new Target2Calendar(),
            clock),
        auditRecorder,
        digest,
        clock,
        ALERTS_FROM,
        lookbackDays);
  }

  @Test
  void tradeSebExecutedWithNoFtConfirmationAfterTheNextBusinessDay_isUnconfirmedWithItsPriceOk() {
    TransactionOrder order = order("1000");
    givenOrders(order);
    givenExecutions(execution(order, "1000", "20.40"));
    givenClosingPrice("20.40");

    check(TWO_DAYS_AFTER_TRADE).check(List.of(), ACTOR);

    FtExecutedTrade unconfirmed =
        trade(
            order,
            "1000",
            "20.4",
            new FtConfirmationResult(
                UNCONFIRMED,
                OK,
                Map.of("confirmationDueBy", "2026-06-09", "referencePrice", "20.40")));
    verify(auditRecorder).recordOutcome(order, unconfirmed, ACTOR);
    verify(digest).publish(List.of(unconfirmed));
  }

  @Test
  void tradeWithoutAnFtConfirmationUntilTheEndOfTheNextBusinessDay_isNotCheckedYet() {
    givenOrders(order("1000"));

    check(END_OF_THE_NEXT_BUSINESS_DAY).check(List.of(), ACTOR);

    verify(digest).publish(List.of());
    verifyNoInteractions(auditRecorder, positionPriceResolver, executionRepository);
  }

  @Test
  void tradeFtConfirmedForItsQuantity_isConfirmed_withoutCheckingTheSebPrice() {
    TransactionOrder order = order("1000");
    givenOrders(order);
    givenExecutions(execution(order, "1000", "20.40"));

    check(TWO_DAYS_AFTER_TRADE).check(List.of(confirmation("1000", "20.40")), ACTOR);

    FtExecutedTrade confirmed =
        trade(order, "1000", "20.4", new FtConfirmationResult(CONFIRMED, CONFIRMED, Map.of()));
    verify(auditRecorder).recordOutcome(order, confirmed, ACTOR);
    verify(digest).publish(List.of(confirmed));
    verifyNoInteractions(positionPriceResolver);
  }

  @Test
  void tradeWithinOneUnitOfItsFtConfirmation_isConfirmed() {
    TransactionOrder order = order("1000");
    givenOrders(order);
    givenExecutions(execution(order, "1000", "20.40"));

    check(TWO_DAYS_AFTER_TRADE).check(List.of(confirmation("999", "20.40")), ACTOR);

    verify(digest)
        .publish(
            List.of(
                trade(
                    order,
                    "1000",
                    "20.4",
                    new FtConfirmationResult(CONFIRMED, CONFIRMED, Map.of()))));
  }

  @Test
  void onlyTradeThatDay_isConfirmedByAnFtConfirmationOfAnotherQuantity_whichItsOwnCheckFlags() {
    TransactionOrder order = order("1000");
    givenOrders(order);
    givenExecutions(execution(order, "1000", "20.40"));

    check(TWO_DAYS_AFTER_TRADE).check(List.of(confirmation("900", "20.40")), ACTOR);

    verify(digest)
        .publish(
            List.of(
                trade(
                    order,
                    "1000",
                    "20.4",
                    new FtConfirmationResult(CONFIRMED, CONFIRMED, Map.of()))));
  }

  @Test
  void twoTradesThatDay_onlyTheOneWithoutAnFtConfirmationOfItsQuantityIsUnconfirmed() {
    TransactionOrder confirmedOrder = order("1000");
    TransactionOrder unconfirmedOrder = order("250");
    givenOrders(confirmedOrder, unconfirmedOrder);
    givenExecutions(
        execution(confirmedOrder, "1000", "20.40"), execution(unconfirmedOrder, "250", "20.40"));
    givenClosingPrice("20.40");

    check(TWO_DAYS_AFTER_TRADE).check(List.of(confirmation("1000", "20.40")), ACTOR);

    verify(digest)
        .publish(
            List.of(
                trade(
                    confirmedOrder,
                    "1000",
                    "20.4",
                    new FtConfirmationResult(CONFIRMED, CONFIRMED, Map.of())),
                trade(
                    unconfirmedOrder,
                    "250",
                    "20.4",
                    new FtConfirmationResult(
                        UNCONFIRMED,
                        OK,
                        Map.of("confirmationDueBy", "2026-06-09", "referencePrice", "20.40")))));
  }

  @Test
  void anFtConfirmationOfAnotherFundIsinOrDay_doesNotConfirmTheTrade() {
    TransactionOrder order = order("1000");
    givenOrders(order);
    givenExecutions(execution(order, "1000", "20.40"));
    givenClosingPrice("20.40");

    check(TWO_DAYS_AFTER_TRADE)
        .check(
            List.of(
                new FtConfirmation(
                    TUK75,
                    "IE00BFG1TM61",
                    TRADE_DATE,
                    new BigDecimal("1000"),
                    new BigDecimal("20.40")),
                new FtConfirmation(
                    TUK75,
                    ISIN,
                    TRADE_DATE.minusDays(1),
                    new BigDecimal("1000"),
                    new BigDecimal("20.40"))),
            ACTOR);

    verify(digest)
        .publish(
            List.of(
                trade(
                    order,
                    "1000",
                    "20.4",
                    new FtConfirmationResult(
                        UNCONFIRMED,
                        OK,
                        Map.of("confirmationDueBy", "2026-06-09", "referencePrice", "20.40")))));
  }

  @Test
  void ftConfirmationThatFtCancelledWithoutANewOne_doesNotConfirmTheTrade() {
    TransactionOrder order = order("1000");
    givenOrders(order);
    givenExecutions(execution(order, "1000", "20.40"));
    givenClosingPrice("20.40");
    givenCancellationFirstSeenOn(TRADE_DATE);

    check(TWO_DAYS_AFTER_TRADE)
        .check(
            List.of(confirmation("1000", "20.40"), confirmation("1000", "20.40", CANCELLATION)),
            ACTOR);

    verify(digest)
        .publish(
            List.of(
                trade(
                    order,
                    "1000",
                    "20.4",
                    new FtConfirmationResult(
                        UNCONFIRMED,
                        OK,
                        Map.of("confirmationDueBy", "2026-06-09", "referencePrice", "20.40")))));
  }

  @Test
  void ftConfirmationCancelledDaysLater_stillConfirmsTheTradeWhileFtMaySendItsReplacement() {
    TransactionOrder order = order("1000");
    givenOrders(order);
    givenExecutions(execution(order, "1000", "20.40"));
    givenCancellationFirstSeenOn(LocalDate.of(2026, 6, 10));

    check(TWO_DAYS_AFTER_TRADE)
        .check(
            List.of(confirmation("1000", "20.40"), confirmation("1000", "20.40", CANCELLATION)),
            ACTOR);

    verify(digest)
        .publish(
            List.of(
                trade(
                    order,
                    "1000",
                    "20.4",
                    new FtConfirmationResult(CONFIRMED, CONFIRMED, Map.of()))));
  }

  @Test
  void ftConfirmationAndItsWaitingCancellation_confirmOnlyOneOfTwoTradesOfThatQuantity() {
    TransactionOrder first = order("1000");
    TransactionOrder second = order("1000");
    givenOrders(first, second);
    givenExecutions(execution(first, "1000", "20.40"), execution(second, "1000", "20.40"));
    givenClosingPrice("20.40");
    givenCancellationFirstSeenOn(LocalDate.of(2026, 6, 10));

    check(TWO_DAYS_AFTER_TRADE)
        .check(
            List.of(confirmation("1000", "20.40"), confirmation("1000", "20.40", CANCELLATION)),
            ACTOR);

    verify(digest)
        .publish(
            List.of(
                trade(
                    first,
                    "1000",
                    "20.4",
                    new FtConfirmationResult(CONFIRMED, CONFIRMED, Map.of())),
                trade(
                    second,
                    "1000",
                    "20.4",
                    new FtConfirmationResult(
                        UNCONFIRMED,
                        OK,
                        Map.of("confirmationDueBy", "2026-06-09", "referencePrice", "20.40")))));
  }

  @Test
  void ftConfirmationCancelledAndSentAgain_confirmsTheTrade() {
    TransactionOrder order = order("1000");
    givenOrders(order);
    givenExecutions(execution(order, "1000", "20.40"));
    givenCancellationFirstSeenOn(TRADE_DATE);

    check(TWO_DAYS_AFTER_TRADE)
        .check(
            List.of(
                confirmation("1000", "20.39"),
                confirmation("1000", "20.39", CANCELLATION),
                confirmation("1000", "20.40")),
            ACTOR);

    verify(digest)
        .publish(
            List.of(
                trade(
                    order,
                    "1000",
                    "20.4",
                    new FtConfirmationResult(CONFIRMED, CONFIRMED, Map.of()))));
  }

  @Test
  void sebPricedTheTradeOffTheClose_isUnconfirmedWithTheDeviation() {
    TransactionOrder order = order("1000");
    givenOrders(order);
    givenExecutions(execution(order, "1000", "20.50"));
    givenClosingPrice("20.40");

    check(TWO_DAYS_AFTER_TRADE).check(List.of(), ACTOR);

    verify(digest)
        .publish(
            List.of(
                trade(
                    order,
                    "1000",
                    "20.5",
                    new FtConfirmationResult(
                        UNCONFIRMED,
                        ERROR,
                        Map.of(
                            "confirmationDueBy",
                            "2026-06-09",
                            "referencePrice",
                            "20.40",
                            "priceDeltaPercent",
                            "0.4901960784313725")))));
  }

  @Test
  void noClosingPriceForTheTradeDate_isUnconfirmedWithThePriceMissing() {
    TransactionOrder order = order("1000");
    givenOrders(order);
    givenExecutions(execution(order, "1000", "20.40"));

    check(TWO_DAYS_AFTER_TRADE).check(List.of(), ACTOR);

    verify(digest)
        .publish(
            List.of(
                trade(
                    order,
                    "1000",
                    "20.4",
                    new FtConfirmationResult(
                        UNCONFIRMED,
                        PRICE_MISSING,
                        Map.of(
                            "confirmationDueBy",
                            "2026-06-09",
                            "closingPriceDueBy",
                            "2026-06-09")))));
  }

  @Test
  void tradeFilledInPieces_isCheckedAtTheSebAveragePrice() {
    TransactionOrder order = order("1000");
    givenOrders(order);
    givenExecutions(execution(order, "600", "20.30"), execution(order, "400", "20.55"));
    givenClosingPrice("20.40");

    check(TWO_DAYS_AFTER_TRADE).check(List.of(), ACTOR);

    verify(digest)
        .publish(
            List.of(
                trade(
                    order,
                    "1000",
                    "20.4",
                    new FtConfirmationResult(
                        UNCONFIRMED,
                        OK,
                        Map.of("confirmationDueBy", "2026-06-09", "referencePrice", "20.40")))));
  }

  @Test
  void confirmationOfAnOrderCancelledInTheRegistry_doesNotConfirmTheDaysOtherTrade() {
    TransactionOrder cancelled = order("1000");
    cancelled.setOrderStatus(CANCELLED);
    TransactionOrder live = order("250");
    givenOrders(cancelled, live);
    givenExecutions(execution(live, "250", "20.40"));
    givenClosingPrice("20.40");

    check(TWO_DAYS_AFTER_TRADE).check(List.of(confirmation("1000", "20.40")), ACTOR);

    verify(digest)
        .publish(
            List.of(
                trade(
                    live,
                    "250",
                    "20.4",
                    new FtConfirmationResult(
                        UNCONFIRMED,
                        OK,
                        Map.of("confirmationDueBy", "2026-06-09", "referencePrice", "20.40")))));
  }

  @Test
  void orderCancelledInTheRegistry_doesNotStopTheDaysOnlyLiveTradeBeingConfirmed() {
    TransactionOrder cancelled = order("1000");
    cancelled.setOrderStatus(CANCELLED);
    TransactionOrder live = order("250");
    givenOrders(cancelled, live);
    givenExecutions(execution(live, "250", "20.40"));

    check(TWO_DAYS_AFTER_TRADE).check(List.of(confirmation("249", "20.40")), ACTOR);

    verify(digest)
        .publish(
            List.of(
                trade(
                    live,
                    "250",
                    "20.4",
                    new FtConfirmationResult(CONFIRMED, CONFIRMED, Map.of()))));
  }

  @Test
  void sebExecutionWithoutAPrice_leavesTheTradeUnconfirmedWithThePriceUnchecked() {
    TransactionOrder order = order("1000");
    givenOrders(order);
    TransactionExecution withoutPrice = execution(order, "400", "20.40");
    withoutPrice.setUnitPrice(null);
    givenExecutions(execution(order, "600", "20.40"), withoutPrice);

    check(TWO_DAYS_AFTER_TRADE).check(List.of(), ACTOR);

    verify(digest)
        .publish(
            List.of(
                new FtExecutedTrade(
                    TUK75,
                    ISIN,
                    TRADE_DATE,
                    order.getOrderUuid(),
                    new BigDecimal("1000"),
                    null,
                    new FtConfirmationResult(
                        UNCONFIRMED,
                        UNCHECKED,
                        Map.of(
                            "confirmationDueBy",
                            "2026-06-09",
                            "verificationError",
                            "SEB reported no price for the trade")))));
    verifyNoInteractions(positionPriceResolver);
  }

  @Test
  void sebRowsWithoutAQuantity_areLeftOutOfTheTrade() {
    TransactionOrder order = order("1000");
    givenOrders(order);
    TransactionExecution withoutQuantity = execution(order, "500", "19.00");
    withoutQuantity.setExecutedQuantity(null);
    givenExecutions(
        execution(order, "1000", "20.40"), withoutQuantity, execution(order, "0", "19.00"));
    givenClosingPrice("20.40");

    check(TWO_DAYS_AFTER_TRADE).check(List.of(), ACTOR);

    verify(digest)
        .publish(
            List.of(
                trade(
                    order,
                    "1000",
                    "20.4",
                    new FtConfirmationResult(
                        UNCONFIRMED,
                        OK,
                        Map.of("confirmationDueBy", "2026-06-09", "referencePrice", "20.40")))));
  }

  @Test
  void lookbackStartingAfterAlertsFrom_isWhereTheCheckStarts() {
    TransactionOrder order = order("1000");
    given(
            orderRepository.findByOrderVenueAndOrderTimestampGreaterThanEqual(
                OrderVenue.FT, Instant.parse("2026-06-07T00:00:00Z")))
        .willReturn(List.of(order));
    givenExecutions(execution(order, "1000", "20.40"));
    givenClosingPrice("20.40");

    check(TWO_DAYS_AFTER_TRADE, 3).check(List.of(), ACTOR);

    verify(digest)
        .publish(
            List.of(
                trade(
                    order,
                    "1000",
                    "20.4",
                    new FtConfirmationResult(
                        UNCONFIRMED,
                        OK,
                        Map.of("confirmationDueBy", "2026-06-09", "referencePrice", "20.40")))));
  }

  @Test
  void ftCorrectionsOfTradesBeforeTheCheckStarts_areNotLookedUp() {
    givenOrders();
    LocalDate beforeAlertsFrom = LocalDate.of(2026, 5, 20);

    check(TWO_DAYS_AFTER_TRADE)
        .check(
            List.of(
                new FtConfirmation(
                    TUK75, ISIN, beforeAlertsFrom, new BigDecimal("1000"), new BigDecimal("20.40")),
                new FtConfirmation(
                    TUK75,
                    ISIN,
                    beforeAlertsFrom,
                    new BigDecimal("1000"),
                    new BigDecimal("20.40"),
                    CANCELLATION,
                    null)),
            ACTOR);

    verify(auditRecorder, never()).firstSeenOn(any());
    verify(digest).publish(List.of());
  }

  @Test
  void auditTrailFailingForOneTrade_stillAlertsItAndChecksTheRest() {
    TransactionOrder failing = order("1000");
    TransactionOrder other = order("250");
    givenOrders(failing, other);
    givenExecutions(execution(failing, "1000", "20.40"), execution(other, "250", "20.40"));
    givenClosingPrice("20.40");
    FtConfirmationResult unconfirmed =
        new FtConfirmationResult(
            UNCONFIRMED, OK, Map.of("confirmationDueBy", "2026-06-09", "referencePrice", "20.40"));
    FtExecutedTrade failingTrade = trade(failing, "1000", "20.4", unconfirmed);
    FtExecutedTrade otherTrade = trade(other, "250", "20.4", unconfirmed);
    willThrow(new IllegalStateException("audit trail down"))
        .given(auditRecorder)
        .recordOutcome(failing, failingTrade, ACTOR);

    check(TWO_DAYS_AFTER_TRADE).check(List.of(), ACTOR);

    verify(auditRecorder).recordOutcome(other, otherTrade, ACTOR);
    verify(digest).publish(List.of(failingTrade, otherTrade));
  }

  @Test
  void orderSebHasNotExecuted_isNotChecked() {
    givenOrders(order("1000"));
    givenExecutions();

    check(TWO_DAYS_AFTER_TRADE).check(List.of(), ACTOR);

    verify(digest).publish(List.of());
    verifyNoInteractions(auditRecorder);
  }

  @Test
  void priceThatFailsToResolve_leavesTheTradeUnconfirmedWithThePriceUnchecked_andChecksTheRest() {
    TransactionOrder failing = order("1000");
    TransactionOrder other = order("250");
    other.setInstrumentIsin("IE00BFG1TM61");
    givenOrders(failing, other);
    givenExecutions(execution(failing, "1000", "20.40"), execution(other, "250", "20.40"));
    given(positionPriceResolver.resolve(ISIN, TRADE_DATE))
        .willThrow(new IllegalStateException("price source down"));
    given(positionPriceResolver.resolve("IE00BFG1TM61", TRADE_DATE))
        .willReturn(Optional.of(closingPrice("20.40")));

    check(TWO_DAYS_AFTER_TRADE).check(List.of(), ACTOR);

    verify(digest)
        .publish(
            List.of(
                trade(
                    failing,
                    "1000",
                    "20.4",
                    new FtConfirmationResult(
                        UNCONFIRMED,
                        UNCHECKED,
                        Map.of(
                            "confirmationDueBy",
                            "2026-06-09",
                            "verificationError",
                            "price source down"))),
                new FtExecutedTrade(
                    TUK75,
                    "IE00BFG1TM61",
                    TRADE_DATE,
                    other.getOrderUuid(),
                    new BigDecimal("250"),
                    new BigDecimal("20.4"),
                    new FtConfirmationResult(
                        UNCONFIRMED,
                        OK,
                        Map.of("confirmationDueBy", "2026-06-09", "referencePrice", "20.40")))));
  }

  private void givenOrders(TransactionOrder... orders) {
    given(
            orderRepository.findByOrderVenueAndOrderTimestampGreaterThanEqual(
                OrderVenue.FT, SINCE_ALERTS_FROM))
        .willReturn(Arrays.asList(orders));
  }

  private void givenCancellationFirstSeenOn(LocalDate firstSeenOn) {
    given(auditRecorder.firstSeenOn(any())).willReturn(firstSeenOn);
  }

  private void givenExecutions(TransactionExecution... executions) {
    given(executionRepository.findByOrderIdIn(any())).willReturn(Arrays.asList(executions));
  }

  private void givenClosingPrice(String price) {
    given(positionPriceResolver.resolve(ISIN, TRADE_DATE))
        .willReturn(Optional.of(closingPrice(price)));
  }

  private static ResolvedPrice closingPrice(String price) {
    return ResolvedPrice.builder()
        .usedPrice(new BigDecimal(price))
        .priceDate(TRADE_DATE)
        .validationStatus(ValidationStatus.OK)
        .build();
  }

  private TransactionOrder order(String quantity) {
    return TransactionOrder.builder()
        .id(nextOrderId++)
        .fund(TUK75)
        .instrumentIsin(ISIN)
        .transactionType(BUY)
        .instrumentType(ETF)
        .orderQuantity(new BigDecimal(quantity))
        .orderVenue(OrderVenue.FT)
        .orderStatus(EXECUTED)
        .orderUuid(UUID.randomUUID())
        .orderTimestamp(Instant.parse("2026-06-08T09:00:00Z"))
        .build();
  }

  private static TransactionExecution execution(
      TransactionOrder order, String quantity, String price) {
    return TransactionExecution.builder()
        .orderId(order.getId())
        .executedQuantity(new BigDecimal(quantity))
        .unitPrice(new BigDecimal(price))
        .source("SEB_OOTEL")
        .build();
  }

  private static FtConfirmation confirmation(String quantity, String price) {
    return confirmation(quantity, price, FtConfirmationType.NORMAL);
  }

  private static FtConfirmation confirmation(
      String quantity, String price, FtConfirmationType type) {
    return new FtConfirmation(
        TUK75, ISIN, TRADE_DATE, new BigDecimal(quantity), new BigDecimal(price), type, null);
  }

  private static FtExecutedTrade trade(
      TransactionOrder order,
      String executedQuantity,
      String sebPrice,
      FtConfirmationResult result) {
    return new FtExecutedTrade(
        TUK75,
        ISIN,
        TRADE_DATE,
        order.getOrderUuid(),
        new BigDecimal(executedQuantity),
        new BigDecimal(sebPrice),
        result);
  }
}
