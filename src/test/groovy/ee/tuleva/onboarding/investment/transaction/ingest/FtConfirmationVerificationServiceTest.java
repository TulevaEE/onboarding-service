package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.FtConfirmationType.CANCELLATION;
import static ee.tuleva.onboarding.investment.transaction.FtConfirmationType.NORMAL;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.AMBIGUOUS;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.CANCELLED;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.ERROR;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.IGNORED;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.NOTHING_TO_CANCEL;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.OK;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.ORPHAN;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.PENDING_EXECUTION;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.PENDING_NAV;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.PENDING_REPLACEMENT;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.REPLACED;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.UNCHECKED;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.VOIDED;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.comparisons.fundvalue.PriceSource;
import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.comparisons.fundvalue.ValidationStatus;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.calendar.EstonianCalendar;
import ee.tuleva.onboarding.investment.calendar.Target2Calendar;
import ee.tuleva.onboarding.investment.transaction.FtConfirmation;
import ee.tuleva.onboarding.investment.transaction.FtConfirmationBatchResult;
import ee.tuleva.onboarding.investment.transaction.FtConfirmationResult;
import ee.tuleva.onboarding.investment.transaction.OrderStatus;
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
class FtConfirmationVerificationServiceTest {

  private static final String ISIN = "IE000F60HVH9";
  private static final LocalDate TRADE_DATE = LocalDate.of(2026, 6, 8); // Monday
  private static final Instant DAY_AFTER_TRADE = Instant.parse("2026-06-09T08:00:00Z");
  private static final Instant TWO_DAYS_AFTER_TRADE = Instant.parse("2026-06-10T08:00:00Z");
  private static final Instant THREE_DAYS_AFTER_TRADE = Instant.parse("2026-06-11T08:00:00Z");
  private static final LocalDate DAY_AFTER_TRADE_DATE = LocalDate.of(2026, 6, 9);
  private static final UUID ORDER_UUID = UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29");

  @Mock private TransactionOrderRepository orderRepository;
  @Mock private TransactionExecutionRepository executionRepository;
  @Mock private PositionPriceResolver positionPriceResolver;
  @Mock private FtConfirmationAuditRecorder auditRecorder;
  @Mock private ReconciliationAuditRecorder reconciliationRecorder;
  @Mock private FtConfirmationDigest digest;

  private FtConfirmationVerificationService service(Instant now) {
    Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    return new FtConfirmationVerificationService(
        orderRepository,
        new FtPriceCheck(
            positionPriceResolver, new PriceValidator(), new Target2Calendar(), clock, TRADE_DATE),
        new FtQuantityCheck(
            executionRepository,
            new FtExecutionWait(
                reconciliationRecorder, new EstonianCalendar(new PublicHolidays()), clock)),
        auditRecorder,
        new FtCancellationCheck(new Target2Calendar(), auditRecorder, clock),
        digest);
  }

  @Test
  void orderNotFound_returnsOrphan() {
    given(orderRepository.findByInstrumentIsin(ISIN)).willReturn(List.of());
    FtConfirmation confirmation = confirmation();

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(confirmation);

    assertThat(result.quantityStatus()).isEqualTo(ORPHAN);
    assertThat(result.priceStatus()).isEqualTo(ORPHAN);
    verify(auditRecorder)
        .recordOutcome(null, new FtConfirmationOutcome(confirmation, result), "admin");
  }

  @Test
  void orderForDifferentFund_returnsOrphan() {
    TransactionOrder order = order(new BigDecimal("40434"));
    order.setFund(TUK00);
    given(orderRepository.findByInstrumentIsin(ISIN)).willReturn(List.of(order));

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(confirmation());

    assertThat(result.quantityStatus()).isEqualTo(ORPHAN);
  }

  @Test
  void confirmationForAnOrderCancelledInTheRegistry_isAnOrphan() {
    TransactionOrder order = order(new BigDecimal("40434"));
    order.setOrderStatus(OrderStatus.CANCELLED);
    given(orderRepository.findByInstrumentIsin(ISIN)).willReturn(List.of(order));

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(confirmation());

    assertThat(result.quantityStatus()).isEqualTo(ORPHAN);
    assertThat(result.priceStatus()).isEqualTo(ORPHAN);
  }

  @Test
  void confirmationMatchingALiveOrderAndOneCancelledInTheRegistry_isCheckedAgainstTheLiveOne() {
    TransactionOrder cancelledOrder = order(10L, new BigDecimal("40434"));
    cancelledOrder.setOrderStatus(OrderStatus.CANCELLED);
    TransactionOrder liveOrder = order(42L, new BigDecimal("40434"));
    given(orderRepository.findByInstrumentIsin(ISIN))
        .willReturn(List.of(cancelledOrder, liveOrder));
    given(executionRepository.findAllByOrderId(42L))
        .willReturn(List.of(execution(new BigDecimal("40434"))));
    givenReferencePrice(new BigDecimal("10.09"), TRADE_DATE);

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(confirmation());

    assertThat(result.quantityStatus()).isEqualTo(OK);
    assertThat(result.priceStatus()).isEqualTo(OK);
    assertThat(result.details()).containsEntry("orderUuid", ORDER_UUID.toString());
  }

  @Test
  void orderForDifferentTradeDate_returnsOrphan() {
    TransactionOrder order = order(new BigDecimal("40434"));
    order.setOrderTimestamp(Instant.parse("2026-06-05T09:30:00Z"));
    given(orderRepository.findByInstrumentIsin(ISIN)).willReturn(List.of(order));

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(confirmation());

    assertThat(result.quantityStatus()).isEqualTo(ORPHAN);
  }

  @Test
  void orderOnDifferentVenue_isNotAnFtCandidate_returnsOrphan() {
    TransactionOrder sebOrder = order(new BigDecimal("40434"));
    sebOrder.setOrderVenue(OrderVenue.SEB);
    given(orderRepository.findByInstrumentIsin(ISIN)).willReturn(List.of(sebOrder));

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(confirmation());

    assertThat(result.quantityStatus()).isEqualTo(ORPHAN);
  }

