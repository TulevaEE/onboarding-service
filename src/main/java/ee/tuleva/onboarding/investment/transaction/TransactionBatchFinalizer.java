package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;
import static java.util.stream.Collectors.toMap;
import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW;

import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.transaction.export.BrokerFileGenerator;
import ee.tuleva.onboarding.investment.transaction.export.CustodianOrderEmailSender;
import ee.tuleva.onboarding.investment.transaction.export.ExportFile;
import ee.tuleva.onboarding.investment.transaction.export.GoogleDriveProperties;
import ee.tuleva.onboarding.investment.transaction.export.OrderDay;
import ee.tuleva.onboarding.investment.transaction.export.TransactionExportService;
import ee.tuleva.onboarding.investment.transaction.export.TransactionExportUploader;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionBatchFinalizer {

  private static final ZoneId TALLINN = ZoneId.of(TIMEZONE);

  private final TransactionOrderRepository orderRepository;
  private final TransactionBatchRepository batchRepository;
  private final TransactionAuditEventRepository auditEventRepository;
  private final SettlementDateCalculator settlementDateCalculator;
  private final TransactionExportService exportService;
  private final BrokerFileGenerator brokerFileGenerator;
  private final ModelPortfolioAllocationRepository modelPortfolioAllocationRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final GoogleDriveProperties driveProperties;
  @Nullable private final TransactionExportUploader exportUploader;
  private final CustodianOrderEmailSender custodianOrderEmailSender;
  private final TransactionOrderFactory orderFactory;
  private final PlatformTransactionManager transactionManager;
  private final Clock clock;

  private static void requireUnsent(TransactionBatch batch) {
    if (batch.getStatus() == BatchStatus.SENT) {
      throw new IllegalStateException(
          "Batch already finalized: id=" + batch.getId() + ", status=" + batch.getStatus());
    }
  }

  private static void requirePreparedOn(TransactionBatch batch, LocalDate tradeDate) {
    Instant createdAt = batch.getCreatedAt();
    LocalDate preparedOn = createdAt == null ? null : createdAt.atZone(TALLINN).toLocalDate();
    if (!tradeDate.equals(preparedOn)) {
      throw new TransactionBatchRefusedException(
          ("A batch is sent only on the day it was prepared, prepare it again: batchId=%s,"
                  + " preparedOn=%s, today=%s")
              .formatted(batch.getId(), preparedOn, tradeDate));
    }
  }

  @Transactional
  public void finalizeConfirmedBatches(List<TransactionBatch> batches, OrderSender sender) {
    batches.forEach(TransactionBatchFinalizer::requireUnsent);
    Instant now = Instant.now(clock);
    LocalDate tradeDate = now.atZone(TALLINN).toLocalDate();
    batches.forEach(batch -> requirePreparedOn(batch, tradeDate));
    List<Long> batchIds = batches.stream().map(TransactionBatch::getId).toList();
    log.info("Finalizing batches: ids={}", batchIds);

    List<SentBatch> sentBatches = new ArrayList<>();
    InstrumentNames theDaysNames = InstrumentNames.NONE;
    for (TransactionBatch batch : batches) {
      sentBatches.add(new SentBatch(batch, markOrdersSent(batch, now)));
      theDaysNames = theDaysNames.orElse(instrumentNamesOf(batch.getFund(), tradeDate));
    }
    List<TransactionOrder> theDaysOrders =
        sentBatches.stream().map(SentBatch::orders).flatMap(List::stream).toList();

    Map<ExportFile, byte[]> brokerFiles =
        brokerFileGenerator.generate(
            theDaysOrders,
            theDaysNames.labels(),
            theDaysNames.rics(),
            theDaysNames.bbgTickers(),
            tradeDate);
    sentBatches.forEach(
        sent ->
            markSentWithTheDaysFiles(
                sent.batch(), sent.orders(), brokerFiles, batchIds, now, tradeDate));
    var sendTheOrders =
        custodianOrderEmailSender.prepareSend(
            new OrderDay(batchIds, tradeDate, now, sender, theDaysOrders, brokerFiles));

    int orderCount = theDaysOrders.size();
    runAfterCommit(
        () ->
            emailTheOrdersThenPublishToDrive(
                batchIds, now, tradeDate, orderCount, brokerFiles, sendTheOrders));

    log.info("Batches finalized: ids={}, orderCount={}", batchIds, orderCount);
  }

  private record SentBatch(TransactionBatch batch, List<TransactionOrder> orders) {}

  private List<TransactionOrder> markOrdersSent(TransactionBatch batch, Instant now) {
    List<TransactionOrder> orders = orderRepository.findByBatchId(batch.getId());
    orderFactory.requireQuantitiesForNonAmountOrders(batch, orders);
    for (TransactionOrder order : orders) {
      order.setOrderTimestamp(now);
      order.setExpectedSettlementDate(
          settlementDateCalculator.calculateSettlementDate(
              now, order.getInstrumentType(), order.getInstrumentIsin()));
      order.setOrderStatus(OrderStatus.SENT);
    }
    orderRepository.saveAll(orders);
    return orders;
  }

  private void markSentWithTheDaysFiles(
      TransactionBatch batch,
      List<TransactionOrder> orders,
      Map<ExportFile, byte[]> brokerFiles,
      List<Long> sentTogetherWith,
      Instant now,
      LocalDate tradeDate) {
    Map<String, Object> updatedMetadata = new HashMap<>(batch.getMetadata());
    updatedMetadata.put(
        ExportFile.GENERIC_ORDERS.metadataKey(),
        encodeExport(exportService.generateOrdersExport(orders)));
    brokerFiles.forEach(
        (file, content) -> updatedMetadata.put(file.metadataKey(), encodeExport(content)));
    batch.setMetadata(updatedMetadata);

    batch.setStatus(BatchStatus.SENT);
    batchRepository.save(batch);

    auditEventRepository.save(
        TransactionAuditEvent.builder()
            .batch(batch)
            .eventType("BATCH_FINALIZED")
            .actor(finalizingActor(batch))
            .createdAt(now)
            .payload(
                Map.of(
                    "tradeDate",
                    tradeDate.toString(),
                    "orderCount",
                    orders.size(),
                    "sentTogetherWith",
                    sentTogetherWith))
            .build());
  }

  private InstrumentNames instrumentNamesOf(TulevaFund fund, LocalDate tradeDate) {
    List<ModelPortfolioAllocation> currentAllocations =
        modelPortfolioAllocationRepository.findLatestByFundAsOf(fund, tradeDate);
    List<ModelPortfolioAllocation> previousAllocations =
        modelPortfolioAllocationRepository.findPreviousByFundAsOf(fund, tradeDate);
    var mergedAllocations = new ArrayList<>(previousAllocations);
    mergedAllocations.addAll(currentAllocations);
    return new InstrumentNames(
        buildLookupMap(mergedAllocations, ModelPortfolioAllocation::getLabel),
        buildLookupMap(mergedAllocations, ModelPortfolioAllocation::getTicker),
        buildLookupMap(mergedAllocations, ModelPortfolioAllocation::getBbgTicker));
  }

  private record InstrumentNames(
      Map<String, String> labels, Map<String, String> rics, Map<String, String> bbgTickers) {

    static final InstrumentNames NONE = new InstrumentNames(Map.of(), Map.of(), Map.of());

    InstrumentNames orElse(InstrumentNames other) {
      return new InstrumentNames(
          firstWins(labels, other.labels),
          firstWins(rics, other.rics),
          firstWins(bbgTickers, other.bbgTickers));
    }

    private static Map<String, String> firstWins(
        Map<String, String> first, Map<String, String> second) {
      Map<String, String> merged = new LinkedHashMap<>(second);
      merged.putAll(first);
      return Map.copyOf(merged);
    }
  }

  private void emailTheOrdersThenPublishToDrive(
      List<Long> batchIds,
      Instant sentAt,
      LocalDate tradeDate,
      int orderCount,
      Map<ExportFile, byte[]> brokerFiles,
      Runnable sendTheOrders) {
    try {
      sendTheOrders.run();
    } catch (RuntimeException e) {
      log.error(
          "Order email step failed, still copying the files to Drive and publishing the batches:"
              + " batchIds={}",
          batchIds,
          e);
    }
    Map<String, String> driveFileUrls = uploadToDrive(batchIds, tradeDate, sentAt, brokerFiles);
    if (!driveFileUrls.isEmpty()) {
      batchIds.forEach(batchId -> persistDriveFileUrls(batchId, driveFileUrls));
    }
    eventPublisher.publishEvent(
        new BatchFinalizedEvent(batchIds, orderCount, tradeDate.toString(), driveFileUrls));
  }

  private void persistDriveFileUrls(Long batchId, Map<String, String> driveFileUrls) {
    try {
      transactionOfItsOwn()
          .executeWithoutResult(status -> storeDriveFileUrls(batchId, driveFileUrls));
    } catch (RuntimeException e) {
      log.error("Drive file links not stored on batch: batchId={}", batchId, e);
    }
  }

  private void storeDriveFileUrls(Long batchId, Map<String, String> driveFileUrls) {
    TransactionBatch committedBatch =
        batchRepository
            .findById(batchId)
            .orElseThrow(
                () -> new IllegalStateException("Finalized batch not found: batchId=" + batchId));
    Map<String, Object> updatedMetadata = new HashMap<>(committedBatch.getMetadata());
    updatedMetadata.put("driveFileUrls", driveFileUrls);
    committedBatch.setMetadata(updatedMetadata);
    batchRepository.save(committedBatch);
  }

  private TransactionTemplate transactionOfItsOwn() {
    var transactionOfItsOwn = new TransactionTemplate(transactionManager);
    transactionOfItsOwn.setPropagationBehavior(PROPAGATION_REQUIRES_NEW);
    return transactionOfItsOwn;
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

  private static String finalizingActor(TransactionBatch batch) {
    return batch.getConfirmedBy() == null ? "system" : batch.getConfirmedBy();
  }

  private Map<String, String> buildLookupMap(
      List<ModelPortfolioAllocation> allocations,
      Function<ModelPortfolioAllocation, @Nullable String> valueExtractor) {
    return allocations.stream()
        .filter(
            allocation -> allocation.getIsin() != null && valueExtractor.apply(allocation) != null)
        .collect(toMap(ModelPortfolioAllocation::getIsin, valueExtractor, (a, b) -> b));
  }

  private Map<String, String> uploadToDrive(
      List<Long> batchIds, LocalDate tradeDate, Instant sentAt, Map<ExportFile, byte[]> files) {
    if (!driveProperties.enabled() || exportUploader == null) {
      return Map.of();
    }
    try {
      return exportUploader.uploadExports(driveProperties.rootFolderId(), tradeDate, sentAt, files);
    } catch (Exception e) {
      log.error("Google Drive upload failed: batchIds={}", batchIds, e);
      return Map.of();
    }
  }

  private String encodeExport(byte[] export) {
    return Base64.getEncoder().encodeToString(export);
  }
}
