package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.transaction.BatchStatus.AWAITING_CONFIRMATION;
import static ee.tuleva.onboarding.investment.transaction.BatchStatus.CONFIRMED;
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
    TransactionBatch batch = batch(10L, AWAITING_CONFIRMATION);
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
        service.createAndProcess(TUK75, REBALANCE, AS_OF_DATE, Map.of("IE00BFG1TM61", "1000.00"));

    then(commandRepository)
        .should()
        .save(
            TransactionCommand.builder()
                .fund(TUK75)
                .mode(REBALANCE)
                .asOfDate(AS_OF_DATE)
                .manualAdjustments(Map.of("IE00BFG1TM61", "1000.00"))
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
        .willReturn(new ProcessCommandResult(batch(10L, AWAITING_CONFIRMATION), List.of()));

    service.createAndProcess(TUK75, REBALANCE, AS_OF_DATE, null);

    then(commandRepository)
        .should()
        .save(
            TransactionCommand.builder()
                .fund(TUK75)
                .mode(REBALANCE)
                .asOfDate(AS_OF_DATE)
                .manualAdjustments(Map.of())
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
        service.createAndProcess(TUK75, REBALANCE, AS_OF_DATE, null);

    assertThat(response.status()).isEqualTo(FAILED);
    assertThat(response.errorMessage()).isEqualTo("No positions found");
    assertThat(response.orders()).isEmpty();
  }

  @Test
  void createAndProcessAll_withoutFunds_processesEveryFund() {
    given(preparationService.processCommand(any()))
        .willReturn(new ProcessCommandResult(batch(10L, AWAITING_CONFIRMATION), List.of()));

    List<TransactionCommandResponse> responses =
        service.createAndProcessAll(null, REBALANCE, AS_OF_DATE);

    assertThat(responses)
        .extracting(TransactionCommandResponse::fund)
        .containsExactly(TulevaFund.values());
  }

  @Test
  void createAndProcessAll_withFunds_processesOnlyThoseFunds() {
    given(preparationService.processCommand(any()))
        .willReturn(new ProcessCommandResult(batch(10L, AWAITING_CONFIRMATION), List.of()));

    List<TransactionCommandResponse> responses =
        service.createAndProcessAll(List.of(TUK75), REBALANCE, AS_OF_DATE);

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
    TransactionOrder order = order(100L, batch(10L, AWAITING_CONFIRMATION));
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
    TransactionBatch batch = batch(10L, AWAITING_CONFIRMATION);
    TransactionOrder order = order(100L, batch);
    given(batchRepository.findById(10L)).willReturn(Optional.of(batch));
    given(orderRepository.findByBatchId(10L)).willReturn(List.of(order));

    Optional<TransactionBatchResponse> response = service.getBatch(10L);

    assertThat(response).contains(TransactionBatchResponse.from(batch, List.of(order)));
  }

  @Test
  void confirmAndFinalize_confirmsAwaitingBatchThroughExistingFinalizePath() {
    TransactionBatch batch = batch(10L, AWAITING_CONFIRMATION);
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
    TransactionBatch batch = batch(10L, AWAITING_CONFIRMATION);
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
    TransactionBatch batch = batch(10L, AWAITING_CONFIRMATION);
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
  void cancelBatch_cancelsBatchAndNotYetExecutedOrdersAndWritesAudit() {
    TransactionBatch batch = batch(10L, AWAITING_CONFIRMATION);
    TransactionOrder pendingOrder = order(100L, batch);
    pendingOrder.setOrderStatus(OrderStatus.PENDING);
    TransactionOrder sentOrder = order(101L, batch);
    sentOrder.setOrderStatus(OrderStatus.SENT);
    given(batchRepository.findById(10L)).willReturn(Optional.of(batch));
    given(orderRepository.findByBatchId(10L)).willReturn(List.of(pendingOrder, sentOrder));

    TransactionBatchResponse response = service.cancelBatch(10L, "duplicate batch", "operator-7");

    assertThat(response.status()).isEqualTo(BatchStatus.CANCELLED);
    assertThat(batch.getStatus()).isEqualTo(BatchStatus.CANCELLED);
    assertThat(batch.getCancellationReason()).isEqualTo("duplicate batch");
    assertThat(batch.getCancelledBy()).isEqualTo("operator-7");
    assertThat(batch.getCancelledAt()).isEqualTo(Instant.parse("2026-06-11T09:00:00Z"));
    assertThat(pendingOrder.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(sentOrder.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);

    then(batchRepository).should().save(batch);
    then(orderRepository).should().saveAll(List.of(pendingOrder, sentOrder));
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
  void exportFile_decodesStoredBase64Export() {
    byte[] xlsx = {1, 2, 3};
    TransactionBatch batch = batch(10L, BatchStatus.SENT);
    batch.setMetadata(Map.of("sebEtfXlsx", Base64.getEncoder().encodeToString(xlsx)));
    given(batchRepository.findById(10L)).willReturn(Optional.of(batch));

    assertThat(service.exportFile(10L, "sebEtfXlsx")).contains(xlsx);
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
    TransactionBatch latestBatch = batch(10L, AWAITING_CONFIRMATION);
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
                TUK75,
                1,
                new BigDecimal("1000.00"),
                10L,
                AWAITING_CONFIRMATION,
                latestBatch.getCreatedAt()));
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