  @Test
  void orderPlacedLateEveningUtc_matchesSameUtcTradeDate_notNextDayInTallinn() {
    TransactionOrder order = order(new BigDecimal("40434"));
    order.setOrderTimestamp(Instant.parse("2026-06-08T22:30:00Z")); // 01:30 on 06-09 in Tallinn
    given(orderRepository.findByInstrumentIsin(ISIN)).willReturn(List.of(order));
    given(executionRepository.findAllByOrderId(order.getId()))
        .willReturn(List.of(execution(new BigDecimal("40434"))));
    givenReferencePrice(new BigDecimal("10.09"), TRADE_DATE);

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(confirmation());

    assertThat(result.quantityStatus()).isEqualTo(OK);
  }

  @Test
  void quantityAndPriceMatch_returnsOkOk() {
    givenOrderAndExecution(new BigDecimal("40434"), new BigDecimal("40434"));
    givenReferencePrice(new BigDecimal("10.09"), TRADE_DATE);

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(confirmation());

    assertThat(result.quantityStatus()).isEqualTo(OK);
    assertThat(result.priceStatus()).isEqualTo(OK);
    assertThat(result.details())
        .containsEntry("orderUuid", ORDER_UUID.toString())
        .containsEntry("orderQuantity", "40434")
        .containsEntry("executedQuantity", "40434")
        .containsEntry("referencePrice", "10.09");
  }

  @Test
  void quantityWithinOneUnit_returnsOk() {
    givenOrderAndExecution(new BigDecimal("40433"), new BigDecimal("40435"));
    givenReferencePrice(new BigDecimal("10.09"), TRADE_DATE);

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(confirmation());

    assertThat(result.quantityStatus()).isEqualTo(OK);
  }

  @Test
  void splitExecution_sumsExecutedQuantityAcrossPieces_returnsOk() {
    // Order filled in two SEB pieces (20217 + 20217 = 40434): the executed quantity is the sum of
    // every piece, not just the first one.
    TransactionOrder order = order(new BigDecimal("40434"));
    given(orderRepository.findByInstrumentIsin(ISIN)).willReturn(List.of(order));
    given(executionRepository.findAllByOrderId(order.getId()))
        .willReturn(
            List.of(
                executionPiece(7L, new BigDecimal("20217")),
                executionPiece(8L, new BigDecimal("20217"))));
    givenReferencePrice(new BigDecimal("10.09"), TRADE_DATE);

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(confirmation());

    assertThat(result.quantityStatus()).isEqualTo(OK);
    assertThat(result.details()).containsEntry("executedQuantity", "40434");
  }

  @Test
  void orderQuantityDiffersByMoreThanOneUnit_returnsErrorNamingTheOrderQuantity() {
    givenOrderAndExecution(new BigDecimal("40432"), new BigDecimal("40434"));
    givenReferencePrice(new BigDecimal("10.09"), TRADE_DATE);

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(confirmation());

    assertThat(result.quantityStatus()).isEqualTo(ERROR);
    assertThat(result.details())
        .containsEntry("orderQuantityMismatch", "40432")
        .doesNotContainKey("executedQuantityMismatch");
  }

  @Test
  void executedQuantityDiffersByMoreThanOneUnit_returnsErrorNamingTheExecutedQuantity() {
    givenOrderAndExecution(new BigDecimal("40434"), new BigDecimal("40436"));
    givenReferencePrice(new BigDecimal("10.09"), TRADE_DATE);

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(confirmation());

    assertThat(result.quantityStatus()).isEqualTo(ERROR);
    assertThat(result.details())
        .containsEntry("executedQuantityMismatch", "40436")
        .doesNotContainKey("orderQuantityMismatch");
  }

  @Test
  void quantitiesWithinTolerance_recordNoMismatch() {
    givenOrderAndExecution(new BigDecimal("40435"), new BigDecimal("40433"));
    givenReferencePrice(new BigDecimal("10.09"), TRADE_DATE);

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(confirmation());

    assertThat(result.quantityStatus()).isEqualTo(OK);
    assertThat(result.details())
        .doesNotContainKeys("orderQuantityMismatch", "executedQuantityMismatch");
  }

  @Test
  void orderQuantityMissing_returnsError() {
    givenOrderAndExecution(null, new BigDecimal("40434"));
    givenReferencePrice(new BigDecimal("10.09"), TRADE_DATE);

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(confirmation());

    assertThat(result.quantityStatus()).isEqualTo(ERROR);
  }

  @Test
  void executionMissing_theDayAfterTheTrade_returnsPendingExecution() {
    givenOrderWithoutExecution(new BigDecimal("40434"));
    given(reconciliationRecorder.firstReportReconciledAsOfOrAfter(DAY_AFTER_TRADE_DATE))
        .willReturn(Optional.empty());
    givenReferencePrice(new BigDecimal("10.09"), TRADE_DATE);

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(confirmation());

    assertThat(result.quantityStatus()).isEqualTo(PENDING_EXECUTION);
    assertThat(result.details())
        .containsEntry("awaitingSebReportAsOf", "2026-06-09")
        .containsEntry("executionPendingUntil", "2026-06-10");
  }

  @Test
  void
      executionMissing_twoDaysAfterTheTrade_beforeSebsReportForTheDayAfterIsReconciled_staysPending() {
    givenOrderWithoutExecution(new BigDecimal("40434"));
    given(reconciliationRecorder.firstReportReconciledAsOfOrAfter(DAY_AFTER_TRADE_DATE))
        .willReturn(Optional.empty());
    givenReferencePrice(new BigDecimal("10.09"), TRADE_DATE);

    FtConfirmationResult result = service(TWO_DAYS_AFTER_TRADE).verify(confirmation());

    assertThat(result.quantityStatus()).isEqualTo(PENDING_EXECUTION);
  }

