package ee.tuleva.onboarding.investment.transaction;

import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.transaction.calculation.TradeCalculationEngine;
import ee.tuleva.onboarding.investment.transaction.export.GoogleDriveProperties;
import ee.tuleva.onboarding.investment.transaction.export.TransactionExportService;
import ee.tuleva.onboarding.investment.transaction.export.TransactionExportUploader;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;
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
  private final SettlementDateCalculator settlementDateCalculator;
  private final TransactionExportService exportService;
  private final ModelPortfolioAllocationRepository modelPortfolioAllocationRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final GoogleDriveProperties driveProperties;
  @Nullable private final TransactionExportUploader exportUploader;
  private final Clock clock;

  @Transactional
  public ProcessCommandResult processCommand(TransactionCommand command) {
    try {
      log.info(
          "Processing command: id={}, fund={}, mode={}",
          command.getId(),
          command.getFund(),
          command.getMode());

      var input =
          inputService.gatherInput(
              command.getFund(), command.getAsOfDate(), command.getManualAdjustments());

      var result = calculationEngine.calculate(input, command.getMode());

      var batch =
          TransactionBatch.builder()
              .fund(command.getFund())
              .status(BatchStatus.AWAITING_CONFIRMATION)
              .createdBy("system")
              .createdAt(Instant.now(clock))
              .metadata(Map.of("commandId", command.getId(), "mode", command.getMode().name()))
              .build();
      batchRepository.save(batch);

      List<TransactionOrder> orders = createOrders(batch, result, Instant.now(clock));
      orderRepository.saveAll(orders);

      auditEventRepository.save(
          TransactionAuditEvent.builder()
              .batch(batch)
              .eventType("CALCULATION_COMPLETED")
              .actor("system")
              .createdAt(Instant.now(clock))
              .payload(
                  Map.of(
                      "input", serializeInput(input),
                      "output", serializeTrades(result.trades()),
                      "summary",
                          Map.of(
                              "fund", command.getFund().name(),
                              "mode", command.getMode().name(),
                              "tradeCount", orders.size())))
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

    } catch (IllegalStateException | IllegalArgumentException e) {
      log.error("Command processing failed: id={}", command.getId(), e);
      command.setStatus(CommandStatus.FAILED);
      command.setErrorMessage(e.getMessage());
      command.setProcessedAt(Instant.now(clock));
      commandRepository.save(command);
      return null;
    }
  }

  @Transactional
  public void finalizeConfirmedBatch(TransactionBatch batch) {
    log.info("Finalizing batch: id={}", batch.getId());

    Instant now = Instant.now(clock);
    LocalDate tradeDate = now.atZone(clock.getZone()).toLocalDate();

    List<TransactionOrder> orders = orderRepository.findByBatchId(batch.getId());
    orders.forEach(
        order -> {
          order.setOrderTimestamp(now);
          order.setExpectedSettlementDate(
              settlementDateCalculator.calculateSettlementDate(
                  tradeDate, order.getInstrumentType()));
          order.setOrderStatus(OrderStatus.SENT);
        });
    orderRepository.saveAll(orders);

    List<ModelPortfolioAllocation> allocations =
        modelPortfolioAllocationRepository.findLatestByFund(batch.getFund());
    Map<String, String> labelsByIsin =
        buildLookupMap(allocations, ModelPortfolioAllocation::getLabel);
    Map<String, String> ricByIsin =
        buildLookupMap(allocations, ModelPortfolioAllocation::getTicker);
    Map<String, String> bbgByIsin =
        buildLookupMap(allocations, ModelPortfolioAllocation::getBbgTicker);

    byte[] xlsxExport = exportService.generateOrdersExport(orders);
    byte[] sebFundXlsx = exportService.generateSebFundExport(orders, labelsByIsin);
    byte[] sebEtfXlsx = exportService.generateSebEtfExport(orders, ricByIsin);
    byte[] ftEtfXlsx = exportService.generateFtEtfExport(orders, labelsByIsin, bbgByIsin);

    Map<String, Object> updatedMetadata = new HashMap<>(batch.getMetadata());
    updatedMetadata.put("xlsxExport", encodeExport(xlsxExport));
    updatedMetadata.put("sebFundXlsx", encodeExport(sebFundXlsx));
    updatedMetadata.put("sebEtfXlsx", encodeExport(sebEtfXlsx));
    updatedMetadata.put("ftEtfXlsx", encodeExport(ftEtfXlsx));

    Map<String, String> driveFileUrls =
        uploadExportsToDrive(batch, now, sebFundXlsx, sebEtfXlsx, ftEtfXlsx);
    if (!driveFileUrls.isEmpty()) {
      updatedMetadata.put("driveFileUrls", driveFileUrls);
    }
    batch.setMetadata(updatedMetadata);

    batch.setStatus(BatchStatus.SENT);
    batchRepository.save(batch);

    auditEventRepository.save(
        TransactionAuditEvent.builder()
            .batch(batch)
            .eventType("BATCH_FINALIZED")
            .actor("system")
            .createdAt(Instant.now(clock))
            .payload(Map.of("tradeDate", tradeDate.toString(), "orderCount", orders.size()))
            .build());

    eventPublisher.publishEvent(
        new BatchFinalizedEvent(batch.getId(), orders.size(), tradeDate.toString(), driveFileUrls));

    log.info("Batch finalized: id={}, orderCount={}", batch.getId(), orders.size());
  }

  private List<TransactionOrder> createOrders(
      TransactionBatch batch, FundCalculationResult result, Instant createdAt) {
    var input = result.input();

    return result.trades().stream()
        .filter(trade -> trade.tradeAmount().compareTo(ZERO) != 0)
        .map(
            trade ->
                TransactionOrder.builder()
                    .batch(batch)
                    .fund(result.fund())
                    .instrumentIsin(trade.isin())
                    .transactionType(
                        trade.tradeAmount().compareTo(ZERO) > 0
                            ? TransactionType.BUY
                            : TransactionType.SELL)
                    .instrumentType(
                        input.instrumentTypes().getOrDefault(trade.isin(), InstrumentType.ETF))
                    .orderAmount(trade.tradeAmount().abs())
                    .orderVenue(input.orderVenues().getOrDefault(trade.isin(), OrderVenue.SEB))
                    .createdAt(createdAt)
                    .build())
        .toList();
  }

  static Map<String, Object> serializeInput(FundTransactionInput input) {
    return Map.ofEntries(
        Map.entry(
            "positions",
            input.positions().stream()
                .map(
                    position ->
                        Map.of(
                            "isin", position.isin(),
                            "marketValue", position.marketValue().toPlainString()))
                .toList()),
        Map.entry(
            "modelWeights",
            input.modelWeights().stream()
                .map(
                    weight ->
                        Map.of("isin", weight.isin(), "weight", weight.weight().toPlainString()))
                .toList()),
        Map.entry("grossPortfolioValue", input.grossPortfolioValue().toPlainString()),
        Map.entry("cashBuffer", input.cashBuffer().toPlainString()),
        Map.entry("liabilities", input.liabilities().toPlainString()),
        Map.entry("receivables", input.receivables().toPlainString()),
        Map.entry("freeCash", input.freeCash().toPlainString()),
        Map.entry("minTransactionThreshold", input.minTransactionThreshold().toPlainString()),
        Map.entry(
            "positionLimits",
            input.positionLimits().entrySet().stream()
                .collect(
                    toMap(
                        Map.Entry::getKey,
                        entry ->
                            Map.of(
                                "softLimit", entry.getValue().softLimit().toPlainString(),
                                "hardLimit", entry.getValue().hardLimit().toPlainString())))),
        Map.entry("fastSellIsins", List.copyOf(input.fastSellIsins())));
  }

  static List<Map<String, Object>> serializeTrades(List<TradeCalculation> trades) {
    return trades.stream()
        .map(
            trade ->
                Map.<String, Object>of(
                    "isin", trade.isin(),
                    "tradeAmount", trade.tradeAmount().toPlainString(),
                    "projectedWeight", trade.projectedWeight().toPlainString(),
                    "limitStatus", trade.limitStatus().name()))
        .toList();
  }

  private Map<String, String> buildLookupMap(
      List<ModelPortfolioAllocation> allocations,
      Function<ModelPortfolioAllocation, String> valueExtractor) {
    return allocations.stream()
        .filter(
            allocation -> allocation.getIsin() != null && valueExtractor.apply(allocation) != null)
        .collect(toMap(ModelPortfolioAllocation::getIsin, valueExtractor, (a, b) -> b));
  }

  private Map<String, String> uploadExportsToDrive(
      TransactionBatch batch,
      Instant timestamp,
      byte[] sebFundXlsx,
      byte[] sebEtfXlsx,
      byte[] ftEtfXlsx) {
    if (!driveProperties.enabled() || exportUploader == null) {
      return Map.of();
    }
    try {
      var exports =
          Map.of("sebFundXlsx", sebFundXlsx, "sebEtfXlsx", sebEtfXlsx, "ftEtfXlsx", ftEtfXlsx);
      return exportUploader.uploadExports(
          driveProperties.rootFolderId(), batch.getFund(), timestamp, exports);
    } catch (Exception e) {
      log.error("Google Drive upload failed: batchId={}", batch.getId(), e);
      return Map.of();
    }
  }

  private String encodeExport(byte[] export) {
    return Base64.getEncoder().encodeToString(export);
  }
}
