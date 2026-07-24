package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;
import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.investment.epis.SettlementTimingWarning;
import ee.tuleva.onboarding.investment.epis.SettlementTimingWarningService;
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
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
  private static final ZoneId TALLINN = ZoneId.of(TIMEZONE);

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
  private final SettlementTimingWarningService settlementTimingWarningService;
  private final Clock clock;

  @Transactional
  public ProcessCommandResult processCommand(TransactionCommand command) {
    @Nullable FundTransactionInput input = null;
    @Nullable TransactionBatch batch = null;
    try {
      log.info(
          "Processing command: id={}, fund={}, mode={}",
          command.getId(),
          command.getFund(),
          command.getMode());

      input =
          inputService.gatherInput(
              command.getFund(), command.getAsOfDate(), command.getManualAdjustments());

      var result = calculationEngine.calculate(input, command.getMode());

      batch =
          TransactionBatch.builder()
              .fund(command.getFund())
              .status(BatchStatus.AWAITING_CONFIRMATION)
              .createdBy(actorOf(command))
              .createdAt(Instant.now(clock))
              .metadata(Map.of("commandId", command.getId(), "mode", command.getMode().name()))
              .build();
      batchRepository.save(batch);

      CalculatedOrders calculated =
          createOrders(batch, result, command.getAsOfDate(), Instant.now(clock));
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
      payload.put("input", serializeInput(input, command.getManualAdjustments()));
    }
    payload.put("exceptionClass", e.getClass().getName());
    putIfPresent(payload, "errorMessage", command.getErrorMessage());
    return payload;
  }

  @Transactional
  public void finalizeConfirmedBatch(TransactionBatch batch) {
    if (batch.getStatus() == BatchStatus.SENT) {
      throw new IllegalStateException(
          "Batch already finalized: id=" + batch.getId() + ", status=" + batch.getStatus());
    }
    log.info("Finalizing batch: id={}", batch.getId());

    Instant now = Instant.now(clock);
    LocalDate tradeDate = now.atZone(TALLINN).toLocalDate();

    List<TransactionOrder> orders = orderRepository.findByBatchId(batch.getId());
    requireQuantitiesForNonAmountOrders(batch, orders);
    orders.forEach(
        order -> {
          order.setOrderTimestamp(now);
          order.setExpectedSettlementDate(
              settlementDateCalculator.calculateSettlementDate(
                  now, order.getInstrumentType(), order.getInstrumentIsin()));
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
    byte[] ftEtfXlsx =
        exportService.generateFtEtfExport(orders, labelsByIsin, bbgByIsin, tradeDate);
    byte[] uuidWorkbookXlsx = exportService.generateUuidWorkbook(orders);

    Map<String, Object> updatedMetadata = new HashMap<>(batch.getMetadata());
    updatedMetadata.put("xlsxExport", encodeExport(xlsxExport));
    updatedMetadata.put("sebFundXlsx", encodeExport(sebFundXlsx));
    updatedMetadata.put("sebEtfXlsx", encodeExport(sebEtfXlsx));
    updatedMetadata.put("ftEtfXlsx", encodeExport(ftEtfXlsx));
    updatedMetadata.put("uuidWorkbookXlsx", encodeExport(uuidWorkbookXlsx));
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
                batch,
                now,
                tradeDate,
                orders.size(),
                sebFundXlsx,
                sebEtfXlsx,
                ftEtfXlsx,
                uuidWorkbookXlsx));

    log.info("Batch finalized: id={}, orderCount={}", batch.getId(), orders.size());
  }

  private void publishExportsToDrive(
      TransactionBatch batch,
      Instant timestamp,
      LocalDate tradeDate,
      int orderCount,
      byte[] sebFundXlsx,
      byte[] sebEtfXlsx,
      byte[] ftEtfXlsx,
      byte[] uuidWorkbookXlsx) {
    var exports =
        Map.of(
            "sebFundXlsx", sebFundXlsx,
            "sebEtfXlsx", sebEtfXlsx,
            "ftEtfXlsx", ftEtfXlsx,
            "uuidWorkbookXlsx", uuidWorkbookXlsx);
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

  private CalculatedOrders createOrders(
      TransactionBatch batch, FundCalculationResult result, LocalDate asOfDate, Instant createdAt) {
    var input = result.input();
    List<TransactionOrder> orders = new ArrayList<>();
    Map<String, ResolvedPrice> priceResolutions = new LinkedHashMap<>();

    for (TradeCalculation trade : result.trades()) {
      if (trade.tradeAmount().compareTo(ZERO) == 0) {
        continue;
      }
      var instrumentType = input.instrumentTypes().getOrDefault(trade.isin(), InstrumentType.ETF);
      var transactionType =
          trade.tradeAmount().compareTo(ZERO) > 0 ? TransactionType.BUY : TransactionType.SELL;
      var orderAmount = trade.tradeAmount().abs();
      var orderQuantity =
          resolveOrderQuantity(
              instrumentType, transactionType, trade.isin(), orderAmount, asOfDate);
      if (!isAmountBasedOrder(instrumentType, transactionType)) {
        priceResolutions.put(trade.isin(), orderQuantity.resolvedPrice());
      }
      orders.add(
          TransactionOrder.builder()
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
              .build());
    }
    return new CalculatedOrders(orders, priceResolutions);
  }

  private record CalculatedOrders(
      List<TransactionOrder> orders, Map<String, ResolvedPrice> priceResolutions) {}

  private record OrderQuantity(
      @Nullable BigDecimal quantity,
      @Nullable String stalePriceComment,
      @Nullable ResolvedPrice resolvedPrice) {}

  private OrderQuantity resolveOrderQuantity(
      InstrumentType instrumentType,
      TransactionType transactionType,
      String isin,
      BigDecimal orderAmount,
      LocalDate asOfDate) {
    if (isAmountBasedOrder(instrumentType, transactionType)) {
      return new OrderQuantity(null, null, null);
    }
    ResolvedPrice resolvedPrice = positionPriceResolver.resolve(isin, asOfDate).orElse(null);
    BigDecimal price = resolvedPrice == null ? null : resolvedPrice.usedPrice();
    if (price == null) {
      log.warn(
          "No price found for order quantity: isin={}, instrumentType={}, asOfDate={}",
          isin,
          instrumentType,
          asOfDate);
      return new OrderQuantity(null, null, resolvedPrice);
    }
    if (price.signum() <= 0) {
      log.warn(
          "Non-positive price for order quantity, leaving quantity unset:"
              + " isin={}, instrumentType={}, price={}, asOfDate={}",
          isin,
          instrumentType,
          price.toPlainString(),
          asOfDate);
      return new OrderQuantity(null, null, resolvedPrice);
    }
    BigDecimal quantity = orderAmount.divide(price, 6, RoundingMode.HALF_UP);
    String stalePriceComment = describeIfStale(isin, resolvedPrice, asOfDate);
    return new OrderQuantity(quantity, stalePriceComment, resolvedPrice);
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
    Map<String, Object> result = new LinkedHashMap<>();
    putIfPresent(
        result,
        "positions",
        input.positions().stream()
            .map(position -> serializePosition(position, input.grossPortfolioValue()))
            .toList());
    putIfPresent(
        result,
        "modelWeights",
        input.modelWeights().stream()
            .map(TransactionPreparationService::serializeModelWeight)
            .toList());
    putIfPresent(result, "grossPortfolioValue", plain(input.grossPortfolioValue()));
    putIfPresent(result, "cashBuffer", plain(input.cashBuffer()));
    putIfPresent(result, "liabilities", plain(input.liabilities()));
    putIfPresent(result, "receivables", plain(input.receivables()));
    putIfPresent(result, "freeCash", plain(input.freeCash()));
    putIfPresent(result, "minTransactionThreshold", plain(input.minTransactionThreshold()));
    putIfPresent(
        result,
        "positionDate",
        input.positionDate() == null ? null : input.positionDate().toString());
    putIfPresent(
        result,
        "modelEffectiveDate",
        input.modelEffectiveDate() == null ? null : input.modelEffectiveDate().toString());
    putIfPresent(
        result, "liabilityBreakdown", serializeLiabilityBreakdown(input.liabilityBreakdown()));
    putIfPresent(result, "reportCash", plain(input.reportCash()));
    putIfPresent(result, "ledgerCash", plain(input.ledgerCash()));
    putIfPresent(result, "cashDifference", plain(cashDifference(input)));
    putIfPresent(result, "positionLimits", serializePositionLimits(input.positionLimits()));
    putIfPresent(result, "fastSellIsins", List.copyOf(input.fastSellIsins()));
    putIfPresent(result, "manualAdjustments", Map.copyOf(manualAdjustments));
    return result;
  }

  @Nullable
  private static Map<String, Object> serializeLiabilityBreakdown(
      @Nullable LiabilityBreakdown breakdown) {
    if (breakdown == null) {
      return null;
    }
    Map<String, Object> map = new LinkedHashMap<>();
    putIfPresent(map, "managementFee", plain(breakdown.managementFee()));
    putIfPresent(map, "depotFee", plain(breakdown.depotFee()));
    putIfPresent(map, "pevaRava", plain(breakdown.pevaRava()));
    putIfPresent(map, "r16", plain(breakdown.r16()));
    putIfPresent(map, "r45Net", plain(breakdown.r45Net()));
    putIfPresent(map, "pendingBuys", plain(breakdown.pendingBuys()));
    putIfPresent(map, "pendingSells", plain(breakdown.pendingSells()));
    putIfPresent(map, "unreconciledBankReceipts", plain(breakdown.unreconciledBankReceipts()));
    putIfPresent(map, "fundUnitsReservedValue", plain(breakdown.fundUnitsReservedValue()));
    putIfPresent(map, "incomingPaymentsClearing", plain(breakdown.incomingPaymentsClearing()));
    return map;
  }

  @Nullable
  private static BigDecimal cashDifference(FundTransactionInput input) {
    if (input.reportCash() == null || input.ledgerCash() == null) {
      return null;
    }
    return input.reportCash().subtract(input.ledgerCash());
  }

  private static Map<String, Object> serializePosition(
      PositionSnapshot position, BigDecimal grossPortfolioValue) {
    Map<String, Object> map = new LinkedHashMap<>();
    putIfPresent(map, "isin", position.isin());
    putIfPresent(map, "marketValue", plain(position.marketValue()));
    putIfPresent(map, "quantity", plain(position.quantity()));
    putIfPresent(map, "unitPrice", plain(position.unitPrice()));
    putIfPresent(map, "currentWeight", plain(currentWeight(position, grossPortfolioValue)));
    return map;
  }

  @Nullable
  private static BigDecimal currentWeight(
      PositionSnapshot position, BigDecimal grossPortfolioValue) {
    if (grossPortfolioValue.signum() == 0 || position.marketValue() == null) {
      return null;
    }
    return position.marketValue().divide(grossPortfolioValue, 6, RoundingMode.HALF_UP);
  }

  private static Map<String, Object> serializeModelWeight(ModelWeight weight) {
    Map<String, Object> map = new LinkedHashMap<>();
    putIfPresent(map, "isin", weight.isin());
    putIfPresent(map, "weight", plain(weight.weight()));
    return map;
  }

  private static Map<String, Map<String, Object>> serializePositionLimits(
      Map<String, PositionLimitSnapshot> positionLimits) {
    Map<String, Map<String, Object>> map = new LinkedHashMap<>();
    positionLimits.forEach(
        (isin, limit) -> {
          Map<String, Object> limitMap = new LinkedHashMap<>();
          putIfPresent(limitMap, "softLimit", plain(limit.softLimit()));
          putIfPresent(limitMap, "hardLimit", plain(limit.hardLimit()));
          map.put(isin, limitMap);
        });
    return map;
  }

  private Map<String, Object> completedPayload(
      TransactionCommand command,
      FundTransactionInput input,
      FundCalculationResult result,
      List<TransactionOrder> orders,
      CalculatedOrders calculated) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("input", serializeInput(input, command.getManualAdjustments()));
    payload.put("output", serializeTrades(result.trades(), ordersByIsin(orders)));
    payload.put("priceResolutions", serializePriceResolutions(calculated.priceResolutions()));
    payload.put(
        "settlementWarnings",
        serializeSettlementWarnings(
            settlementTimingWarningService.activeWarnings(
                command.getFund(), command.getAsOfDate())));
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

  static List<Map<String, Object>> serializeTrades(
      List<TradeCalculation> trades, Map<String, TransactionOrder> ordersByIsin) {
    return trades.stream()
        .map(trade -> serializeTrade(trade, ordersByIsin.get(trade.isin())))
        .toList();
  }

  private static Map<String, Object> serializeTrade(
      TradeCalculation trade, @Nullable TransactionOrder order) {
    Map<String, Object> map = new LinkedHashMap<>();
    putIfPresent(map, "isin", trade.isin());
    putIfPresent(map, "tradeAmount", plain(trade.tradeAmount()));
    putIfPresent(map, "projectedWeight", plain(trade.projectedWeight()));
    putIfPresent(
        map, "limitStatus", trade.limitStatus() == null ? null : trade.limitStatus().name());
    if (order != null) {
      putIfPresent(map, "quantity", plain(order.getOrderQuantity()));
      putIfPresent(
          map,
          "side",
          order.getTransactionType() == null ? null : order.getTransactionType().name());
      putIfPresent(
          map, "venue", order.getOrderVenue() == null ? null : order.getOrderVenue().name());
      putIfPresent(
          map,
          "instrumentType",
          order.getInstrumentType() == null ? null : order.getInstrumentType().name());
    }
    return map;
  }

  static List<Map<String, Object>> serializeSettlementWarnings(
      List<SettlementTimingWarning> warnings) {
    return warnings.stream()
        .map(TransactionPreparationService::serializeSettlementWarning)
        .toList();
  }

  private static Map<String, Object> serializeSettlementWarning(SettlementTimingWarning warning) {
    Map<String, Object> map = new LinkedHashMap<>();
    putIfPresent(map, "type", warning.type() == null ? null : warning.type().name());
    putIfPresent(map, "fund", warning.fund() == null ? null : warning.fund().name());
    putIfPresent(
        map,
        "sellSettlementDate",
        warning.sellSettlementDate() == null ? null : warning.sellSettlementDate().toString());
    putIfPresent(
        map,
        "deadlineDate",
        warning.deadlineDate() == null ? null : warning.deadlineDate().toString());
    putIfPresent(map, "message", warning.message());
    return map;
  }

  static List<Map<String, Object>> serializePriceResolutions(
      Map<String, ResolvedPrice> priceResolutions) {
    return priceResolutions.entrySet().stream()
        .map(entry -> serializePriceResolution(entry.getKey(), entry.getValue()))
        .toList();
  }

  private static Map<String, Object> serializePriceResolution(
      String isin, @Nullable ResolvedPrice resolvedPrice) {
    Map<String, Object> map = new LinkedHashMap<>();
    putIfPresent(map, "isin", isin);
    if (resolvedPrice != null) {
      putIfPresent(map, "price", plain(resolvedPrice.usedPrice()));
      putIfPresent(
          map,
          "priceDate",
          resolvedPrice.priceDate() == null ? null : resolvedPrice.priceDate().toString());
      putIfPresent(
          map,
          "priceSource",
          resolvedPrice.priceSource() == null ? null : resolvedPrice.priceSource().name());
      putIfPresent(
          map,
          "validationStatus",
          resolvedPrice.validationStatus() == null
              ? null
              : resolvedPrice.validationStatus().name());
    }
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