  @Test
  void executionMissing_afterSebsReportForTheDayAfterTheTradeIsReconciled_returnsErrorNamingIt() {
    givenOrderWithoutExecution(new BigDecimal("40434"));
    given(reconciliationRecorder.firstReportReconciledAsOfOrAfter(DAY_AFTER_TRADE_DATE))
        .willReturn(Optional.of(DAY_AFTER_TRADE_DATE));
    givenReferencePrice(new BigDecimal("10.09"), TRADE_DATE);

    FtConfirmationResult result = service(TWO_DAYS_AFTER_TRADE).verify(confirmation());

    assertThat(result.quantityStatus()).isEqualTo(ERROR);
    assertThat(result.details())
        .containsEntry("executionMissing", "no execution after SEB pending report as of 2026-06-09")
        .containsEntry("sebReportAsOf", "2026-06-09")
        .doesNotContainKey("executedQuantity");
  }

  @Test
  void executionMissing_whenNoSebReportIsReconciledWithinTwoBusinessDays_returnsError() {
    givenOrderWithoutExecution(new BigDecimal("40434"));
    given(reconciliationRecorder.firstReportReconciledAsOfOrAfter(DAY_AFTER_TRADE_DATE))
        .willReturn(Optional.empty());
    givenReferencePrice(new BigDecimal("10.09"), TRADE_DATE);

    FtConfirmationResult result = service(THREE_DAYS_AFTER_TRADE).verify(confirmation());

    assertThat(result.quantityStatus()).isEqualTo(ERROR);
    assertThat(result.details()).containsEntry("sebReportMissingAsOf", "2026-06-09");
  }

  @Test
  void executionCommittedWithTheReconciledReport_afterTheFirstRead_isFoundOnTheSecondRead() {
    TransactionOrder order = order(new BigDecimal("40434"));
    given(orderRepository.findByInstrumentIsin(ISIN)).willReturn(List.of(order));
    given(executionRepository.findAllByOrderId(order.getId()))
        .willReturn(List.of())
        .willReturn(List.of(execution(new BigDecimal("40434"))));
    given(reconciliationRecorder.firstReportReconciledAsOfOrAfter(DAY_AFTER_TRADE_DATE))
        .willReturn(Optional.of(DAY_AFTER_TRADE_DATE));
    givenReferencePrice(new BigDecimal("10.09"), TRADE_DATE);

    FtConfirmationResult result = service(TWO_DAYS_AFTER_TRADE).verify(confirmation());

    assertThat(result.quantityStatus()).isEqualTo(OK);
    assertThat(result.details())
        .containsEntry("executedQuantity", "40434")
        .doesNotContainKeys("executionMissing", "sebReportAsOf");
  }

  @Test
  void executionMissingAndOrderQuantityMismatch_returnsErrorEvenWithinPendingWindow() {
    givenOrderWithoutExecution(new BigDecimal("40432"));
    given(reconciliationRecorder.firstReportReconciledAsOfOrAfter(DAY_AFTER_TRADE_DATE))
        .willReturn(Optional.empty());
    givenReferencePrice(new BigDecimal("10.09"), TRADE_DATE);

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(confirmation());

    assertThat(result.quantityStatus()).isEqualTo(ERROR);
    assertThat(result.details()).containsEntry("orderQuantityMismatch", "40432");
  }

  @Test
  void referencePriceMissing_returnsPendingNav() {
    givenOrderAndExecution(new BigDecimal("40434"), new BigDecimal("40434"));
    given(positionPriceResolver.resolve(ISIN, TRADE_DATE)).willReturn(Optional.empty());

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(confirmation());

    assertThat(result.priceStatus()).isEqualTo(PENDING_NAV);
  }

  @Test
  void referencePriceHasNoPriceData_returnsPendingNav() {
    givenOrderAndExecution(new BigDecimal("40434"), new BigDecimal("40434"));
    given(positionPriceResolver.resolve(ISIN, TRADE_DATE))
        .willReturn(
            Optional.of(
                ResolvedPrice.builder().validationStatus(ValidationStatus.NO_PRICE_DATA).build()));

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(confirmation());

    assertThat(result.priceStatus()).isEqualTo(PENDING_NAV);
  }

  @Test
  void referencePriceHasNoPriceDataButMatchingDate_returnsPendingNav() {
    givenOrderAndExecution(new BigDecimal("40434"), new BigDecimal("40434"));
    given(positionPriceResolver.resolve(ISIN, TRADE_DATE))
        .willReturn(
            Optional.of(
                ResolvedPrice.builder()
                    .usedPrice(new BigDecimal("10.09"))
                    .priceDate(TRADE_DATE)
                    .validationStatus(ValidationStatus.NO_PRICE_DATA)
                    .build()));

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(confirmation());

    assertThat(result.priceStatus()).isEqualTo(PENDING_NAV);
  }

  @Test
  void referencePriceForDifferentDate_returnsPendingNav() {
    givenOrderAndExecution(new BigDecimal("40434"), new BigDecimal("40434"));
    givenReferencePrice(new BigDecimal("10.09"), TRADE_DATE.minusDays(1));

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(confirmation());

    assertThat(result.priceStatus()).isEqualTo(PENDING_NAV);
  }

  @Test
  void priceOutsideTolerance_returnsError() {
    givenOrderAndExecution(new BigDecimal("40434"), new BigDecimal("40434"));
    givenReferencePrice(new BigDecimal("10.09"), TRADE_DATE);

    FtConfirmationResult result =
        service(DAY_AFTER_TRADE)
            .verify(confirmation(new BigDecimal("40434"), new BigDecimal("10.102")));

    assertThat(result.priceStatus()).isEqualTo(ERROR);
    assertThat(result.details()).containsKey("priceDeltaPercent");
  }

  @Test
  void negativeReferencePrice_returnsError() {
    givenOrderAndExecution(new BigDecimal("40434"), new BigDecimal("40434"));
    givenReferencePrice(new BigDecimal("-10.09"), TRADE_DATE);

    FtConfirmationResult result =
        service(DAY_AFTER_TRADE)
            .verify(confirmation(new BigDecimal("40434"), new BigDecimal("10.09")));

    assertThat(result.priceStatus()).isEqualTo(ERROR);
    assertThat(result.details()).containsKey("invalidReferencePrice");
  }

