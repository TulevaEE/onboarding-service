package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.ERROR;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.OK;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.PENDING_EXECUTION;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.PENDING_NAV;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.comparisons.fundvalue.PriceSource;
import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.comparisons.fundvalue.ValidationStatus;
import ee.tuleva.onboarding.investment.calendar.Target2Calendar;
import ee.tuleva.onboarding.investment.transaction.FtConfirmation;
import ee.tuleva.onboarding.investment.transaction.FtConfirmationResult;
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
import java.util.List;
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
  private static final UUID ORDER_UUID = UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29");

  @Mock private TransactionOrderRepository orderRepository;
  @Mock private TransactionExecutionRepository executionRepository;
  @Mock private PositionPriceResolver positionPriceResolver;
  @Mock private FtConfirmationAuditRecorder auditRecorder;

  private FtConfirmationVerificationService service(Instant now) {
    return new FtConfirmationVerificationService(
        orderRepository,
        executionRepository,
        positionPriceResolver,
        new PriceValidator(),
        new Target2Calendar(),
        auditRecorder,
        Clock.fixed(now, ZoneOffset.UTC));
  }

  @Test
  void orderNotFound_returnsEmpty() {
    given(orderRepository.findByInstrumentIsin(ISIN)).willReturn(List.of());

    Optional<FtConfirmationResult> result = service(DAY_AFTER_TRADE).verify(confirmation());

    assertThat(result).isEmpty();
    verifyNoInteractions(auditRecorder);
  }

  @Test
  void orderForDifferentFund_returnsEmpty() {
    TransactionOrder order = order(new BigDecimal("40434"));
    order.setFund(TUK00);
    given(orderRepository.findByInstrumentIsin(ISIN)).willReturn(List.of(order));

    Optional<FtConfirmationResult> result = service(DAY_AFTER_TRADE).verify(confirmation());

    assertThat(result).isEmpty();
  }

  @Test
  void orderForDifferentTradeDate_returnsEmpty() {
    TransactionOrder order = order(new BigDecimal("40434"));
    order.setOrderTimestamp(Instant.parse("2026-06-05T09:30:00Z"));
    given(orderRepository.findByInstrumentIsin(ISIN)).willReturn(List.of(order));

    Optional<FtConfirmationResult> result = service(DAY_AFTER_TRADE).verify(confirmation());

    assertThat(result).isEmpty();
  }

  @Test
  void quantityAndPriceMatch_returnsOkOk() {
    givenOrderAndExecution(new BigDecimal("40434"), new BigDecimal("40434"));
    givenReferencePrice(new BigDecimal("10.09"), TRADE_DATE);

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(confirmation()).orElseThrow();

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

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(confirmation()).orElseThrow();

    assertThat(result.quantityStatus()).isEqualTo(OK);
  }

  @Test
  void orderQuantityDiffersByMoreThanOneUnit_returnsError() {
    givenOrderAndExecution(new BigDecimal("40432"), new BigDecimal("40434"));
    givenReferencePrice(new BigDecimal("10.09"), TRADE_DATE);

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(confirmation()).orElseThrow();

    assertThat(result.quantityStatus()).isEqualTo(ERROR);
  }

  @Test
  void executedQuantityDiffersByMoreThanOneUnit_returnsError() {
    givenOrderAndExecution(new BigDecimal("40434"), new BigDecimal("40436"));
    givenReferencePrice(new BigDecimal("10.09"), TRADE_DATE);

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(confirmation()).orElseThrow();

    assertThat(result.quantityStatus()).isEqualTo(ERROR);
  }

  @Test
  void orderQuantityMissing_returnsError() {
    givenOrderAndExecution(null, new BigDecimal("40434"));
    givenReferencePrice(new BigDecimal("10.09"), TRADE_DATE);

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(confirmation()).orElseThrow();

    assertThat(result.quantityStatus()).isEqualTo(ERROR);
  }

  @Test
  void executionMissingWithinOneBusinessDay_returnsPendingExecution() {
    TransactionOrder order = order(new BigDecimal("40434"));
    given(orderRepository.findByInstrumentIsin(ISIN)).willReturn(List.of(order));
    given(executionRepository.findByOrderId(order.getId())).willReturn(Optional.empty());
    givenReferencePrice(new BigDecimal("10.09"), TRADE_DATE);

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(confirmation()).orElseThrow();

    assertThat(result.quantityStatus()).isEqualTo(PENDING_EXECUTION);
    assertThat(result.details()).containsEntry("executionPendingUntil", "2026-06-09");
  }

  @Test
  void executionMissingAfterOneBusinessDay_returnsError() {
    TransactionOrder order = order(new BigDecimal("40434"));
    given(orderRepository.findByInstrumentIsin(ISIN)).willReturn(List.of(order));
    given(executionRepository.findByOrderId(order.getId())).willReturn(Optional.empty());
    givenReferencePrice(new BigDecimal("10.09"), TRADE_DATE);

    FtConfirmationResult result =
        service(TWO_DAYS_AFTER_TRADE).verify(confirmation()).orElseThrow();

    assertThat(result.quantityStatus()).isEqualTo(ERROR);
  }

  @Test
  void executionMissingAndOrderQuantityMismatch_returnsErrorEvenWithinPendingWindow() {
    TransactionOrder order = order(new BigDecimal("40432"));
    given(orderRepository.findByInstrumentIsin(ISIN)).willReturn(List.of(order));
    given(executionRepository.findByOrderId(order.getId())).willReturn(Optional.empty());
    givenReferencePrice(new BigDecimal("10.09"), TRADE_DATE);

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(confirmation()).orElseThrow();

    assertThat(result.quantityStatus()).isEqualTo(ERROR);
  }

  @Test
  void referencePriceMissing_returnsPendingNav() {
    givenOrderAndExecution(new BigDecimal("40434"), new BigDecimal("40434"));
    given(positionPriceResolver.resolve(ISIN, TRADE_DATE)).willReturn(Optional.empty());

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(confirmation()).orElseThrow();

    assertThat(result.priceStatus()).isEqualTo(PENDING_NAV);
  }

  @Test
  void referencePriceHasNoPriceData_returnsPendingNav() {
    givenOrderAndExecution(new BigDecimal("40434"), new BigDecimal("40434"));
    given(positionPriceResolver.resolve(ISIN, TRADE_DATE))
        .willReturn(
            Optional.of(
                ResolvedPrice.builder().validationStatus(ValidationStatus.NO_PRICE_DATA).build()));

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(confirmation()).orElseThrow();

    assertThat(result.priceStatus()).isEqualTo(PENDING_NAV);
  }

  @Test
  void referencePriceForDifferentDate_returnsPendingNav() {
    givenOrderAndExecution(new BigDecimal("40434"), new BigDecimal("40434"));
    givenReferencePrice(new BigDecimal("10.09"), TRADE_DATE.minusDays(1));

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(confirmation()).orElseThrow();

    assertThat(result.priceStatus()).isEqualTo(PENDING_NAV);
  }

  @Test
  void priceOutsideTolerance_returnsError() {
    givenOrderAndExecution(new BigDecimal("40434"), new BigDecimal("40434"));
    givenReferencePrice(new BigDecimal("10.09"), TRADE_DATE);

    FtConfirmationResult result =
        service(DAY_AFTER_TRADE)
            .verify(confirmation(new BigDecimal("40434"), new BigDecimal("10.102")))
            .orElseThrow();

    assertThat(result.priceStatus()).isEqualTo(ERROR);
    assertThat(result.details()).containsKey("priceDeltaPercent");
  }

  @Test
  void priceExactlyAtToleranceBoundary_returnsOk() {
    givenOrderAndExecution(new BigDecimal("40434"), new BigDecimal("40434"));
    givenReferencePrice(new BigDecimal("10.09"), TRADE_DATE);

    FtConfirmationResult result =
        service(DAY_AFTER_TRADE)
            .verify(confirmation(new BigDecimal("40434"), new BigDecimal("10.10009")))
            .orElseThrow();

    assertThat(result.priceStatus()).isEqualTo(OK);
  }

  @Test
  void recordsAuditEventWithInputsAndResult() {
    TransactionOrder order =
        givenOrderAndExecution(new BigDecimal("40434"), new BigDecimal("40434"));
    givenReferencePrice(new BigDecimal("10.09"), TRADE_DATE);
    FtConfirmation confirmation = confirmation();

    FtConfirmationResult result = service(DAY_AFTER_TRADE).verify(confirmation).orElseThrow();

    verify(auditRecorder).recordVerified(order, confirmation, result);
  }

  private FtConfirmation confirmation() {
    return confirmation(new BigDecimal("40434"), new BigDecimal("10.09"));
  }

  private FtConfirmation confirmation(BigDecimal quantity, BigDecimal grossPrice) {
    return new FtConfirmation(TUK75, ISIN, TRADE_DATE, quantity, grossPrice);
  }

  private TransactionOrder givenOrderAndExecution(
      BigDecimal orderQuantity, BigDecimal executedQuantity) {
    TransactionOrder order = order(orderQuantity);
    given(orderRepository.findByInstrumentIsin(ISIN)).willReturn(List.of(order));
    given(executionRepository.findByOrderId(order.getId()))
        .willReturn(Optional.of(execution(executedQuantity)));
    return order;
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
    return TransactionOrder.builder()
        .id(42L)
        .fund(TUK75)
        .instrumentIsin(ISIN)
        .transactionType(BUY)
        .instrumentType(ETF)
        .orderQuantity(orderQuantity)
        .orderVenue(OrderVenue.FT)
        .orderUuid(ORDER_UUID)
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
}
