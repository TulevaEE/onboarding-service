package ee.tuleva.onboarding.investment.transaction;

import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.transaction.calculation.TradeCalculationEngine;
import ee.tuleva.onboarding.investment.transaction.export.CustodianOrderEmailSender;
import ee.tuleva.onboarding.investment.transaction.export.GoogleDriveProperties;
import ee.tuleva.onboarding.investment.transaction.export.TransactionExportService;
import ee.tuleva.onboarding.investment.transaction.export.TransactionExportUploader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionPreparationService {

  private static final int STALE_PRICE_THRESHOLD_DAYS = 3;

  private final TransactionInputService inputService;
  private final TradeCalculationEngine calculationEngine;
  private final TransactionBatchRepository batchRepository;
  private final TransactionOrderRepository orderRepository;
  private final TransactionAuditEventRepository auditEventRepository;
  private final TransactionCommandRepository commandRepository;
  private final SettlementDateCalculator settlementDateCalculator;
  private final TransactionExportService exportService;
  private final ModelPortfolioAllocationRepository modelPortfolioAllocationRepository;
  private final PositionPriceResolver positionPriceResolver;
  private final ApplicationEventPublisher eventPublisher;
  private final GoogleDriveProperties driveProperties;
  @Nullable private final TransactionExportUploader exportUploader;
  private final CustodianOrderEmailSender custodianOrderEmailSender;
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

      List<TransactionOrder> orders =
          createOrders(batch, result, command.getAsOfDate(), Instant.now(clock));
      orderRepository.saveAll(orders);

      auditEventRepository.save(
          TransactionAuditEvent.builder()
              .batch(batch)
              .eventType("CALCULATION_COMPLETED")
              .actor("system")
              .createdAt(Instant.now(clock))
              .payload(
                  Map.of(
                      "input", serializeInput(input, command.getManualAdjustments()),
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

    } catch (RuntimeException e) {
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
    if (batch.getStatus() == BatchStatus.SENT) {
      throw new IllegalStateException(
          "Batch already finalized: id=" + batch.getId() + ", status=" + batch.getStatus());
    }
    log.info("Finalizing batch: id={}", batch.getId());

    Instant now = Instant.now(clock);
    LocalDate tradeDate = now.atZone(clock.getZone()).toLocalDate();

    List<TransactionOrder> orders = orderRepository.findByBatchId(batch.getId());
    requireQuantitiesForNonAmountOrders(batch, orders);
    orders.forEach(
        order -> {
          order.setOrderTimestamp(now);
          order.setExpectedSettlementDate(
              settlementDateCalculator.calculateSettlementDate(
                  tradeDate, order.getInstrumentType(), order.getInstrumentIsin()));
          order.setOrderStatus(OrderStatus.SENT);
        });
    orderRepository.saveAll(orders);

    List<ModelPortfolioAllocation> currentAllocations =
        modelPortfolioAllocationRepository.findLatestByFundAsOf(batch.getFund(), tradeDate);
    List<ModelPortfolioAllocation> previousAllocations =
        modelPortfolioAllocationRepository.findPreviousByFundAsOf(batch.getFund(), tradeDate);
    var mergedAllocations = new ArrayList<>(previousAllocations);
    mergedAllocations.addAll(currentAllocations);
    Map<String, String> labelsByIsin =
        buildLookupMap(mergedAllocations, ModelPortfolioAllocation::getLabel);
    Map<String, String> ricByIsin =
        buildLookupMap(mergedAllocations, ModelPortfolioAllocation::getTicker);
    Map<String, String> bbgByIsin =
        buildLookupMap(mergedAllocations, ModelPortfolioAllocation::getBbgTicker);

    byte[] xlsxExport = exportService.generateOrdersExport(orders);
    byte[] sebFundXlsx = exportService.generateSebFundExport(orders, labelsByIsin);
    byte[] sebEtfXlsx = exportService.generateSebEtfExport(orders, ricByIsin);
    byte[] ftEtfXlsx = exportService.generateFtEtfExport(orders, labelsByIsin, bbgByIsin);

    Map<String, Object> updatedMetadata = new HashMap<>(batch.getMetadata());
    updatedMetadata.put("xlsxExport", encodeExport(xlsxExport));
    updatedMetadata.put("sebFundXlsx", encodeExport(sebFundXlsx));
    updatedMetadata.put("sebEtfXlsx", encodeExport(sebEtfXlsx));
    updatedMetadata.put("ftEtfXlsx", encodeExport(ftEtfXlsx));
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

    runAfterCommit(
        () ->
            publishExportsToDrive(
                batch, now, tradeDate, orders.size(), sebFundXlsx, sebEtfXlsx, ftEtfXlsx));

    log.info("Batch finalized: id={}, orderCount={}", batch.getId(), orders.size());
  }

  private void publishExportsToDrive(
      TransactionBatch batch,
      Instant timestamp,
      LocalDate tradeDate,
      int orderCount,
      byte[] sebFundXlsx,
      byte[] sebEtfXlsx,
      byte[] ftEtfXlsx) {
    var exports =
        Map.of("sebFundXlsx", sebFundXlsx, "sebEtfXlsx", sebEtfXlsx, "ftEtfXlsx", ftEtfXlsx);
    Map<String, String> driveFileUrls = uploadExportsToDrive(batch, timestamp, exports);
    if (!driveFileUrls.isEmpty()) {
      persistDriveFileUrls(batch, driveFileUrls);
    }
    custodianOrderEmailSender.send(batch.getFund(), timestamp, exports);
    eventPublisher.publishEvent(
        new BatchFinalizedEvent(batch.getId(), orderCount, tradeDate.toString(), driveFileUrls));
  }

  void persistDriveFileUrls(TransactionBatch batch, Map<String, String> driveFileUrls) {
    Map<String, Object> updatedMetadata = new HashMap<>(batch.getMetadata());
    updatedMetadata.put("driveFileUrls", driveFileUrls);
    batch.setMetadata(updatedMetadata);
    batchRepository.save(batch);
  }

  private void runAfterCommit(Runnable action) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              action.run();
            }
          });
    } else {
      action.run();
    }
  }

  private List<TransactionOrder> createOrders(
      TransactionBatch batch, FundCalculationResult result, LocalDate asOfDate, Instant createdAt) {
    var input = result.input();

    return result.trades().stream()
        .filter(trade -> trade.tradeAmount().compareTo(ZERO) != 0)
        .map(
            trade -> {
              var instrumentType =
                  input.instrumentTypes().getOrDefault(trade.isin(), InstrumentType.ETF);
              var transactionType =
                  trade.tradeAmount().compareTo(ZERO) > 0
                      ? TransactionType.BUY
                      : TransactionType.SELL;
              var orderAmount = trade.tradeAmount().abs();
              var orderQuantity =
                  resolveOrderQuantity(
                      instrumentType, transactionType, trade.isin(), orderAmount, asOfDate);
              return TransactionOrder.builder()
                  .batch(batch)
                  .fund(result.fund())
                  .instrumentIsin(trade.isin())
                  .transactionType(transactionType)
                  .instrumentType(instrumentType)
                  .orderAmount(orderAmount)
                  .orderQuantity(orderQuantity.quantity())
                  .comment(orderQuantity.stalePriceComment())
                  .orderVenue(input.orderVenues().getOrDefault(trade.isin(), OrderVenue.SEB))
                  .createdAt(createdAt)
                  .build();
            })
        .toList();
  }

  private record OrderQuantity(@Nullable BigDecimal quantity, @Nullable String stalePriceComment) {}

  private OrderQuantity resolveOrderQuantity(
      InstrumentType instrumentType,
      TransactionType transactionType,
      String isin,
      BigDecimal orderAmount,
      LocalDate asOfDate) {
    if (isAmountBasedOrder(instrumentType, transactionType)) {
      return new OrderQuantity(null, null);
    }
    ResolvedPrice resolvedPrice = positionPriceResolver.resolve(isin, asOfDate).orElse(null);
    BigDecimal price = resolvedPrice == null ? null : resolvedPrice.usedPrice();
    if (price == null) {
      log.warn(
          "No price found for order quantity: isin={}, instrumentType={}, asOfDate={}",
          isin,
          instrumentType,
          asOfDate);
      return new OrderQuantity(null, null);
    }
    if (price.signum() <= 0) {
      log.warn(
          "Non-positive price for order quantity, leaving quantity unset:"
              + " isin={}, instrumentType={}, price={}, asOfDate={}",
          isin,
          instrumentType,
          price.toPlainString(),
          asOfDate);
      return new OrderQuantity(null, null);
    }
    BigDecimal quantity = orderAmount.divide(price, 6, RoundingMode.HALF_UP);
    String stalePriceComment = describeIfStale(isin, resolvedPrice, asOfDate);
    return new OrderQuantity(quantity, stalePriceComment);
  }

  @Nullable
  private String describeIfStale(String isin, ResolvedPrice resolvedPrice, LocalDate asOfDate) {
    LocalDate priceDate = resolvedPrice.priceDate();
    if (priceDate == null) {
      return null;
    }
    long ageDays = ChronoUnit.DAYS.between(priceDate, asOfDate);
    if (ageDays <= STALE_PRICE_THRESHOLD_DAYS) {
      return null;
    }
    log.warn(
        "Order sized on stale price: isin={}, priceDate={}, ageDays={}, source={}, asOfDate={}",
        isin,
        priceDate,
        ageDays,
        resolvedPrice.priceSource(),
        asOfDate);
    return "Sized on stale price: priceDate=%s, ageDays=%d, source=%s"
        .formatted(priceDate, ageDays, resolvedPrice.priceSource());
  }

  private boolean isAmountBasedOrder(
      InstrumentType instrumentType, TransactionType transactionType) {
    return instrumentType == InstrumentType.FUND && transactionType == TransactionType.BUY;
  }

  private void requireQuantitiesForNonAmountOrders(
      TransactionBatch batch, List<TransactionOrder> orders) {
    List<String> missingQuantity =
        orders.stream()
            .filter(
                order -> !isAmountBasedOrder(order.getInstrumentType(), order.getTransactionType()))
            .filter(order -> order.getOrderQuantity() == null)
            .map(TransactionOrder::getInstrumentIsin)
            .toList();
    if (!missingQuantity.isEmpty()) {
      throw new IllegalStateException(
          "Cannot finalize batch: orders require a quantity but have none: batchId="
              + batch.getId()
              + ", isins="
              + missingQuantity);
    }
  }

  static Map<String, Object> serializeInput(
      FundTransactionInput input, Map<String, Object> manualAdjustments) {
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
        Map.entry("fastSellIsins", List.copyOf(input.fastSellIsins())),
        Map.entry("manualAdjustments", Map.copyOf(manualAdjustments)));
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
      TransactionBatch batch, Instant timestamp, Map<String, byte[]> exports) {
    if (!driveProperties.enabled() || exportUploader == null) {
      return Map.of();
    }
    try {
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