  @Test
  void zeroReferencePrice_returnsError() {
    givenOrderAndExecution(new BigDecimal("40434"), new BigDecimal("40434"));
    givenReferencePrice(new BigDecimal("0.00"), TRADE_DATE);

    FtConfirmationResult result =
        service(DAY_AFTER_TRADE)
            .verify(confirmation(new BigDecimal("40434"), new BigDecimal("10.09")));

    assertThat(result.priceStatus()).isEqualTo(ERROR);
    assertThat(result.details()).containsKey("invalidReferencePrice");
  }

  @Test
  void priceExactlyAtToleranceBoundary_returnsOk() {
    givenOrderAndExecution(new BigDecimal("40434"), new BigDecimal("40434"));
    givenReferencePrice(new BigDecimal("10.09"), TRADE_DATE);

    FtConfirmationResult result =
        service(DAY_AFTER_TRADE)
            .verify(confirmation(new BigDecimal("40434"), new BigDecimal("10.10009")));

    assertThat(result.priceStatus()).isEqualTo(OK);
  }

  @Test
  void recordsAuditEventWithInputsAndResult() {
    TransactionOrder order =
        givenOrderAndExecution(new BigDecimal("40434"), new BigDecimal("40434"));
    givenReferencePrice(new BigDecimal("10.09"), TRADE_DATE);
    FtConfirmation confirmation = confirmation();

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(confirmation);

    verify(auditRecorder)
        .recordOutcome(order, new FtConfirmationOutcome(confirmation, result), "admin");
  }

  @Test
  void cancellationWithNoNewConfirmationAfterTheNextBusinessDay_returnsCancelled() {
    TransactionOrder order = order(new BigDecimal("40434"));
    given(orderRepository.findByInstrumentIsin(ISIN)).willReturn(List.of(order));
    givenCancellationFirstSeenOn(TRADE_DATE);

    FtConfirmationResult result = service(TWO_DAYS_AFTER_TRADE).verify(cancellationConfirmation());

    assertThat(result.quantityStatus()).isEqualTo(CANCELLED);
    assertThat(result.priceStatus()).isEqualTo(CANCELLED);
    assertThat(result.details())
        .containsEntry("orderUuid", ORDER_UUID.toString())
        .containsEntry("cancellationSignature", ISIN + "|FT-ACC|" + TRADE_DATE + "|10.09")
        .containsEntry("replacementMissing", "no new confirmation by 2026-06-09");
  }

  @Test
  void cancellationFirstSeenDaysAfterTheTrade_waitsUntilTheNextBusinessDayAfterItArrived() {
    TransactionOrder order = order(new BigDecimal("40434"));
    given(orderRepository.findByInstrumentIsin(ISIN)).willReturn(List.of(order));
    givenCancellationFirstSeenOn(LocalDate.of(2026, 6, 10));

    FtConfirmationResult result =
        service(Instant.parse("2026-06-11T08:00:00Z")).verify(cancellationConfirmation());

    assertThat(result.quantityStatus()).isEqualTo(PENDING_REPLACEMENT);
    assertThat(result.details()).containsEntry("replacementPendingUntil", "2026-06-11");
  }

  @Test
  void verifyAll_anotherTradeThatDay_doesNotHideACancellationWithNoNewConfirmation() {
    TransactionOrder cancelledOrder = order(42L, new BigDecimal("40434"));
    TransactionOrder otherOrder = order(10L, new BigDecimal("250"));
    given(orderRepository.findByInstrumentIsin(ISIN))
        .willReturn(List.of(cancelledOrder, otherOrder));
    given(executionRepository.findAllByOrderId(10L))
        .willReturn(List.of(execution(new BigDecimal("250"))));
    givenReferencePrice(new BigDecimal("10.09"), TRADE_DATE);
    givenCancellationFirstSeenOn(TRADE_DATE);
    FtConfirmation otherTrade =
        new FtConfirmation(
            TUK75,
            ISIN,
            TRADE_DATE,
            new BigDecimal("250"),
            new BigDecimal("10.09"),
            NORMAL,
            "FT-ACC");

    List<FtConfirmationBatchResult> results =
        service(TWO_DAYS_AFTER_TRADE)
            .verifyAll(
                List.of(
                    confirmationOnFtAccount(new BigDecimal("10.09")),
                    cancellationConfirmation(),
                    otherTrade),
                "ops");

    assertThat(results.get(0).result().quantityStatus()).isEqualTo(VOIDED);
    assertThat(results.get(1).result().quantityStatus()).isEqualTo(CANCELLED);
    assertThat(results.get(1).result().details()).containsEntry("orderUuid", ORDER_UUID.toString());
    assertThat(results.get(2).result().quantityStatus()).isEqualTo(OK);
  }

  @Test
  void cancellationWithNoNewConfirmationYet_waitsUntilTheEndOfTheNextBusinessDay() {
    TransactionOrder order = order(new BigDecimal("40434"));
    given(orderRepository.findByInstrumentIsin(ISIN)).willReturn(List.of(order));
    givenCancellationFirstSeenOn(TRADE_DATE);

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(cancellationConfirmation());

    assertThat(result.quantityStatus()).isEqualTo(PENDING_REPLACEMENT);
    assertThat(result.priceStatus()).isEqualTo(PENDING_REPLACEMENT);
    assertThat(result.details()).containsEntry("replacementPendingUntil", "2026-06-09");
  }

  @Test
  void verifyAll_cancellationFollowedByANewConfirmation_returnsReplaced_evenAfterTheDeadline() {
    givenOrderAndExecution(new BigDecimal("40434"), new BigDecimal("40434"));
    givenReferencePrice(new BigDecimal("10.12"), TRADE_DATE);
    FtConfirmation newConfirmation = confirmationOnFtAccount(new BigDecimal("10.12"));

    List<FtConfirmationBatchResult> results =
        service(TWO_DAYS_AFTER_TRADE)
            .verifyAll(List.of(cancellationConfirmation(), newConfirmation), "ops");

    assertThat(results.get(0).result().quantityStatus()).isEqualTo(REPLACED);
    assertThat(results.get(0).result().priceStatus()).isEqualTo(REPLACED);
    assertThat(results.get(0).result().details()).containsEntry("orderUuid", ORDER_UUID.toString());
    assertThat(results.get(1).result().quantityStatus()).isEqualTo(OK);
    assertThat(results.get(1).result().priceStatus()).isEqualTo(OK);
  }

