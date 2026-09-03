package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.transaction.export.CustodianOrderEmailSender;
import ee.tuleva.onboarding.investment.transaction.export.GoogleDriveProperties;
import ee.tuleva.onboarding.investment.transaction.export.TransactionExportService;
import ee.tuleva.onboarding.investment.transaction.export.TransactionExportUploader;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
  private final ModelPortfolioAllocationRepository modelPortfolioAllocationRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final GoogleDriveProperties driveProperties;
  @Nullable private final TransactionExportUploader exportUploader;
  private final CustodianOrderEmailSender custodianOrderEmailSender;
  private final TransactionOrderFactory orderFactory;
  private final Clock clock;

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
    orderFactory.requireQuantitiesForNonAmountOrders(batch, orders);
    for (TransactionOrder order : orders) {
      order.setOrderTimestamp(now);
      order.setExpectedSettlementDate(
          settlementDateCalculator.calculateSettlementDate(
              now, order.getInstrumentType(), order.getInstrumentIsin()));
      order.setOrderStatus(OrderStatus.SENT);
    }
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
            .actor(finalizingActor(batch))
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
