package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.BatchStatus.CONFIRMED;
import static ee.tuleva.onboarding.investment.transaction.BatchStatus.DRAFT;
import static ee.tuleva.onboarding.investment.transaction.CommandStatus.CALCULATED;
import static ee.tuleva.onboarding.investment.transaction.CommandStatus.FAILED;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.TransactionMode.REBALANCE;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TransactionAdminServiceTest {

  private static final LocalDate AS_OF_DATE = LocalDate.parse("2026-06-10");
  private static final LocalDate TODAY = LocalDate.parse("2026-06-11");

  @Spy
  private Clock clock =
      Clock.fixed(Instant.parse("2026-06-11T09:00:00Z"), ZoneId.of("Europe/Tallinn"));

  @Mock private TransactionCommandRepository commandRepository;
  @Mock private TransactionBatchRepository batchRepository;
  @Mock private TransactionOrderRepository orderRepository;
  @Mock private TransactionAuditEventRepository auditEventRepository;
  @Mock private TransactionBatchFinalizer batchFinalizer;

  @Mock private BatchConfirmations batchConfirmations;

  @InjectMocks private TransactionAdminService service;

  private static final OrderSender SENDER =
      new OrderSender("fundmanager@tuleva.ee", "Fund Manager");

  private static TransactionBatch batch(Long id, BatchStatus status) {
    return batch(id, TUK75, status);
  }

  private static TransactionBatch batch(Long id, TulevaFund fund, BatchStatus status) {
    return TransactionBatch.builder()
        .id(id)
        .fund(fund)
        .status(status)
        .version(3L)
        .createdBy("system")
        .createdAt(Instant.parse("2026-06-10T09:00:00Z"))
        .metadata(Map.of())
        .build();
  }

  private void assertStillAnUnconfirmedDraft(TransactionBatch batch) {
    assertThat(batch.getStatus()).isEqualTo(DRAFT);
    assertThat(batch.getConfirmedBy()).isNull();
    assertThat(batch.getConfirmedAt()).isNull();
    then(batchRepository).should(never()).saveAndFlush(any());
    then(batchFinalizer).shouldHaveNoInteractions();
  }

  private static TransactionOrder order(Long id, TransactionBatch batch) {
    return TransactionOrder.builder()
        .id(id)
        .batch(batch)
        .fund(TUK75)
        .instrumentIsin("IE00BFG1TM61")
        .transactionType(BUY)
        .instrumentType(ETF)
        .orderAmount(new BigDecimal("1000.00"))
        .orderVenue(OrderVenue.SEB)
        .build();
  }

  @Test
  void discardBatch_concurrentlyModifiedBatch_throwsConflict() {
    TransactionBatch batch = batch(10L, DRAFT);
    given(batchRepository.findById(10L)).willReturn(Optional.of(batch));
    willThrow(new ObjectOptimisticLockingFailureException(TransactionBatch.class, 10L))
        .given(batchRepository)
        .saveAndFlush(batch);

    assertThatThrownBy(() -> service.discardBatch(10L, "admin"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
        .isEqualTo(409);
    then(orderRepository).should(never()).saveAll(any());
  }

  @Test
  void getCommand_returnsCommandWithOrdersOfItsBatch() {
    TransactionCommand command =
        TransactionCommand.builder()
            .id(1L)
            .fund(TUK75)
            .mode(REBALANCE)
            .asOfDate(AS_OF_DATE)
            .status(CALCULATED)
            .batchId(10L)
            .build();
    TransactionOrder order = order(100L, batch(10L, DRAFT));
    given(commandRepository.findById(1L)).willReturn(Optional.of(command));
    given(orderRepository.findByBatchId(10L)).willReturn(List.of(order));

    Optional<TransactionCommandResponse> response = service.getCommand(1L);

    assertThat(response).contains(TransactionCommandResponse.from(command, List.of(order), null));
  }

  @Test
  void getCommand_withoutBatch_returnsCommandWithoutOrders() {
    TransactionCommand command =
        TransactionCommand.builder()
            .id(1L)
            .fund(TUK75)
            .mode(REBALANCE)
            .asOfDate(AS_OF_DATE)
            .status(FAILED)
            .build();
    given(commandRepository.findById(1L)).willReturn(Optional.of(command));

    Optional<TransactionCommandResponse> response = service.getCommand(1L);

    assertThat(response).contains(TransactionCommandResponse.from(command, List.of(), null));
    then(orderRepository).shouldHaveNoInteractions();
  }

  @Test
  void getCommand_unknownId_returnsEmpty() {
    given(commandRepository.findById(999L)).willReturn(Optional.empty());

    assertThat(service.getCommand(999L)).isEmpty();
  }

  @Test
  void getBatch_returnsBatchWithOrders() {
    TransactionBatch batch = batch(10L, DRAFT);
    TransactionOrder order = order(100L, batch);
    given(batchRepository.findById(10L)).willReturn(Optional.of(batch));
    given(orderRepository.findByBatchId(10L)).willReturn(List.of(order));

    Optional<TransactionBatchResponse> response = service.getBatch(10L);

    assertThat(response).contains(TransactionBatchResponse.from(batch, List.of(order), null));
  }

  @Test
  void getBatch_includesCalculationCompletedPayloadAsSnapshot() {
    TransactionBatch batch = batch(10L, DRAFT);
    TransactionOrder order = order(100L, batch);
    given(batchRepository.findById(10L)).willReturn(Optional.of(batch));
    given(orderRepository.findByBatchId(10L)).willReturn(List.of(order));
    given(auditEventRepository.findByBatchIdOrderByCreatedAt(10L))
        .willReturn(
            List.of(
                TransactionAuditEvent.builder()
                    .batch(batch)
                    .eventType("CALCULATION_COMPLETED")
                    .payload(Map.of("summary", Map.of("tradeCount", 1)))
                    .build(),
                TransactionAuditEvent.builder()
                    .batch(batch)
                    .eventType("BATCH_FINALIZED")
                    .payload(Map.of("tradeDate", "2026-06-11"))
                    .build()));

    Optional<TransactionBatchResponse> response = service.getBatch(10L);

    assertThat(response)
        .get()
        .extracting(TransactionBatchResponse::calculationSnapshot)
        .isEqualTo(Map.of("summary", Map.of("tradeCount", 1)));
  }

  @Test
  void getBatch_withoutCalculationCompletedEvent_hasNullSnapshot() {
    TransactionBatch batch = batch(10L, DRAFT);
    given(batchRepository.findById(10L)).willReturn(Optional.of(batch));
    given(orderRepository.findByBatchId(10L)).willReturn(List.of());
    given(auditEventRepository.findByBatchIdOrderByCreatedAt(10L))
        .willReturn(
            List.of(
                TransactionAuditEvent.builder()
                    .batch(batch)
                    .eventType("BATCH_FINALIZED")
                    .payload(Map.of("tradeDate", "2026-06-11"))
                    .build()));

    Optional<TransactionBatchResponse> response = service.getBatch(10L);

    assertThat(response).get().extracting(TransactionBatchResponse::calculationSnapshot).isNull();
  }

  @Test
  void confirmAndSend_confirmsEveryFundsBatchAndSendsThemTogetherInTheSendersOwnName() {
    TransactionBatch tuk75 = batch(10L, DRAFT);
    TransactionBatch tuv100 = batch(11L, TUV100, DRAFT);
    given(batchRepository.findById(10L)).willReturn(Optional.of(tuk75));
    given(batchRepository.findById(11L)).willReturn(Optional.of(tuv100));
    given(batchConfirmations.requireAllowedToSend(tuk75, 3L, "operator-7")).willReturn(SENDER);
    given(batchConfirmations.requireAllowedToSend(tuv100, 3L, "operator-7")).willReturn(SENDER);
    willAnswer(
            invocation -> {
              List<TransactionBatch> confirmed = invocation.getArgument(0);
              assertThat(confirmed).extracting(TransactionBatch::getStatus).containsOnly(CONFIRMED);
              confirmed.forEach(batch -> batch.setStatus(BatchStatus.SENT));
              return null;
            })
        .given(batchFinalizer)
        .finalizeConfirmedBatches(List.of(tuk75, tuv100), SENDER);

    List<TransactionBatchResponse> responses =
        service.confirmAndSend(
            List.of(new ReviewedBatch(10L, 3L), new ReviewedBatch(11L, 3L)), "operator-7");

    then(batchFinalizer).should().finalizeConfirmedBatches(List.of(tuk75, tuv100), SENDER);
    assertThat(responses)
        .extracting(TransactionBatchResponse::status)
        .containsExactly(BatchStatus.SENT, BatchStatus.SENT);
    assertThat(List.of(tuk75, tuv100))
        .extracting(TransactionBatch::getConfirmedBy, TransactionBatch::getConfirmedAt)
        .containsOnly(tuple("operator-7", Instant.parse("2026-06-11T09:00:00Z")));
  }

  @Test
  void confirmAndSend_unknownBatch_throwsNotFound() {
    given(batchRepository.findById(999L)).willReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.confirmAndSend(List.of(new ReviewedBatch(999L, 3L)), "operator-7"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
        .isEqualTo(404);
  }

  @Test
  void confirmAndSend_theSameBatchListedTwice_isABadRequestAndConfirmsNothing() {
    assertThatThrownBy(
            () ->
                service.confirmAndSend(
                    List.of(new ReviewedBatch(10L, 3L), new ReviewedBatch(10L, 3L)), "operator-7"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
        .isEqualTo(400);
    then(batchRepository).shouldHaveNoInteractions();
    then(batchFinalizer).shouldHaveNoInteractions();
  }

  @Test
  void confirmAndSend_concurrentlyModifiedBatch_throwsConflictAndNeverFinalizes() {
    TransactionBatch batch = batch(10L, DRAFT);
    given(batchRepository.findById(10L)).willReturn(Optional.of(batch));
    given(batchConfirmations.requireAllowedToSend(batch, 3L, "operator-7")).willReturn(SENDER);
    willThrow(new ObjectOptimisticLockingFailureException(TransactionBatch.class, 10L))
        .given(batchRepository)
        .saveAndFlush(batch);

    assertThatThrownBy(
            () -> service.confirmAndSend(List.of(new ReviewedBatch(10L, 3L)), "operator-7"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
        .isEqualTo(409);
    then(batchFinalizer).should(never()).finalizeConfirmedBatches(any(), any());
  }

  @Test
  void confirmAndSend_oneBatchRefusedByTheConfirmations_isForbiddenAndLeavesEveryBatchADraft() {
    TransactionBatch tuk75 = batch(10L, DRAFT);
    TransactionBatch tuv100 = batch(11L, TUV100, DRAFT);
    given(batchRepository.findById(10L)).willReturn(Optional.of(tuk75));
    given(batchRepository.findById(11L)).willReturn(Optional.of(tuv100));
    given(batchConfirmations.requireAllowedToSend(tuk75, 3L, "operator-7")).willReturn(SENDER);
    willThrow(new ResponseStatusException(FORBIDDEN, "no first confirmation"))
        .given(batchConfirmations)
        .requireAllowedToSend(tuv100, 3L, "operator-7");

    assertThatThrownBy(
            () ->
                service.confirmAndSend(
                    List.of(new ReviewedBatch(10L, 3L), new ReviewedBatch(11L, 3L)), "operator-7"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
        .isEqualTo(403);
    assertStillAnUnconfirmedDraft(tuk75);
    assertStillAnUnconfirmedDraft(tuv100);
  }

  @Test
  void giveFirstConfirmation_recordsItThroughTheConfirmationsAndReturnsTheDraft() {
    TransactionBatch batch = batch(10L, DRAFT);
    given(batchRepository.findById(10L)).willReturn(Optional.of(batch));

    TransactionBatchResponse response = service.giveFirstConfirmation(10L, 3L, "operator-1");

    then(batchConfirmations).should().giveFirst(batch, 3L, "operator-1");
    assertThat(response.status()).isEqualTo(DRAFT);
  }

  @Test
  void giveFirstConfirmation_sentBatch_throwsConflict() {
    given(batchRepository.findById(10L)).willReturn(Optional.of(batch(10L, BatchStatus.SENT)));

    assertThatThrownBy(() -> service.giveFirstConfirmation(10L, 3L, "operator-1"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
        .isEqualTo(409);
    then(batchConfirmations).shouldHaveNoInteractions();
  }

  @Test
  void giveFirstConfirmation_unknownBatch_throwsNotFound() {
    given(batchRepository.findById(999L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.giveFirstConfirmation(999L, 3L, "operator-1"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
        .isEqualTo(404);
  }

  @Test
  void confirmAndSend_alreadySentBatch_throwsConflict() {
    given(batchRepository.findById(10L)).willReturn(Optional.of(batch(10L, BatchStatus.SENT)));

    assertThatThrownBy(
            () -> service.confirmAndSend(List.of(new ReviewedBatch(10L, 3L)), "operator-7"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
        .isEqualTo(409);
    then(batchFinalizer).shouldHaveNoInteractions();
  }

  @Test
  void cancelBatch_cancelsSentBatchOrdersAndWritesAudit() {
    TransactionBatch batch = batch(10L, BatchStatus.SENT);
    TransactionOrder firstOrder = order(100L, batch);
    firstOrder.setOrderStatus(OrderStatus.SENT);
    TransactionOrder secondOrder = order(101L, batch);
    secondOrder.setOrderStatus(OrderStatus.SENT);
    given(batchRepository.findById(10L)).willReturn(Optional.of(batch));
    given(orderRepository.findByBatchId(10L)).willReturn(List.of(firstOrder, secondOrder));

    TransactionBatchResponse response = service.cancelBatch(10L, "duplicate batch", "operator-7");

    assertThat(response.status()).isEqualTo(BatchStatus.CANCELLED);
    assertThat(batch.getStatus()).isEqualTo(BatchStatus.CANCELLED);
    assertThat(batch.getCancellationReason()).isEqualTo("duplicate batch");
    assertThat(batch.getCancelledBy()).isEqualTo("operator-7");
    assertThat(batch.getCancelledAt()).isEqualTo(Instant.parse("2026-06-11T09:00:00Z"));
    assertThat(firstOrder.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(secondOrder.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);

    then(batchRepository).should().save(batch);
    then(orderRepository).should().saveAll(List.of(firstOrder, secondOrder));
    then(batchRepository).should(never()).delete(any());
    then(orderRepository).should(never()).deleteAll(any());
    then(auditEventRepository)
        .should()
        .save(
            TransactionAuditEvent.builder()
                .batch(batch)
                .eventType("BATCH_CANCELLED")
                .actor("operator-7")
                .createdAt(Instant.parse("2026-06-11T09:00:00Z"))
                .payload(
                    Map.of("reason", "duplicate batch", "actor", "operator-7", "orderCount", 2))
                .build());
  }

  @Test
  void cancelBatch_draftBatch_throwsConflictAndMutatesNothing() {
    TransactionBatch batch = batch(10L, DRAFT);
    given(batchRepository.findById(10L)).willReturn(Optional.of(batch));

    assertThatThrownBy(() -> service.cancelBatch(10L, "duplicate batch", "operator-7"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
        .isEqualTo(409);

    assertThat(batch.getStatus()).isEqualTo(DRAFT);
    then(orderRepository).shouldHaveNoInteractions();
    then(batchRepository).should(never()).save(any());
    then(auditEventRepository).shouldHaveNoInteractions();
  }

  @Test
  void cancelBatch_withExecutedOrder_throwsConflictAndMutatesNothing() {
    TransactionBatch batch = batch(10L, BatchStatus.SENT);
    TransactionOrder executedOrder = order(100L, batch);
    executedOrder.setOrderStatus(OrderStatus.EXECUTED);
    given(batchRepository.findById(10L)).willReturn(Optional.of(batch));
    given(orderRepository.findByBatchId(10L)).willReturn(List.of(executedOrder));

    assertThatThrownBy(() -> service.cancelBatch(10L, "duplicate batch", "operator-7"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
        .isEqualTo(409);

    assertThat(batch.getStatus()).isEqualTo(BatchStatus.SENT);
    assertThat(executedOrder.getOrderStatus()).isEqualTo(OrderStatus.EXECUTED);
    then(batchRepository).should(never()).save(any());
    then(orderRepository).should(never()).saveAll(any());
    then(auditEventRepository).shouldHaveNoInteractions();
  }

  @Test
  void cancelBatch_unknownBatch_throwsNotFound() {
    given(batchRepository.findById(999L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.cancelBatch(999L, "duplicate batch", "admin"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
        .isEqualTo(404);
  }

  @Test
  void discardBatch_draftBatch_setsBatchAndOrdersDiscardedAndWritesAudit() {
    TransactionBatch batch = batch(10L, DRAFT);
    TransactionOrder order = order(100L, batch);
    order.setOrderStatus(OrderStatus.DRAFT);
    given(batchRepository.findById(10L)).willReturn(Optional.of(batch));
    given(orderRepository.findByBatchId(10L)).willReturn(List.of(order));

    TransactionBatchResponse response = service.discardBatch(10L, "operator-7");

    assertThat(response.status()).isEqualTo(BatchStatus.DISCARDED);
    assertThat(batch.getStatus()).isEqualTo(BatchStatus.DISCARDED);
    assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.DISCARDED);

    then(batchRepository).should().saveAndFlush(batch);
    then(orderRepository).should().saveAll(List.of(order));
    then(auditEventRepository)
        .should()
        .save(
            TransactionAuditEvent.builder()
                .batch(batch)
                .eventType("BATCH_DISCARDED")
                .actor("operator-7")
                .createdAt(Instant.parse("2026-06-11T09:00:00Z"))
                .payload(Map.of("actor", "operator-7", "orderCount", 1))
                .build());
  }

  @Test
  void discardBatch_nonDraftBatch_throwsConflictAndMutatesNothing() {
    TransactionBatch batch = batch(10L, CONFIRMED);
    given(batchRepository.findById(10L)).willReturn(Optional.of(batch));

    assertThatThrownBy(() -> service.discardBatch(10L, "operator-7"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
        .isEqualTo(409);

    assertThat(batch.getStatus()).isEqualTo(BatchStatus.CONFIRMED);
    then(batchRepository).should(never()).save(any());
    then(orderRepository).shouldHaveNoInteractions();
    then(auditEventRepository).shouldHaveNoInteractions();
  }

  @Test
  void discardBatch_unknownBatch_throwsNotFound() {
    given(batchRepository.findById(999L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.discardBatch(999L, "admin"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
        .isEqualTo(404);
  }

  @Test
  void cancelOrder_sentOrder_setsCancelledAndWritesOrderScopedAudit() {
    TransactionOrder order = order(100L, batch(10L, BatchStatus.SENT));
    order.setOrderStatus(OrderStatus.SENT);
    given(orderRepository.findById(100L)).willReturn(Optional.of(order));

    TransactionOrderResponse response = service.cancelOrder(100L, "trader error", "operator-7");

    assertThat(response.orderStatus()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);

    then(orderRepository).should().save(order);
    then(auditEventRepository)
        .should()
        .save(
            TransactionAuditEvent.builder()
                .orderId(100L)
                .eventType("ORDER_CANCELLED")
                .actor("operator-7")
                .createdAt(Instant.parse("2026-06-11T09:00:00Z"))
                .payload(Map.of("reason", "trader error", "actor", "operator-7"))
                .build());
  }

  @Test
  void cancelOrder_executedOrder_throwsConflictAndMutatesNothing() {
    TransactionOrder order = order(100L, batch(10L, BatchStatus.SENT));
    order.setOrderStatus(OrderStatus.EXECUTED);
    given(orderRepository.findById(100L)).willReturn(Optional.of(order));

    assertThatThrownBy(() -> service.cancelOrder(100L, "trader error", "operator-7"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
        .isEqualTo(409);

    assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.EXECUTED);
    then(orderRepository).should(never()).save(any());
    then(auditEventRepository).shouldHaveNoInteractions();
  }

  @Test
  void cancelOrder_settledOrder_throwsConflictAndMutatesNothing() {
    TransactionOrder order = order(100L, batch(10L, BatchStatus.SENT));
    order.setOrderStatus(OrderStatus.SETTLED);
    given(orderRepository.findById(100L)).willReturn(Optional.of(order));

    assertThatThrownBy(() -> service.cancelOrder(100L, "trader error", "operator-7"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
        .isEqualTo(409);

    assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.SETTLED);
    then(orderRepository).should(never()).save(any());
    then(auditEventRepository).shouldHaveNoInteractions();
  }

  @Test
  void cancelOrder_draftOrder_throwsConflictAndMutatesNothing() {
    TransactionOrder order = order(100L, batch(10L, DRAFT));
    order.setOrderStatus(OrderStatus.DRAFT);
    given(orderRepository.findById(100L)).willReturn(Optional.of(order));

    assertThatThrownBy(() -> service.cancelOrder(100L, "trader error", "operator-7"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
        .isEqualTo(409);

    assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.DRAFT);
    then(orderRepository).should(never()).save(any());
    then(auditEventRepository).shouldHaveNoInteractions();
  }

  @Test
  void cancelOrder_unknownOrder_throwsNotFound() {
    given(orderRepository.findById(999L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.cancelOrder(999L, "trader error", "admin"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
        .isEqualTo(404);
  }

  @Test
  void setOrderType_draftOrder_updatesTypeAndWritesOrderScopedAudit() {
    TransactionOrder order = order(100L, batch(10L, DRAFT));
    order.setOrderStatus(OrderStatus.DRAFT);
    given(orderRepository.findById(100L)).willReturn(Optional.of(order));

    TransactionOrderResponse response = service.setOrderType(100L, OrderType.NAV, "operator-7");

    assertThat(response.orderType()).isEqualTo(OrderType.NAV);
    assertThat(order.getOrderType()).isEqualTo(OrderType.NAV);

    then(orderRepository).should().save(order);
    then(auditEventRepository)
        .should()
        .save(
            TransactionAuditEvent.builder()
                .orderId(100L)
                .eventType("ORDER_TYPE_CHANGED")
                .actor("operator-7")
                .createdAt(Instant.parse("2026-06-11T09:00:00Z"))
                .payload(Map.of("from", "MOC", "to", "NAV", "actor", "operator-7"))
                .build());
  }

  @Test
  void setOrderType_firstLocksTheBatchSoAConcurrentConfirmationConflicts() {
    TransactionOrder order = order(100L, batch(10L, DRAFT));
    order.setOrderStatus(OrderStatus.DRAFT);
    given(orderRepository.findById(100L)).willReturn(Optional.of(order));

    service.setOrderType(100L, OrderType.NAV, "operator-7");

    then(batchConfirmations).should().lockForOrderTypeChange(order.getBatch());
  }

  @Test
  void setOrderType_batchChangedSinceTheOrderWasRead_throwsConflictAndMutatesNothing() {
    TransactionOrder order = order(100L, batch(10L, DRAFT));
    order.setOrderStatus(OrderStatus.DRAFT);
    given(orderRepository.findById(100L)).willReturn(Optional.of(order));
    willThrow(new ResponseStatusException(CONFLICT, "Batch was modified concurrently"))
        .given(batchConfirmations)
        .lockForOrderTypeChange(order.getBatch());

    assertThatThrownBy(() -> service.setOrderType(100L, OrderType.NAV, "operator-7"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
        .isEqualTo(409);
    assertThat(order.getOrderType()).isEqualTo(OrderType.MOC);
    then(orderRepository).should(never()).save(any());
    then(auditEventRepository).should(never()).save(any());
  }

  @Test
  void setOrderType_sentOrder_throwsConflictAndMutatesNothing() {
    TransactionOrder order = order(100L, batch(10L, BatchStatus.SENT));
    order.setOrderStatus(OrderStatus.SENT);
    given(orderRepository.findById(100L)).willReturn(Optional.of(order));

    assertThatThrownBy(() -> service.setOrderType(100L, OrderType.NAV, "operator-7"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
        .isEqualTo(409);

    assertThat(order.getOrderType()).isEqualTo(OrderType.MOC);
    then(orderRepository).should(never()).save(any());
    then(auditEventRepository).shouldHaveNoInteractions();
  }

  @Test
  void setOrderType_unknownOrder_throwsNotFound() {
    given(orderRepository.findById(999L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.setOrderType(999L, OrderType.NAV, "admin"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
        .isEqualTo(404);
  }

  @Test
  void exportFile_decodesStoredBase64Export() {
    byte[] xlsx = {1, 2, 3};
    TransactionBatch batch = batch(10L, BatchStatus.SENT);
    batch.setMetadata(Map.of("sebEtfXlsx", Base64.getEncoder().encodeToString(xlsx)));
    given(batchRepository.findById(10L)).willReturn(Optional.of(batch));

    assertThat(service.exportFile(10L, "sebEtfXlsx")).contains(xlsx);
  }

  @Test
  void exportFile_decodesStoredUuidWorkbookExport() {
    byte[] xlsx = {4, 5, 6};
    TransactionBatch batch = batch(10L, BatchStatus.SENT);
    batch.setMetadata(Map.of("uuidWorkbookXlsx", Base64.getEncoder().encodeToString(xlsx)));
    given(batchRepository.findById(10L)).willReturn(Optional.of(batch));

    assertThat(service.exportFile(10L, "uuidWorkbookXlsx")).contains(xlsx);
  }

  @Test
  void exportFile_missingExport_returnsEmpty() {
    given(batchRepository.findById(10L)).willReturn(Optional.of(batch(10L, BatchStatus.SENT)));

    assertThat(service.exportFile(10L, "sebEtfXlsx")).isEmpty();
  }

  @Test
  void exportFile_unknownExportType_returnsEmptyWithoutTouchingRepository() {
    assertThat(service.exportFile(10L, "commandId")).isEmpty();
    assertThat(service.exportFile(10L, "../etc/passwd")).isEmpty();
    then(batchRepository).shouldHaveNoInteractions();
  }

  @Test
  void dailySummary_aggregatesUnsettledOrdersAndLatestBatchPerFund() {
    TransactionBatch latestBatch = batch(10L, DRAFT);
    TransactionOrder unsettled = order(100L, latestBatch);
    unsettled.setOrderStatus(OrderStatus.SENT);
    given(orderRepository.findUnsettledOrders(TUK75, TODAY)).willReturn(List.of(unsettled));
    given(batchRepository.findFirstByFundOrderByCreatedAtDesc(TUK75))
        .willReturn(Optional.of(latestBatch));

    TransactionDailySummary summary = service.dailySummary();

    assertThat(summary.date()).isEqualTo(TODAY);
    assertThat(summary.funds())
        .contains(
            new TransactionDailySummary.FundSummary(
                TUK75, 1, new BigDecimal("1000.00"), 10L, DRAFT, latestBatch.getCreatedAt()));
    assertThat(summary.funds()).hasSize(TulevaFund.values().length);
  }

  @Test
  void dailySummary_fundWithoutActivity_hasZeroCountsAndNoBatch() {
    TransactionDailySummary summary = service.dailySummary();

    assertThat(summary.funds())
        .contains(
            new TransactionDailySummary.FundSummary(TUK75, 0, BigDecimal.ZERO, null, null, null));
  }
}