  @Test
  void verifyAll_cancellationWithANewConfirmationForEachMatchingOrder_isReplaced() {
    TransactionOrder first = order(10L, new BigDecimal("40434"));
    TransactionOrder second = order(42L, new BigDecimal("40434"));
    given(orderRepository.findByInstrumentIsin(ISIN)).willReturn(List.of(first, second));

    List<FtConfirmationBatchResult> results =
        service(TWO_DAYS_AFTER_TRADE)
            .verifyAll(
                List.of(
                    cancellationConfirmation(),
                    confirmationOnFtAccount(new BigDecimal("10.12")),
                    confirmationOnFtAccount(new BigDecimal("10.13"))),
                "ops");

    assertThat(results.get(0).result().quantityStatus()).isEqualTo(REPLACED);
  }

  @Test
  void verifyAll_confirmationFtLaterCancelled_returnsVoided_withoutCheckingItsQuantityOrPrice() {
    TransactionOrder order = order(new BigDecimal("40434"));
    given(orderRepository.findByInstrumentIsin(ISIN)).willReturn(List.of(order));
    FtConfirmation cancelledConfirmation = confirmationOnFtAccount(new BigDecimal("10.09"));
    givenCancellationFirstSeenOn(TRADE_DATE);

    List<FtConfirmationBatchResult> results =
        service(DAY_AFTER_TRADE)
            .verifyAll(List.of(cancelledConfirmation, cancellationConfirmation()), "ops");

    FtConfirmationResult voided = results.get(0).result();
    assertThat(voided.quantityStatus()).isEqualTo(VOIDED);
    assertThat(voided.priceStatus()).isEqualTo(VOIDED);
    assertThat(voided.details())
        .containsEntry("orderUuid", ORDER_UUID.toString())
        .containsEntry("cancellationSignature", ISIN + "|FT-ACC|" + TRADE_DATE + "|10.09");
    assertThat(results.get(1).result().quantityStatus()).isEqualTo(PENDING_REPLACEMENT);
    verifyNoInteractions(executionRepository, positionPriceResolver);
    verify(auditRecorder)
        .recordOutcome(order, new FtConfirmationOutcome(cancelledConfirmation, voided), "ops");
  }

  @Test
  void verifyAll_confirmationFtCancelledOnAnAccountWithNoOrder_isVoidedNotOrphan() {
    given(orderRepository.findByInstrumentIsin(ISIN)).willReturn(List.of());
    FtConfirmation cancelledConfirmation = confirmationOnFtAccount(new BigDecimal("10.09"));

    List<FtConfirmationBatchResult> results =
        service(TWO_DAYS_AFTER_TRADE)
            .verifyAll(List.of(cancelledConfirmation, cancellationConfirmation()), "ops");

    assertThat(results.get(0).result().quantityStatus()).isEqualTo(VOIDED);
    assertThat(results.get(1).result().quantityStatus()).isEqualTo(NOTHING_TO_CANCEL);
    verify(auditRecorder)
        .recordOutcome(
            null, new FtConfirmationOutcome(cancelledConfirmation, results.get(0).result()), "ops");
  }

  @Test
  void cancellationConfirmation_recordsAuditEvent() {
    TransactionOrder order = order(new BigDecimal("40434"));
    given(orderRepository.findByInstrumentIsin(ISIN)).willReturn(List.of(order));
    FtConfirmation confirmation = cancellationConfirmation();
    givenCancellationFirstSeenOn(TRADE_DATE);

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(confirmation);

    verify(auditRecorder)
        .recordOutcome(order, new FtConfirmationOutcome(confirmation, result), "admin");
  }

  @Test
  void cancellationWithNoNewConfirmation_multipleMatchingOrders_returnsAmbiguous_notCancelled() {
    TransactionOrder first = order(10L, new BigDecimal("40434"));
    TransactionOrder second = order(42L, new BigDecimal("40434"));
    given(orderRepository.findByInstrumentIsin(ISIN)).willReturn(List.of(first, second));
    givenCancellationFirstSeenOn(TRADE_DATE);

    FtConfirmationResult result = service(TWO_DAYS_AFTER_TRADE).verify(cancellationConfirmation());

    assertThat(result.quantityStatus()).isEqualTo(AMBIGUOUS);
    assertThat(result.priceStatus()).isEqualTo(AMBIGUOUS);
    assertThat(result.details()).containsEntry("ambiguousOrderCount", "2");
  }

  @Test
  void cancellationWithNoOrderInTheRegistry_returnsNothingToCancel() {
    given(orderRepository.findByInstrumentIsin(ISIN)).willReturn(List.of());
    FtConfirmation confirmation = cancellationConfirmation();

    FtConfirmationResult result = service(TWO_DAYS_AFTER_TRADE).verify(confirmation);

    assertThat(result.quantityStatus()).isEqualTo(NOTHING_TO_CANCEL);
    assertThat(result.priceStatus()).isEqualTo(NOTHING_TO_CANCEL);
    verify(auditRecorder)
        .recordOutcome(null, new FtConfirmationOutcome(confirmation, result), "admin");
  }

  @Test
  void cancellationNeedingAction_clearsOnceTheOrderIsCancelledInTheRegistry() {
    TransactionOrder order = order(new BigDecimal("40434"));
    order.setOrderStatus(OrderStatus.CANCELLED);
    given(orderRepository.findByInstrumentIsin(ISIN)).willReturn(List.of(order));

    FtConfirmationResult result = service(TWO_DAYS_AFTER_TRADE).verify(cancellationConfirmation());

    assertThat(result.quantityStatus()).isEqualTo(NOTHING_TO_CANCEL);
    assertThat(result.priceStatus()).isEqualTo(NOTHING_TO_CANCEL);
  }

