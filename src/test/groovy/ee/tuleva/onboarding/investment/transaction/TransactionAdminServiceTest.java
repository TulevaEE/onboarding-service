package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.transaction.BatchStatus.CONFIRMED;
import static ee.tuleva.onboarding.investment.transaction.BatchStatus.DRAFT;
import static ee.tuleva.onboarding.investment.transaction.CommandStatus.CALCULATED;
import static ee.tuleva.onboarding.investment.transaction.CommandStatus.FAILED;
import static ee.tuleva.onboarding.investment.transaction.CommandStatus.PROCESSING;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.TransactionMode.REBALANCE;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;

import ee.tuleva.onboarding.fund.TulevaFund;
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
  @Mock private TransactionPreparationService preparationService;

  @InjectMocks private TransactionAdminService service;

  private static TransactionBatch batch(Long id, BatchStatus status) {
    return TransactionBatch.builder()
        .id(id)
        .fund(TUK75)
        .status(status)
        .createdBy("system")
        .createdAt(Instant.parse("2026-06-10T09:00:00Z"))
        .metadata(Map.of())
        .build();
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
  void createAndProcess_persistsProcessingCommandAndProcessesItSynchronously() {
    TransactionBatch batch = batch(10L, DRAFT);
    TransactionOrder order = order(100L, batch);
    willAnswer(
            invocation -> {
              TransactionCommand command = invocation.getArgument(0);
              assertThat(command.getStatus()).isEqualTo(PROCESSING);
              assertThat(command.getManualAdjustments())
                  .isEqualTo(Map.of("IE00BFG1TM61", "1000.00"));
              command.setStatus(CALCULATED);
              command.setBatchId(10L);
              return new ProcessCommandResult(batch, List.of(order));
            })
        .given(preparationService)
        .processCommand(any());

    TransactionCommandResponse response =
        service.createAndProcess(
            TUK75, REBALANCE, AS_OF_DATE, Map.of("IE00BFG1TM61", "1000.00"), "operator-9");

    then(commandRepository)
        .should()
        .save(
            TransactionCommand.builder()
                .fund(TUK75)
                .mode(REBALANCE)
                .asOfDate(AS_OF_DATE)
                .manualAdjustments(Map.of("IE00BFG1TM61", "1000.00"))
                .actor("operator-9")
                .status(CALCULATED)
                .batchId(10L)
                .build());
    assertThat(response.fund()).isEqualTo(TUK75);
    assertThat(response.status()).isEqualTo(CALCULATED);
    assertThat(response.batchId()).isEqualTo(10L);
    assertThat(response.orders()).containsExactly(TransactionOrderResponse.from(order));
  }

  @Test
  void createAndProcess_withNullAdjustments_defaultsToEmptyMap() {
    given(preparationService.processCommand(any()))
        .willReturn(new ProcessCommandResult(batch(10L, DRAFT), List.of()));

    service.createAndProcess(TUK75, REBALANCE, AS_OF_DATE, null, "admin");

    then(commandRepository)
        .should()
        .save(
            TransactionCommand.builder()
                .fund(TUK75)
                .mode(REBALANCE)
                .asOfDate(AS_OF_DATE)
                .manualAdjustments(Map.of())
                .actor("admin")
                .status(PROCESSING)
                .build());
  }

  @Test
  void createAndProcess_failedProcessingReturnsFailedCommandWithoutOrders() {
    willAnswer(
            invocation -> {
              TransactionCommand command = invocation.getArgument(0);
              command.setStatus(FAILED);
              command.setErrorMessage("No positions found");
              return null;
            })
        .given(preparationService)
        .processCommand(any());

    TransactionCommandResponse response =
        service.createAndProcess(TUK75, REBALANCE, AS_OF_DATE, null, "admin");

    assertThat(response.status()).isEqualTo(FAILED);
    assertThat(response.errorMessage()).isEqualTo("No positions found");
    assertThat(response.orders()).isEmpty();
  }

  @Test
  void createAndProcessAll_withoutFunds_processesEveryFund() {
    given(preparationService.processCommand(any()))
        .willReturn(new ProcessCommandResult(batch(10L, DRAFT), List.of()));

    List<TransactionCommandResponse> responses =
        service.createAndProcessAll(null, REBALANCE, AS_OF_DATE, "admin");

    assertThat(responses)
        .extracting(TransactionCommandResponse::fund)
        .containsExactly(TulevaFund.values());
  }

  @Test
  void createAndProcessAll_withFunds_processesOnlyThoseFunds() {
    given(preparationService.processCommand(any()))
        .willReturn(new ProcessCommandResult(batch(10L, DRAFT), List.of()));

    List<TransactionCommandResponse> responses =
        service.createAndProcessAll(List.of(TUK75), REBALANCE, AS_OF_DATE, "admin");

    assertThat(responses).extracting(TransactionCommandResponse::fund).containsExactly(TUK75);
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

    assertThat(response).contains(TransactionCommandResponse.from(command, List.of(order)));
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

    assertThat(response).contains(TransactionCommandResponse.from(command, List.of()));
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

    assertThat(response).contains(TransactionBatchResponse.from(batch, List.of(order)));
  }

  @Test
  void confirmAndFinalize_confirmsAwaitingBatchThroughExistingFinalizePath() {
    TransactionBatch batch = batch(10L, DRAFT);
    given(batchRepository.findById(10L)).willReturn(Optional.of(batch));
    willAnswer(
            invocation -> {
              TransactionBatch finalized = invocation.getArgument(0);
              finalized.setStatus(BatchStatus.SENT);
              return null;
            })
        .given(preparationService)
        .finalizeConfirmedBatch(batch);
    given(orderRepository.findByBatchId(10L)).willReturn(List.of());

    TransactionBatchResponse response = service.confirmAndFinalize(10L, "operator-7");

    then(preparationService).should().finalizeConfirmedBatch(batch);
    assertThat(response.status()).isEqualTo(BatchStatus.SENT);
    assertThat(batch.getConfirmedBy()).isEqualTo("operator-7");
    assertThat(batch.getConfirmedAt()).isEqualTo(Instant.parse("2026-06-11T09:00:00Z"));
  }

  @Test
  void confirmAndFinalize_passesConfirmedStatusToFinalize() {
    TransactionBatch batch = batch(10L, DRAFT);
    given(batchRepository.findById(10L)).willReturn(Optional.of(batch));
    willAnswer(
            invocation -> {
              TransactionBatch confirmed = invocation.getArgument(0);
              assertThat(confirmed.getStatus()).isEqualTo(CONFIRMED);
              return null;
            })
        .given(preparationService)
        .finalizeConfirmedBatch(batch);
    given(orderRepository.findByBatchId(10L)).willReturn(List.of());

    service.confirmAndFinalize(10L, "admin");

    then(preparationService).should().finalizeConfirmedBatch(batch);
  }

  @Test
  void confirmAndFinalize_unknownBatch_throwsNotFound() {
    given(batchRepository.findById(999L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.confirmAndFinalize(999L, "admin"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
        .isEqualTo(404);
  }

  @Test
  void confirmAndFinalize_concurrentlyModifiedBatch_throwsConflictAndNeverFinalizes() {
    TransactionBatch batch = batch(10L, DRAFT);
    given(batchRepository.findById(10L)).willReturn(Optional.of(batch));
    willThrow(new ObjectOptimisticLockingFailureException(TransactionBatch.class, 10L))
        .given(batchRepository)
        .saveAndFlush(batch);

    assertThatThrownBy(() -> service.confirmAndFinalize(10L, "admin"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
        .isEqualTo(409);
    then(preparationService).should(never()).finalizeConfirmedBatch(any());
  }

  @Test
  void confirmAndFinalize_alreadySentBatch_throwsConflict() {
    given(batchRepository.findById(10L)).willReturn(Optional.of(batch(10L, BatchStatus.SENT)));

    assertThatThrownBy(() -> service.confirmAndFinalize(10L, "admin"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
        .isEqualTo(409);
    then(preparationService).shouldHaveNoInteractions();
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

    then(batchRepository).should().save(batch);
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
