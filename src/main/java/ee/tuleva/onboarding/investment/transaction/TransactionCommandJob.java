package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.JobRunSchedule.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@Profile({"production", "staging"})
public class TransactionCommandJob {

  private final TransactionCommandRepository commandRepository;
  private final TransactionBatchRepository batchRepository;
  private final TransactionPreparationService preparationService;

  @Scheduled(cron = TRANSACTION_COMMAND, zone = TIMEZONE)
  @SchedulerLock(name = "TransactionCommandJob", lockAtMostFor = "5m", lockAtLeastFor = "30s")
  public void run() {
    processCommands();
    finalizeConfirmedBatches();
  }

  void processCommands() {
    var commands = commandRepository.findByStatus(CommandStatus.PENDING);
    if (commands.isEmpty()) {
      return;
    }

    log.info("Found pending commands: count={}", commands.size());

    for (var command : commands) {
      command.setStatus(CommandStatus.PROCESSING);
      commandRepository.save(command);
      try {
        preparationService.processCommand(command);
      } catch (Exception e) {
        log.error("Unexpected error processing command: id={}", command.getId(), e);
      }
    }
  }

  void finalizeConfirmedBatches() {
    var batches = batchRepository.findByStatus(BatchStatus.CONFIRMED);
    if (batches.isEmpty()) {
      return;
    }

    log.info("Found confirmed batches: count={}", batches.size());

    for (var batch : batches) {
      preparationService.finalizeConfirmedBatch(batch);
    }
  }
}
