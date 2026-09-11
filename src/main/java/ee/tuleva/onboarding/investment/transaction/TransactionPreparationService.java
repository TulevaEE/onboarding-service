package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.investment.epis.SettlementTimingWarningService;
import ee.tuleva.onboarding.investment.transaction.calculation.TradeCalculationEngine;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
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
  private final Clock clock;

  @Transactional
  public @Nullable ProcessCommandResult processCommand(TransactionCommand command) {
    @Nullable FundTransactionInput input = null;
    @Nullable TransactionBatch batch = null;
    try {
      log.info(
          "Processing command: id={}, fund={}, mode={}",
          command.getId(),
          command.getFund(),
          command.getMode());

      input = gatherInput(command);

      var result = calculationEngine.calculate(input, command.getMode());

      batch =
          TransactionBatch.builder()
              .fund(command.getFund())
              .status(BatchStatus.DRAFT)
              .createdBy(actorOf(command))
              .createdAt(Instant.now(clock))
              .metadata(Map.of("commandId", command.getId(), "mode", command.getMode().name()))
              .build();
      batchRepository.save(batch);

      CalculatedOrders calculated =
          orderFactory.createOrders(batch, result, command.getAsOfDate(), Instant.now(clock));
      List<TransactionOrder> orders = calculated.orders();
      orderRepository.saveAll(orders);

      auditEventRepository.save(
          TransactionAuditEvent.builder()
              .batch(batch)
              .eventType("CALCULATION_COMPLETED")
              .actor(actorOf(command))
              .createdAt(Instant.now(clock))
              .payload(completedPayload(command, input, result, orders, calculated))
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
      log.error("Command processing failed: id={}", command.getId(), e);
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
              .payload(failedPayload(command, input, e))
              .build());
      return null;
    }
  }

  private FundTransactionInput gatherInput(TransactionCommand command) {
    BigDecimal cash = command.getCash();
    return cash == null
        ? inputService.gatherInput(
            command.getFund(), command.getAsOfDate(), command.getManualAdjustments())
        : inputService.gatherInput(
            command.getFund(), command.getAsOfDate(), command.getManualAdjustments(), cash);
  }

  private static String actorOf(TransactionCommand command) {
    return command.getActor() == null ? "system" : command.getActor();
  }

  private Map<String, Object> failedPayload(
      TransactionCommand command, @Nullable FundTransactionInput input, RuntimeException e) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("fund", command.getFund().name());
    payload.put("asOfDate", command.getAsOfDate().toString());
    payload.put("manualAdjustments", Map.copyOf(command.getManualAdjustments()));
    if (input != null) {
      payload.put(
          "input", TransactionInputPayloads.serializeInput(input, command.getManualAdjustments()));
    }
    payload.put("exceptionClass", e.getClass().getName());
    putIfPresent(payload, "errorMessage", command.getErrorMessage());
    return payload;
  }

  private Map<String, Object> completedPayload(
      TransactionCommand command,
      FundTransactionInput input,
      FundCalculationResult result,
      List<TransactionOrder> orders,
      CalculatedOrders calculated) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put(
        "input", TransactionInputPayloads.serializeInput(input, command.getManualAdjustments()));
    payload.put(
        "output",
        TransactionAuditPayloads.serializeTrades(
            result.trades(), ordersByIsin(orders), calculated.priceResolutions()));
    payload.put(
        "priceResolutions",
        TransactionAuditPayloads.serializePriceResolutions(calculated.priceResolutions()));
    payload.put(
        "settlementWarnings",
        TransactionAuditPayloads.serializeSettlementWarnings(
            settlementTimingWarningService.activeWarnings(
                command.getFund(), command.getAsOfDate())));
    payload.put(
        "calculationWarnings",
        TransactionAuditPayloads.serializeCalculationWarnings(result.warnings()));
    payload.put("modelDrift", TransactionAuditPayloads.serializeModelDrift(input, result));
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("fund", command.getFund().name());
    summary.put("mode", command.getMode().name());
    summary.put("tradeCount", orders.size());
    putIfPresent(summary, "netInvestable", plain(result.netInvestable()));
    putIfPresent(summary, "noTradeReason", result.noTradeReason());
    payload.put("summary", summary);
    return payload;
  }

  private static Map<String, TransactionOrder> ordersByIsin(List<TransactionOrder> orders) {
    Map<String, TransactionOrder> map = new LinkedHashMap<>();
    orders.forEach(order -> map.put(order.getInstrumentIsin(), order));
    return map;
  }

  private static void putIfPresent(Map<String, Object> map, String key, @Nullable Object value) {
    if (value != null) {
      map.put(key, value);
    }
  }

  @Nullable
  private static String plain(@Nullable BigDecimal value) {
    return value == null ? null : value.toPlainString();
  }
}
