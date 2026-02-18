package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static ee.tuleva.onboarding.investment.transaction.BatchStatus.CONFIRMED;
import static ee.tuleva.onboarding.investment.transaction.CommandStatus.PENDING;
import static ee.tuleva.onboarding.investment.transaction.CommandStatus.PROCESSING;
import static ee.tuleva.onboarding.investment.transaction.TransactionMode.BUY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionCommandJobTest {

  @Mock private TransactionCommandRepository commandRepository;
  @Mock private TransactionBatchRepository batchRepository;
  @Mock private TransactionPreparationService preparationService;

  @InjectMocks private TransactionCommandJob job;

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

    verify(preparationService).finalizeConfirmedBatch(batch);
  }

  @Test
  void finalizeConfirmedBatches_withNoBatches_doesNothing() {
    when(batchRepository.findByStatus(CONFIRMED)).thenReturn(List.of());

    job.finalizeConfirmedBatches();

    verifyNoInteractions(preparationService);
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
  void run_processesCommandsAndFinalizesBatches() {
    when(commandRepository.findByStatus(PENDING)).thenReturn(List.of());
    when(batchRepository.findByStatus(CONFIRMED)).thenReturn(List.of());

    job.run();

    verify(commandRepository).findByStatus(PENDING);
    verify(batchRepository).findByStatus(CONFIRMED);
  }
}
