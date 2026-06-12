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
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;

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

    TransactionBatchResponse response = service.confirmAndFinalize(10L);

    then(preparationService).should().finalizeConfirmedBatch(batch);
    assertThat(response.status()).isEqualTo(BatchStatus.SENT);
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

    service.confirmAndFinalize(10L);

    then(preparationService).should().finalizeConfirmedBatch(batch);
  }

  @Test
  void confirmAndFinalize_unknownBatch_throwsNotFound() {
    given(batchRepository.findById(999L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.confirmAndFinalize(999L))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
        .isEqualTo(404);
  }

  @Test
  void confirmAndFinalize_alreadySentBatch_throwsConflict() {
    given(batchRepository.findById(10L)).willReturn(Optional.of(batch(10L, BatchStatus.SENT)));

    assertThatThrownBy(() -> service.confirmAndFinalize(10L))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
        .isEqualTo(409);
    then(preparationService).shouldHaveNoInteractions();
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