  @Test
  void verifyAll_cancellationOfAnOrderCancelledInTheRegistry_isNotMovedOntoAnotherLiveTrade() {
    TransactionOrder cancelledOrder = order(42L, new BigDecimal("40434"));
    cancelledOrder.setOrderStatus(OrderStatus.CANCELLED);
    TransactionOrder otherOrder = order(10L, new BigDecimal("250"));
    given(orderRepository.findByInstrumentIsin(ISIN))
        .willReturn(List.of(cancelledOrder, otherOrder));

    List<FtConfirmationBatchResult> results =
        service(TWO_DAYS_AFTER_TRADE)
            .verifyAll(
                List.of(cancellationConfirmation(), otherTrade(new BigDecimal("10.09"))), "ops");

    FtConfirmationResult result = results.get(0).result();
    assertThat(result.quantityStatus()).isEqualTo(NOTHING_TO_CANCEL);
    assertThat(result.details()).doesNotContainKey("orderUuid");
  }

  @Test
  void verifyAll_cancellationMatchingACancelledOrder_stillAlertsWhileALiveOrderHasNoConfirmation() {
    TransactionOrder cancelledOrder = order(42L, new BigDecimal("40434"));
    cancelledOrder.setOrderStatus(OrderStatus.CANCELLED);
    TransactionOrder liveOrder = order(10L, new BigDecimal("41000"));
    given(orderRepository.findByInstrumentIsin(ISIN))
        .willReturn(List.of(cancelledOrder, liveOrder));
    givenCancellationFirstSeenOn(TRADE_DATE);

    List<FtConfirmationBatchResult> results =
        service(TWO_DAYS_AFTER_TRADE)
            .verifyAll(
                List.of(
                    confirmationOnFtAccount(new BigDecimal("10.09")), cancellationConfirmation()),
                "ops");

    FtConfirmationResult result = results.get(1).result();
    assertThat(result.quantityStatus()).isEqualTo(CANCELLED);
    assertThat(result.details()).doesNotContainKey("orderUuid");
  }

  @Test
  void verifyAll_anotherTradeConfirmedTwiceAtDifferentPrices_doesNotReplaceTheCancelledTrade() {
    TransactionOrder cancelledOrder = order(42L, new BigDecimal("40434"));
    TransactionOrder otherOrder = order(10L, new BigDecimal("250"));
    given(orderRepository.findByInstrumentIsin(ISIN))
        .willReturn(List.of(cancelledOrder, otherOrder));
    givenCancellationFirstSeenOn(TRADE_DATE);

    List<FtConfirmationBatchResult> results =
        service(TWO_DAYS_AFTER_TRADE)
            .verifyAll(
                List.of(
                    confirmationOnFtAccount(new BigDecimal("10.09")),
                    cancellationConfirmation(),
                    otherTrade(new BigDecimal("10.09")),
                    otherTrade(new BigDecimal("10.10"))),
                "ops");

    assertThat(results.get(1).result().quantityStatus()).isEqualTo(CANCELLED);
  }

  @Test
  void normalConfirmation_picksOrderMatchingQuantityNotOldest() {
    TransactionOrder oldest = order(10L, new BigDecimal("99999"));
    TransactionOrder matching = order(42L, new BigDecimal("40434"));
    given(orderRepository.findByInstrumentIsin(ISIN)).willReturn(List.of(oldest, matching));
    given(executionRepository.findAllByOrderId(42L))
        .willReturn(List.of(execution(new BigDecimal("40434"))));
    givenReferencePrice(new BigDecimal("10.09"), TRADE_DATE);

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(confirmation());

    assertThat(result.quantityStatus()).isEqualTo(OK);
    assertThat(result.details()).containsEntry("orderUuid", ORDER_UUID.toString());
  }

  @Test
  void normalConfirmation_multipleOrdersMatchQuantity_returnsAmbiguous() {
    TransactionOrder first = order(10L, new BigDecimal("40434"));
    TransactionOrder second = order(42L, new BigDecimal("40434"));
    given(orderRepository.findByInstrumentIsin(ISIN)).willReturn(List.of(first, second));

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(confirmation());

    assertThat(result.quantityStatus()).isEqualTo(AMBIGUOUS);
    assertThat(result.priceStatus()).isEqualTo(AMBIGUOUS);
    assertThat(result.details()).containsEntry("ambiguousOrderCount", "2");
  }

  @Test
  void normalConfirmation_noOrderMatchesQuantity_returnsAmbiguousNotOldest() {
    TransactionOrder first = order(10L, new BigDecimal("99999"));
    TransactionOrder second = order(42L, new BigDecimal("88888"));
    given(orderRepository.findByInstrumentIsin(ISIN)).willReturn(List.of(first, second));

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(confirmation());

    assertThat(result.quantityStatus()).isEqualTo(AMBIGUOUS);
    assertThat(result.priceStatus()).isEqualTo(AMBIGUOUS);
    assertThat(result.details()).containsEntry("ambiguousOrderCount", "2");
  }

  @Test
  void weekendTradeDate_returnsIgnored_noMismatch() {
    LocalDate saturday = LocalDate.of(2026, 6, 6); // Saturday
    FtConfirmation weekendConfirmation =
        new FtConfirmation(TUK75, ISIN, saturday, new BigDecimal("40434"), new BigDecimal("10.09"));

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(weekendConfirmation);

    assertThat(result.quantityStatus()).isEqualTo(IGNORED);
    assertThat(result.priceStatus()).isEqualTo(IGNORED);
    assertThat(result.details()).containsEntry("ignoreReason", "weekend trade date: " + saturday);
    verifyNoInteractions(orderRepository, executionRepository, positionPriceResolver);
    verifyNoInteractions(auditRecorder);
  }

  @Test
  void suppressedConfirmation_returnsIgnored_noMismatch() {
    FtConfirmation suppressed =
        new FtConfirmation(
            TUK75,
            ISIN,
            TRADE_DATE,
            new BigDecimal("40434"),
            new BigDecimal("10.09"),
            ee.tuleva.onboarding.investment.transaction.FtConfirmationType.NORMAL,
            null,
            true);

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(suppressed);

    assertThat(result.quantityStatus()).isEqualTo(IGNORED);
    assertThat(result.priceStatus()).isEqualTo(IGNORED);
    assertThat(result.details())
        .containsEntry("ignoreReason", "manually suppressed false positive");
    verifyNoInteractions(orderRepository, executionRepository, positionPriceResolver);
    verifyNoInteractions(auditRecorder);
  }

