package ee.tuleva.onboarding.investment.transaction;

import static org.springframework.transaction.annotation.Isolation.REPEATABLE_READ;

import ee.tuleva.onboarding.investment.epis.SettlementTimingWarningService;
import ee.tuleva.onboarding.investment.transaction.calculation.TradeCalculationEngine;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionPreparationService {

  private final TransactionInputService inputService;
  private final TradeCalculationEngine calculationEngine;
  private final TransactionBatchRepository batchRepository;
  private final TransactionOrderRepository orderRepository;
  private final TransactionAuditEventRepository auditEventRepository;
  private final TransactionCommandRepository commandRepository;
  private final TransactionOrderFactory orderFactory;
  private final SettlementTimingWarningService settlementTimingWarningService;
  private final TradePrepInputGates inputGates;
  private final Clock clock;

  @Transactional(isolation = REPEATABLE_READ)
  public CommandOutcome processCommand(TransactionCommand command) {
    @Nullable FundTransactionInput input = null;
    @Nullable TransactionBatch batch = null;
    try {
      log.info(
          "Processing command: id={}, fund={}, mode={}",
          command.getId(),
          command.getFund(),
          command.getMode());

      GateCheck gates = inputGates.verify(command);
      input = gatherInput(command, gates.pricing());
      input = input.excluding(command.getExcludeIsins());

      var result = calculationEngine.calculate(input, command.getMode());

      TransactionBatch draft =
          TransactionBatch.builder()
              .fund(command.getFund())
              .status(BatchStatus.DRAFT)
              .createdBy(actorOf(command))
              .createdAt(Instant.now(clock))
              .metadata(Map.of("commandId", command.getId(), "mode", command.getMode().name()))
              .build();
      CalculatedOrders calculated =
          orderFactory.createOrders(
              draft, result, command.getAsOfDate(), Instant.now(clock), gates.pricing());
      calculated.requireSellable(command.getFund(), command.getAsOfDate());
      batchRepository.save(draft);
      batch = draft;
      List<TransactionOrder> orders = calculated.orders();
      orderRepository.saveAll(orders);

      auditEventRepository.save(
          TransactionAuditEvent.builder()
              .batch(batch)
              .eventType("CALCULATION_COMPLETED")
              .actor(actorOf(command))
              .createdAt(Instant.now(clock))
              .payload(
                  TransactionCommandPayloads.completed(
                      command,
                      gates,
                      input,
                      result,
                      calculated,
                      TransactionAuditPayloads.serializeSettlementWarnings(
                          settlementTimingWarningService.activeWarnings(command.getFund()))))
              .build());

      command.setStatus(CommandStatus.CALCULATED);
      command.setBatchId(batch.getId());
      command.setProcessedAt(Instant.now(clock));
      commandRepository.save(command);

      log.info(
          "Command processed: id={}, batchId={}, orderCount={}",
          command.getId(),
          batch.getId(),
          orders.size());

      return new ProcessCommandResult(batch, orders);

    } catch (RuntimeException e) {
      CommandOutcome outcome = loggedOutcomeOf(command, e);
      markFailed(command, input, batch, e);
      return outcome;
    }
  }

  private static CommandOutcome loggedOutcomeOf(TransactionCommand command, RuntimeException e) {
    if (e instanceof TradePrepInputGateException stopped) {
      log.warn("Command stopped by input gates: id={}, reason={}", command.getId(), e.getMessage());
      return stopped.stop();
    }
    log.error("Command processing failed: id={}", command.getId(), e);
    return new CommandOutcome.Failed();
  }

  private void markFailed(
      TransactionCommand command,
      @Nullable FundTransactionInput input,
      @Nullable TransactionBatch batch,
      RuntimeException e) {
    command.setStatus(CommandStatus.FAILED);
    command.setErrorMessage(e.getMessage());
    command.setProcessedAt(Instant.now(clock));
    commandRepository.save(command);
    auditEventRepository.save(
        TransactionAuditEvent.builder()
            .batch(batch)
            .eventType("CALCULATION_FAILED")
            .dedupKey(String.valueOf(command.getId()))
            .actor(actorOf(command))
            .createdAt(Instant.now(clock))
            .payload(TransactionCommandPayloads.failed(command, input, e))
            .build());
  }

  private FundTransactionInput gatherInput(TransactionCommand command, TradePrepPricing pricing) {
    BigDecimal cash = command.getCash();
    if (cash == null) {
      return inputService.gatherInput(
          command.getFund(), command.getAsOfDate(), command.getManualAdjustments(), pricing);
    }
    String comment = command.getCashComment();
    if (comment == null || comment.isBlank()) {
      throw new IllegalStateException(
          "A cash figure needs a comment saying where it comes from: commandId=" + command.getId());
    }
    return inputService.gatherInput(
        command.getFund(), command.getAsOfDate(), command.getManualAdjustments(), cash, pricing);
  }

  private static String actorOf(TransactionCommand command) {
    return command.getActor() == null ? "system" : command.getActor();
  }
}
