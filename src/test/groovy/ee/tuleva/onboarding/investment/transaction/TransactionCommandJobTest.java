package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.BatchStatus.CONFIRMED;
import static ee.tuleva.onboarding.investment.transaction.CommandStatus.FAILED;
import static ee.tuleva.onboarding.investment.transaction.CommandStatus.PENDING;
import static ee.tuleva.onboarding.investment.transaction.CommandStatus.PROCESSING;
import static ee.tuleva.onboarding.investment.transaction.TransactionMode.BUY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class TransactionCommandJobTest {

  @Mock private TransactionCommandRepository commandRepository;
  @Mock private TransactionBatchRepository batchRepository;
  @Mock private TransactionPreparationService preparationService;
  @Mock private TransactionBatchFinalizer batchFinalizer;
  @Mock private TradePrepInputGateNotifier inputGateNotifier;
  @Mock private ApplicationEventPublisher eventPublisher;

  private final Clock clock =
      Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneId.of("Europe/Tallinn"));

  private TransactionCommandJob job;

  @BeforeEach
  void setUp() {
    job =
        new TransactionCommandJob(
            commandRepository,
            batchRepository,
            preparationService,
            batchFinalizer,
            inputGateNotifier,
            eventPublisher,
            clock);
  }

  @Test
  void processCommands_postsTheGateStopsOfTheWholeRunAfterProcessingEveryCommand() {
    var tuk75 = pending(1L, TUK75);
    var tuk00 = pending(2L, TUK00);
    var tuv100 = pending(3L, TUV100);
    var tuk75Stop =
        new CommandOutcome.StoppedByInputGates(
            TUK75, AS_OF_DATE, List.of(new InputGateFailure.NoR45Loaded(AS_OF_DATE)));
    var tuk00Stop =
        new CommandOutcome.StoppedByInputGates(
            TUK00, AS_OF_DATE, List.of(new InputGateFailure.NoR45Loaded(AS_OF_DATE)));
    when(commandRepository.findByStatus(PENDING)).thenReturn(List.of(tuk75, tuk00, tuv100));
    when(preparationService.processCommand(tuk75)).thenReturn(tuk75Stop);
    when(preparationService.processCommand(tuk00)).thenReturn(tuk00Stop);
    when(preparationService.processCommand(tuv100)).thenReturn(new CommandOutcome.Failed());

    job.processCommands();

    var inOrder = inOrder(preparationService, inputGateNotifier);
    inOrder.verify(preparationService).processCommand(tuv100);
    inOrder.verify(inputGateNotifier).notifyStopped(List.of(tuk75Stop, tuk00Stop));
  }

  @Test
  void processCommands_stillPostsTheStopsAlreadyMadeWhenALaterCommandCannotBeSaved() {
    var tuk75 = pending(1L, TUK75);
    var tuk00 = pending(2L, TUK00);
    var tuk75Stop =
        new CommandOutcome.StoppedByInputGates(
            TUK75, AS_OF_DATE, List.of(new InputGateFailure.NoR45Loaded(AS_OF_DATE)));
    when(commandRepository.findByStatus(PENDING)).thenReturn(List.of(tuk75, tuk00));
    when(preparationService.processCommand(tuk75)).thenReturn(tuk75Stop);
    when(commandRepository.save(any()))
        .thenAnswer(
            invocation -> {
              if (invocation.getArgument(0) == tuk00) {
                throw new IllegalStateException("Database unavailable");
              }
              return invocation.getArgument(0);
            });

    assertThatThrownBy(job::processCommands).isInstanceOf(IllegalStateException.class);

    verify(inputGateNotifier).notifyStopped(List.of(tuk75Stop));
  }

  private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 11, 18);

  private static TransactionCommand pending(Long id, TulevaFund fund) {
    return TransactionCommand.builder()
        .id(id)
        .fund(fund)
        .mode(BUY)
        .asOfDate(AS_OF_DATE)
        .manualAdjustments(Map.of())
        .status(PENDING)
        .build();
  }

  @Test
  void processCommands_picksPendingCommandsAndProcesses() {
    var command =
        TransactionCommand.builder()
            .id(1L)
            .fund(TUV100)
            .mode(BUY)
            .asOfDate(LocalDate.of(2026, 1, 15))
            .manualAdjustments(Map.of())
            .status(PENDING)
            .build();

    when(commandRepository.findByStatus(PENDING)).thenReturn(List.of(command));

    job.processCommands();

    assertThat(command.getStatus()).isEqualTo(PROCESSING);
    verify(commandRepository).save(command);
    verify(preparationService).processCommand(command);
  }

  @Test
  void processCommands_withNoCommands_doesNothing() {
    when(commandRepository.findByStatus(PENDING)).thenReturn(List.of());

    job.processCommands();

    verifyNoInteractions(preparationService);
  }

  @Test
  void finalizeConfirmedBatches_picksConfirmedBatches() {
    var batch = TransactionBatch.builder().status(CONFIRMED).createdBy("analyst").build();
    batch.onCreate();

    when(batchRepository.findByStatus(CONFIRMED)).thenReturn(List.of(batch));

    job.finalizeConfirmedBatches();

    verify(batchFinalizer).finalizeConfirmedBatch(batch);
  }

  @Test
  void finalizeConfirmedBatches_anOutdatedBatchRefused_stillSendsTheBatchesAfterIt() {
    var outdated = TransactionBatch.builder().id(1L).status(CONFIRMED).createdBy("analyst").build();
    var current = TransactionBatch.builder().id(2L).status(CONFIRMED).createdBy("analyst").build();
    when(batchRepository.findByStatus(CONFIRMED)).thenReturn(List.of(outdated, current));
    doThrow(new TransactionBatchRefusedException("prepared on an earlier day"))
        .when(batchFinalizer)
        .finalizeConfirmedBatch(outdated);

    job.finalizeConfirmedBatches();

    verify(batchFinalizer).finalizeConfirmedBatch(current);
  }

  @Test
  void finalizeConfirmedBatches_withNoBatches_doesNothing() {
    when(batchRepository.findByStatus(CONFIRMED)).thenReturn(List.of());

    job.finalizeConfirmedBatches();

    verifyNoInteractions(batchFinalizer);
  }

  @Test
  void processCommands_continuesProcessingAfterUnexpectedException() {
    var command1 =
        TransactionCommand.builder()
            .id(1L)
            .fund(TUV100)
            .mode(BUY)
            .asOfDate(LocalDate.of(2026, 1, 15))
            .manualAdjustments(Map.of())
            .status(PENDING)
            .build();
    var command2 =
        TransactionCommand.builder()
            .id(2L)
            .fund(TUV100)
            .mode(BUY)
            .asOfDate(LocalDate.of(2026, 1, 15))
            .manualAdjustments(Map.of())
            .status(PENDING)
            .build();

    when(commandRepository.findByStatus(PENDING)).thenReturn(List.of(command1, command2));
    doThrow(new RuntimeException("Unexpected")).when(preparationService).processCommand(command1);

    job.processCommands();

    verify(preparationService).processCommand(command1);
    verify(preparationService).processCommand(command2);
  }

  @Test
  void processCommands_onUnexpectedException_marksCommandFailed() {
    var command =
        TransactionCommand.builder()
            .id(1L)
            .fund(TUV100)
            .mode(BUY)
            .asOfDate(LocalDate.of(2026, 1, 15))
            .manualAdjustments(Map.of())
            .status(PENDING)
            .build();

    when(commandRepository.findByStatus(PENDING)).thenReturn(List.of(command));
    doThrow(new RuntimeException("Boom")).when(preparationService).processCommand(command);

    job.processCommands();

    assertThat(command.getStatus()).isEqualTo(FAILED);
    assertThat(command.getErrorMessage()).isEqualTo("Boom");
    assertThat(command.getProcessedAt()).isEqualTo(Instant.parse("2026-01-15T10:00:00Z"));
  }

  @Test
  void eventListenerProcessesCommandsAndFinalizesBatches() {
    when(commandRepository.findByStatus(PENDING)).thenReturn(List.of());
    when(batchRepository.findByStatus(CONFIRMED)).thenReturn(List.of());

    job.onTransactionCommandRequested();

    verify(commandRepository).findByStatus(PENDING);
    verify(batchRepository).findByStatus(CONFIRMED);
  }
}