  @Test
  void verifyAll_returnsResultPerRowWithIndexAndIsin() {
    givenOrderAndExecution(new BigDecimal("40434"), new BigDecimal("40434"));
    givenReferencePrice(new BigDecimal("10.09"), TRADE_DATE);

    List<FtConfirmationBatchResult> results =
        service(DAY_AFTER_TRADE).verifyAll(List.of(confirmation()), "ops");

    assertThat(results).hasSize(1);
    FtConfirmationBatchResult row = results.get(0);
    assertThat(row.index()).isZero();
    assertThat(row.isin()).isEqualTo(ISIN);
    assertThat(row.error()).isNull();
    assertThat(row.result().quantityStatus()).isEqualTo(OK);
  }

  @Test
  void verifyAll_passesActorToAuditRecorder() {
    TransactionOrder order =
        givenOrderAndExecution(new BigDecimal("40434"), new BigDecimal("40434"));
    givenReferencePrice(new BigDecimal("10.09"), TRADE_DATE);
    FtConfirmation confirmation = confirmation();

    List<FtConfirmationBatchResult> results =
        service(DAY_AFTER_TRADE).verifyAll(List.of(confirmation), "alice");

    verify(auditRecorder)
        .recordOutcome(
            order, new FtConfirmationOutcome(confirmation, results.getFirst().result()), "alice");
  }

  @Test
  void verify_singleRow_publishesOutcomeToDigest() {
    TransactionOrder order = order(new BigDecimal("40432"));
    given(orderRepository.findByInstrumentIsin(ISIN)).willReturn(List.of(order));
    given(executionRepository.findAllByOrderId(order.getId()))
        .willReturn(List.of(execution(new BigDecimal("40432"))));
    givenReferencePrice(new BigDecimal("10.09"), TRADE_DATE);
    FtConfirmation confirmation = confirmation();

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(confirmation);

    verify(digest).publish(List.of(new FtConfirmationOutcome(confirmation, result)));
  }

  @Test
  void verifyAll_nullRow_isIsolatedWithoutAbortingOtherRows() {
    givenOrderAndExecution(new BigDecimal("40434"), new BigDecimal("40434"));
    givenReferencePrice(new BigDecimal("10.09"), TRADE_DATE);

    List<FtConfirmationBatchResult> results =
        service(DAY_AFTER_TRADE).verifyAll(Arrays.asList(null, confirmation()), "ops");

    assertThat(results).hasSize(2);
    assertThat(results.get(0).isin()).isNull();
    assertThat(results.get(0).error()).isEqualTo("null confirmation row");
    assertThat(results.get(1).result().quantityStatus()).isEqualTo(OK);
  }

  @Test
  void verifyAll_rowThatThrows_isIsolatedAsError() {
    given(orderRepository.findByInstrumentIsin("BAD")).willThrow(new RuntimeException("boom"));
    FtConfirmation bad =
        new FtConfirmation(TUK75, "BAD", TRADE_DATE, new BigDecimal("1"), new BigDecimal("1"));

    List<FtConfirmationBatchResult> results =
        service(DAY_AFTER_TRADE).verifyAll(List.of(bad), "ops");

    assertThat(results).hasSize(1);
    assertThat(results.get(0).isin()).isEqualTo("BAD");
    assertThat(results.get(0).result()).isNull();
    assertThat(results.get(0).error()).isEqualTo("boom");
  }

  @Test
  void verifyAll_rowThatThrows_isPassedToTheDigestAsUnchecked() {
    given(orderRepository.findByInstrumentIsin("BAD")).willThrow(new RuntimeException("boom"));
    FtConfirmation bad =
        new FtConfirmation(TUK75, "BAD", TRADE_DATE, new BigDecimal("1"), new BigDecimal("1"));

    service(DAY_AFTER_TRADE).verifyAll(List.of(bad), "ops");

    verify(digest).publish(List.of(new FtConfirmationOutcome(bad, unchecked("boom"))));
  }

  @Test
  void verifyAll_malformedRow_staysOutOfTheDigestSoTheOtherRowsAreStillSent() {
    TransactionOrder order = order(new BigDecimal("40432"));
    given(orderRepository.findByInstrumentIsin(ISIN)).willReturn(List.of(order));
    given(executionRepository.findAllByOrderId(order.getId()))
        .willReturn(List.of(execution(new BigDecimal("40432"))));
    givenReferencePrice(new BigDecimal("10.09"), TRADE_DATE);
    FtConfirmation withoutQuantity =
        new FtConfirmation(TUK75, ISIN, TRADE_DATE, null, new BigDecimal("10.09"));
    FtConfirmation valid = confirmation();

    List<FtConfirmationBatchResult> results =
        service(DAY_AFTER_TRADE).verifyAll(List.of(withoutQuantity, valid), "ops");

    assertThat(results.get(0).result()).isNull();
    verify(digest).publish(List.of(new FtConfirmationOutcome(valid, results.get(1).result())));
  }

  @Test
  void verifyAll_replacementThatCannotBeChecked_isStillSentWhileItsCancellationIsReplaced() {
    TransactionOrder order = order(new BigDecimal("40434"));
    given(orderRepository.findByInstrumentIsin(ISIN)).willReturn(List.of(order));
    given(positionPriceResolver.resolve(ISIN, TRADE_DATE))
        .willThrow(new RuntimeException("price source down"));
    FtConfirmation original = confirmationOnFtAccount(new BigDecimal("10.09"));
    FtConfirmation cancellation = cancellationConfirmation();
    FtConfirmation replacement = confirmationOnFtAccount(new BigDecimal("10.12"));

    List<FtConfirmationBatchResult> results =
        service(TWO_DAYS_AFTER_TRADE)
            .verifyAll(List.of(original, cancellation, replacement), "ops");

    assertThat(results.get(0).result().quantityStatus()).isEqualTo(VOIDED);
    assertThat(results.get(1).result().quantityStatus()).isEqualTo(REPLACED);
    verify(digest)
        .publish(
            List.of(
                new FtConfirmationOutcome(original, results.get(0).result()),
                new FtConfirmationOutcome(cancellation, results.get(1).result()),
                new FtConfirmationOutcome(replacement, unchecked("price source down"))));
  }

