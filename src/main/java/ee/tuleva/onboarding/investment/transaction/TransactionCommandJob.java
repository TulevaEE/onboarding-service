package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.JobRunSchedule.*;

import ee.tuleva.onboarding.investment.event.RunTransactionCommandRequested;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
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
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  @Scheduled(cron = TRANSACTION_COMMAND, zone = TIMEZONE)
  @SchedulerLock(name = "TransactionCommandJob", lockAtMostFor = "5m", lockAtLeastFor = "30s")
  public void schedule() {
    eventPublisher.publishEvent(new RunTransactionCommandRequested());
  }

  @EventListener
  public void onTransactionCommandRequested(RunTransactionCommandRequested event) {
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
        command.setStatus(CommandStatus.FAILED);
        command.setErrorMessage(e.getMessage());
        command.setProcessedAt(Instant.now(clock));
        commandRepository.save(command);
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