  @Test
  void verifyAll_passesEveryVerifiedOutcomeToDigest() {
    TransactionOrder order = order(new BigDecimal("40432"));
    given(orderRepository.findByInstrumentIsin(ISIN)).willReturn(List.of(order));
    given(executionRepository.findAllByOrderId(order.getId()))
        .willReturn(List.of(execution(new BigDecimal("40432"))));
    givenReferencePrice(new BigDecimal("10.09"), TRADE_DATE);
    FtConfirmation confirmation = confirmation();

    List<FtConfirmationBatchResult> results =
        service(DAY_AFTER_TRADE).verifyAll(List.of(confirmation), "ops");

    FtConfirmationResult result = results.get(0).result();
    assertThat(result.quantityStatus()).isEqualTo(ERROR);
    verify(digest).publish(List.of(new FtConfirmationOutcome(confirmation, result)));
  }

  @Test
  void verifyAll_passesCleanOutcomesToDigestToo_alertingIsTheDigestsDecision() {
    givenOrderAndExecution(new BigDecimal("40434"), new BigDecimal("40434"));
    givenReferencePrice(new BigDecimal("10.09"), TRADE_DATE);
    FtConfirmation confirmation = confirmation();

    List<FtConfirmationBatchResult> results =
        service(DAY_AFTER_TRADE).verifyAll(List.of(confirmation), "ops");

    FtConfirmationResult result = results.get(0).result();
    assertThat(result.quantityStatus()).isEqualTo(OK);
    verify(digest).publish(List.of(new FtConfirmationOutcome(confirmation, result)));
  }

  private FtConfirmation confirmation() {
    return confirmation(new BigDecimal("40434"), new BigDecimal("10.09"));
  }

  private FtConfirmation confirmation(BigDecimal quantity, BigDecimal grossPrice) {
    return new FtConfirmation(TUK75, ISIN, TRADE_DATE, quantity, grossPrice);
  }

  private void givenCancellationFirstSeenOn(LocalDate firstSeen) {
    given(auditRecorder.firstSeenOn(cancellationConfirmation())).willReturn(firstSeen);
  }

  private static FtConfirmationResult unchecked(String error) {
    return new FtConfirmationResult(UNCHECKED, UNCHECKED, Map.of("verificationError", error));
  }

  private FtConfirmation otherTrade(BigDecimal grossPrice) {
    return new FtConfirmation(
        TUK75, ISIN, TRADE_DATE, new BigDecimal("250"), grossPrice, NORMAL, "FT-ACC");
  }

  private FtConfirmation confirmationOnFtAccount(BigDecimal grossPrice) {
    return new FtConfirmation(
        TUK75, ISIN, TRADE_DATE, new BigDecimal("40434"), grossPrice, NORMAL, "FT-ACC");
  }

  private FtConfirmation cancellationConfirmation() {
    return new FtConfirmation(
        TUK75,
        ISIN,
        TRADE_DATE,
        new BigDecimal("40434"),
        new BigDecimal("10.09"),
        CANCELLATION,
        "FT-ACC");
  }

  private TransactionOrder givenOrderAndExecution(
      BigDecimal orderQuantity, BigDecimal executedQuantity) {
    TransactionOrder order = order(orderQuantity);
    given(orderRepository.findByInstrumentIsin(ISIN)).willReturn(List.of(order));
    given(executionRepository.findAllByOrderId(order.getId()))
        .willReturn(List.of(execution(executedQuantity)));
    return order;
  }

  private void givenOrderWithoutExecution(BigDecimal orderQuantity) {
    TransactionOrder order = order(orderQuantity);
    given(orderRepository.findByInstrumentIsin(ISIN)).willReturn(List.of(order));
    given(executionRepository.findAllByOrderId(order.getId())).willReturn(List.of());
  }

  private void givenReferencePrice(BigDecimal price, LocalDate priceDate) {
    given(positionPriceResolver.resolve(ISIN, TRADE_DATE))
        .willReturn(
            Optional.of(
                ResolvedPrice.builder()
                    .usedPrice(price)
                    .priceDate(priceDate)
                    .priceSource(PriceSource.DEUTSCHE_BOERSE)
                    .validationStatus(ValidationStatus.OK)
                    .build()));
  }

  private TransactionOrder order(BigDecimal orderQuantity) {
    return order(42L, orderQuantity);
  }

  private TransactionOrder order(long id, BigDecimal orderQuantity) {
    return TransactionOrder.builder()
        .id(id)
        .fund(TUK75)
        .instrumentIsin(ISIN)
        .transactionType(BUY)
        .instrumentType(ETF)
        .orderQuantity(orderQuantity)
        .orderVenue(OrderVenue.FT)
        .orderUuid(id == 42L ? ORDER_UUID : new UUID(0, id))
        .orderStatus(EXECUTED)
        .orderTimestamp(Instant.parse("2026-06-08T09:30:00Z"))
        .build();
  }

  private TransactionExecution execution(BigDecimal executedQuantity) {
    return TransactionExecution.builder()
        .id(7L)
        .orderId(42L)
        .executedQuantity(executedQuantity)
        .unitPrice(new BigDecimal("10.09"))
        .executionTimestamp(Instant.parse("2026-06-08T15:30:00Z"))
        .source("FT")
        .build();
  }

  private TransactionExecution executionPiece(long id, BigDecimal executedQuantity) {
    return TransactionExecution.builder()
        .id(id)
        .orderId(42L)
        .brokerTransactionId("DLA" + id)
        .executedQuantity(executedQuantity)
        .unitPrice(new BigDecimal("10.09"))
        .executionTimestamp(Instant.parse("2026-06-08T15:30:00Z"))
        .source("FT")
        .build();
  }
}
