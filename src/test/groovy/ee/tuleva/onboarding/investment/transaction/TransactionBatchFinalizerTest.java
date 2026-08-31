package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static ee.tuleva.onboarding.investment.transaction.BatchStatus.CONFIRMED;
import static ee.tuleva.onboarding.investment.transaction.BatchStatus.SENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.transaction.export.CustodianOrderEmailSender;
import ee.tuleva.onboarding.investment.transaction.export.GoogleDriveProperties;
import ee.tuleva.onboarding.investment.transaction.export.TransactionExportService;
import ee.tuleva.onboarding.investment.transaction.export.TransactionExportUploader;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class TransactionBatchFinalizerTest {

  @Mock private TransactionOrderRepository orderRepository;
  @Mock private TransactionBatchRepository batchRepository;
  @Mock private TransactionAuditEventRepository auditEventRepository;
  @Mock private SettlementDateCalculator settlementDateCalculator;
  @Mock private TransactionExportService exportService;
  @Mock private ModelPortfolioAllocationRepository modelPortfolioAllocationRepository;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private GoogleDriveProperties driveProperties;
  @Mock private TransactionExportUploader exportUploader;
  @Mock private CustodianOrderEmailSender custodianOrderEmailSender;
  @Mock private PositionPriceResolver positionPriceResolver;
  @Mock private Clock clock;

  private TransactionBatchFinalizer finalizer;

  @BeforeEach
  void setUp() {
    var orderFactory = new TransactionOrderFactory(positionPriceResolver);
    finalizer =
        new TransactionBatchFinalizer(
            orderRepository,
            batchRepository,
            auditEventRepository,
            settlementDateCalculator,
            exportService,
            modelPortfolioAllocationRepository,
            eventPublisher,
            driveProperties,
            exportUploader,
            custodianOrderEmailSender,
            orderFactory,
            clock);
  }

  @AfterEach
  void clearSynchronization() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void finalizeConfirmedBatch_setsOrderTimestampsAndSettlementDatesAndStoresExports() {
    when(clock.instant()).thenReturn(Instant.parse("2026-01-15T10:00:00Z"));

    var batch =
        TransactionBatch.builder()
            .id(1L)
            .fund(TUV100)
            .status(CONFIRMED)
            .createdBy("system")
            .metadata(new HashMap<>(Map.of("commandId", 1L)))
            .build();

    var order =
        TransactionOrder.builder()
            .batch(batch)
            .fund(TUV100)
            .instrumentIsin("IE00A")
            .transactionType(TransactionType.BUY)
            .instrumentType(InstrumentType.ETF)
            .orderAmount(new BigDecimal("100000"))
            .orderQuantity(new BigDecimal("9876.543210"))
            .orderVenue(OrderVenue.SEB)
            .orderStatus(OrderStatus.DRAFT)
            .build();

    var allocation =
        ModelPortfolioAllocation.builder()
            .fund(TUV100)
            .isin("IE00A")
            .label("iShares ESG")
            .ticker("ESGM.DE")
            .bbgTicker("ESGM GY")
            .weight(new BigDecimal("0.60"))
            .effectiveDate(LocalDate.of(2026, 1, 1))
            .build();

    when(orderRepository.findByBatchId(batch.getId())).thenReturn(List.of(order));
    when(settlementDateCalculator.calculateSettlementDate(
            any(Instant.class), eq(InstrumentType.ETF), any()))
        .thenReturn(LocalDate.of(2026, 1, 19));
    when(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUV100, LocalDate.of(2026, 1, 15)))
        .thenReturn(List.of(allocation));
    when(exportService.generateOrdersExport(any())).thenReturn(new byte[] {1, 2, 3});
    when(exportService.generateSebFundExport(any(), any())).thenReturn(new byte[] {4, 5});
    when(exportService.generateSebEtfExport(any(), any())).thenReturn(new byte[] {6, 7});
    when(exportService.generateFtEtfExport(any(), any(), any(), any()))
        .thenReturn(new byte[] {8, 9});
    when(exportService.generateUuidWorkbook(any())).thenReturn(new byte[] {10, 11});

    finalizer.finalizeConfirmedBatch(batch);

    assertThat(order.getOrderTimestamp()).isNotNull();
    assertThat(order.getExpectedSettlementDate()).isEqualTo(LocalDate.of(2026, 1, 19));
    assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.SENT);
    assertThat(batch.getStatus()).isEqualTo(SENT);
    assertThat(batch.getMetadata()).containsKey("xlsxExport");
    assertThat(batch.getMetadata()).containsKey("sebFundXlsx");
    assertThat(batch.getMetadata()).containsKey("sebEtfXlsx");
    assertThat(batch.getMetadata()).containsKey("ftEtfXlsx");
    assertThat(batch.getMetadata()).containsKey("uuidWorkbookXlsx");

    verify(exportService).generateSebFundExport(eq(List.of(order)), any());
    verify(exportService).generateSebEtfExport(eq(List.of(order)), any());
    verify(exportService).generateFtEtfExport(eq(List.of(order)), any(), any(), any());
    verify(exportService).generateUuidWorkbook(eq(List.of(order)));
    verify(orderRepository).saveAll(List.of(order));
    verify(batchRepository).save(batch);
    verify(auditEventRepository).save(argThat(event -> "system".equals(event.getActor())));
    verify(eventPublisher).publishEvent(any(BatchFinalizedEvent.class));
  }

  @Test
  void finalizeConfirmedBatch_usesBatchConfirmedByAsAuditActorWhenPresent() {
    when(clock.instant()).thenReturn(Instant.parse("2026-01-15T10:00:00Z"));

    var batch =
        TransactionBatch.builder()
            .id(1L)
            .fund(TUV100)
            .status(CONFIRMED)
            .createdBy("system")
            .confirmedBy("approver-3")
            .metadata(new HashMap<>(Map.of("commandId", 1L)))
            .build();

    var order =
        TransactionOrder.builder()
            .batch(batch)
            .fund(TUV100)
            .instrumentIsin("IE00A")
            .transactionType(TransactionType.BUY)
            .instrumentType(InstrumentType.ETF)
            .orderAmount(new BigDecimal("100000"))
            .orderQuantity(new BigDecimal("9876.543210"))
            .orderVenue(OrderVenue.SEB)
            .orderStatus(OrderStatus.DRAFT)
            .build();

    when(orderRepository.findByBatchId(batch.getId())).thenReturn(List.of(order));
    when(settlementDateCalculator.calculateSettlementDate(any(Instant.class), any(), any()))
        .thenReturn(LocalDate.of(2026, 1, 19));
    when(exportService.generateOrdersExport(any())).thenReturn(new byte[] {1});
    when(exportService.generateSebFundExport(any(), any())).thenReturn(new byte[] {2});
    when(exportService.generateSebEtfExport(any(), any())).thenReturn(new byte[] {3});
    when(exportService.generateFtEtfExport(any(), any(), any(), any())).thenReturn(new byte[] {4});
    when(exportService.generateUuidWorkbook(any())).thenReturn(new byte[] {5});

    finalizer.finalizeConfirmedBatch(batch);

    verify(auditEventRepository).save(argThat(event -> "approver-3".equals(event.getActor())));
  }

  @Test
  void finalizeConfirmedBatch_excludesAllocationsMissingTickerFromRicLookup() {
    when(clock.instant()).thenReturn(Instant.parse("2026-01-15T10:00:00Z"));

    var batch =
        TransactionBatch.builder()
            .id(1L)
            .fund(TUV100)
            .status(CONFIRMED)
            .createdBy("system")
            .metadata(new HashMap<>(Map.of("commandId", 1L)))
            .build();
    var order =
        TransactionOrder.builder()
            .batch(batch)
            .fund(TUV100)
            .instrumentIsin("IE00A")
            .transactionType(TransactionType.BUY)
            .instrumentType(InstrumentType.ETF)
            .orderAmount(new BigDecimal("100000"))
            .orderQuantity(new BigDecimal("9876.543210"))
            .orderVenue(OrderVenue.SEB)
            .orderStatus(OrderStatus.DRAFT)
            .build();
    var tickerlessAllocation =
        ModelPortfolioAllocation.builder()
            .fund(TUV100)
            .isin("IE00TICKERLESS")
            .label("Tickerless Fund")
            .bbgTicker("TCKL GY")
            .weight(new BigDecimal("0.10"))
            .effectiveDate(LocalDate.of(2026, 1, 1))
            .build();
    var validAllocation =
        ModelPortfolioAllocation.builder()
            .fund(TUV100)
            .isin("IE00A")
            .label("iShares ESG")
            .ticker("ESGM.DE")
            .bbgTicker("ESGM GY")
            .weight(new BigDecimal("0.60"))
            .effectiveDate(LocalDate.of(2026, 1, 1))
            .build();

    when(orderRepository.findByBatchId(batch.getId())).thenReturn(List.of(order));
    when(settlementDateCalculator.calculateSettlementDate(any(Instant.class), any(), any()))
        .thenReturn(LocalDate.of(2026, 1, 19));
    when(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUV100, LocalDate.of(2026, 1, 15)))
        .thenReturn(List.of(validAllocation, tickerlessAllocation));
    when(exportService.generateOrdersExport(any())).thenReturn(new byte[] {1});
    when(exportService.generateSebFundExport(any(), any())).thenReturn(new byte[] {2});
    when(exportService.generateSebEtfExport(any(), any())).thenReturn(new byte[] {3});
    when(exportService.generateFtEtfExport(any(), any(), any(), any())).thenReturn(new byte[] {4});
    when(exportService.generateUuidWorkbook(any())).thenReturn(new byte[] {5});

    finalizer.finalizeConfirmedBatch(batch);

    verify(exportService)
        .generateSebEtfExport(
            eq(List.of(order)),
            argThat(
                ricByIsin ->
                    "ESGM.DE".equals(ricByIsin.get("IE00A"))
                        && !ricByIsin.containsKey("IE00TICKERLESS")));
  }

  @Test
  void finalizeConfirmedBatch_prefersCurrentAllocationOverPreviousForSameIsin() {
    when(clock.instant()).thenReturn(Instant.parse("2026-01-15T10:00:00Z"));

    var batch =
        TransactionBatch.builder()
            .id(1L)
            .fund(TUV100)
            .status(CONFIRMED)
            .createdBy("system")
            .metadata(new HashMap<>(Map.of("commandId", 1L)))
            .build();
    var order =
        TransactionOrder.builder()
            .batch(batch)
            .fund(TUV100)
            .instrumentIsin("IE00A")
            .transactionType(TransactionType.BUY)
            .instrumentType(InstrumentType.ETF)
            .orderAmount(new BigDecimal("100000"))
            .orderQuantity(new BigDecimal("9876.543210"))
            .orderVenue(OrderVenue.SEB)
            .orderStatus(OrderStatus.DRAFT)
            .build();
    var previousAllocation =
        ModelPortfolioAllocation.builder()
            .fund(TUV100)
            .isin("IE00A")
            .label("Old Label")
            .ticker("OLD.TICKER")
            .bbgTicker("OLD GY")
            .weight(new BigDecimal("0.50"))
            .effectiveDate(LocalDate.of(2025, 12, 1))
            .build();
    var currentAllocation =
        ModelPortfolioAllocation.builder()
            .fund(TUV100)
            .isin("IE00A")
            .label("New Label")
            .ticker("NEW.TICKER")
            .bbgTicker("NEW GY")
            .weight(new BigDecimal("0.60"))
            .effectiveDate(LocalDate.of(2026, 1, 1))
            .build();

    when(orderRepository.findByBatchId(batch.getId())).thenReturn(List.of(order));
    when(settlementDateCalculator.calculateSettlementDate(any(Instant.class), any(), any()))
        .thenReturn(LocalDate.of(2026, 1, 19));
    when(modelPortfolioAllocationRepository.findPreviousByFundAsOf(
            TUV100, LocalDate.of(2026, 1, 15)))
        .thenReturn(List.of(previousAllocation));
    when(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUV100, LocalDate.of(2026, 1, 15)))
        .thenReturn(List.of(currentAllocation));
    when(exportService.generateOrdersExport(any())).thenReturn(new byte[] {1});
    when(exportService.generateSebFundExport(any(), any())).thenReturn(new byte[] {2});
    when(exportService.generateSebEtfExport(any(), any())).thenReturn(new byte[] {3});
    when(exportService.generateFtEtfExport(any(), any(), any(), any())).thenReturn(new byte[] {4});
    when(exportService.generateUuidWorkbook(any())).thenReturn(new byte[] {5});

    finalizer.finalizeConfirmedBatch(batch);

    verify(exportService)
        .generateSebEtfExport(
            eq(List.of(order)), argThat(ricByIsin -> "NEW.TICKER".equals(ricByIsin.get("IE00A"))));
  }

  @Test
  void finalizeConfirmedBatch_derivesTradeDateInTallinnZoneNotClockZone() {
    given(clock.instant()).willReturn(Instant.parse("2026-01-15T23:30:00Z"));

    var batch =
        TransactionBatch.builder()
            .id(1L)
            .fund(TUV100)
            .status(CONFIRMED)
            .createdBy("system")
            .metadata(new HashMap<>(Map.of("commandId", 1L)))
            .build();

    var order =
        TransactionOrder.builder()
            .batch(batch)
            .fund(TUV100)
            .instrumentIsin("IE00A")
            .transactionType(TransactionType.BUY)
            .instrumentType(InstrumentType.ETF)
            .orderAmount(new BigDecimal("100000"))
            .orderQuantity(new BigDecimal("9876.543210"))
            .orderVenue(OrderVenue.SEB)
            .orderStatus(OrderStatus.DRAFT)
            .build();

    given(orderRepository.findByBatchId(batch.getId())).willReturn(List.of(order));
    given(settlementDateCalculator.calculateSettlementDate(any(Instant.class), any(), any()))
        .willReturn(LocalDate.of(2026, 1, 20));
    given(exportService.generateOrdersExport(any())).willReturn(new byte[] {1});
    given(exportService.generateSebFundExport(any(), any())).willReturn(new byte[] {2});
    given(exportService.generateSebEtfExport(any(), any())).willReturn(new byte[] {3});
    given(exportService.generateFtEtfExport(any(), any(), any(), any())).willReturn(new byte[] {4});
    given(exportService.generateUuidWorkbook(any())).willReturn(new byte[] {5});

    finalizer.finalizeConfirmedBatch(batch);

    verify(modelPortfolioAllocationRepository)
        .findLatestByFundAsOf(TUV100, LocalDate.of(2026, 1, 16));
    verify(auditEventRepository)
        .save(argThat(event -> "2026-01-16".equals(event.getPayload().get("tradeDate"))));
  }

  @Test
  void finalizeConfirmedBatch_failsWhenNonAmountOrderHasNullQuantity_withoutGeneratingExports() {
    given(clock.instant()).willReturn(Instant.parse("2026-01-15T10:00:00Z"));

    var batch =
        TransactionBatch.builder()
            .id(1L)
            .fund(TUV100)
            .status(CONFIRMED)
            .createdBy("system")
            .metadata(new HashMap<>(Map.of("commandId", 1L)))
            .build();
    var etfOrderWithoutQuantity =
        TransactionOrder.builder()
            .batch(batch)
            .fund(TUV100)
            .instrumentIsin("IE00A")
            .transactionType(TransactionType.BUY)
            .instrumentType(InstrumentType.ETF)
            .orderAmount(new BigDecimal("100000"))
            .orderVenue(OrderVenue.SEB)
            .orderStatus(OrderStatus.DRAFT)
            .build();

    given(orderRepository.findByBatchId(batch.getId()))
        .willReturn(List.of(etfOrderWithoutQuantity));

    assertThatThrownBy(() -> finalizer.finalizeConfirmedBatch(batch))
        .isInstanceOf(IllegalStateException.class);

    assertThat(batch.getStatus()).isEqualTo(CONFIRMED);
    verifyNoInteractions(exportService);
    verify(orderRepository, never()).saveAll(any());
  }

  @Test
  void finalizeConfirmedBatch_alreadySentBatch_doesNotReFinalizeOrReSend() {
    var batch =
        TransactionBatch.builder()
            .id(1L)
            .fund(TUV100)
            .status(SENT)
            .createdBy("system")
            .metadata(new HashMap<>(Map.of("commandId", 1L)))
            .build();

    assertThatThrownBy(() -> finalizer.finalizeConfirmedBatch(batch))
        .isInstanceOf(IllegalStateException.class);

    verifyNoInteractions(exportService);
    verify(orderRepository, never()).saveAll(any());
  }

  @Test
  void finalizeConfirmedBatch_uploadsToDriveWhenEnabled() {
    when(clock.instant()).thenReturn(Instant.parse("2026-01-15T10:00:00Z"));

    var batch =
        TransactionBatch.builder()
            .id(1L)
            .fund(TUV100)
            .status(CONFIRMED)
            .createdBy("system")
            .metadata(new HashMap<>(Map.of("commandId", 1L)))
            .build();

    var order =
        TransactionOrder.builder()
            .batch(batch)
            .fund(TUV100)
            .instrumentIsin("IE00A")
            .transactionType(TransactionType.BUY)
            .instrumentType(InstrumentType.ETF)
            .orderAmount(new BigDecimal("100000"))
            .orderQuantity(new BigDecimal("9876.543210"))
            .orderVenue(OrderVenue.SEB)
            .orderStatus(OrderStatus.DRAFT)
            .build();

    when(orderRepository.findByBatchId(batch.getId())).thenReturn(List.of(order));
    when(settlementDateCalculator.calculateSettlementDate(any(Instant.class), any(), any()))
        .thenReturn(LocalDate.of(2026, 1, 19));
    when(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUV100, LocalDate.of(2026, 1, 15)))
        .thenReturn(List.of());
    when(exportService.generateOrdersExport(any())).thenReturn(new byte[] {1});
    when(exportService.generateSebFundExport(any(), any())).thenReturn(new byte[] {2});
    when(exportService.generateSebEtfExport(any(), any())).thenReturn(new byte[] {3});
    when(exportService.generateFtEtfExport(any(), any(), any(), any())).thenReturn(new byte[] {4});
    when(exportService.generateUuidWorkbook(any())).thenReturn(new byte[] {5});
    when(driveProperties.enabled()).thenReturn(true);
    when(driveProperties.rootFolderId()).thenReturn("root-folder-id");
    when(exportUploader.uploadExports(any(), any(), any(), any()))
        .thenReturn(Map.of("sebFundXlsx", "https://drive.google.com/file1"));

    finalizer.finalizeConfirmedBatch(batch);

    assertThat(batch.getMetadata()).containsKey("driveFileUrls");
    verify(exportUploader).uploadExports(eq("root-folder-id"), eq(TUV100), any(), any());
  }

  @Test
  void finalizeConfirmedBatch_uploadsToDriveOnlyAfterCommit() {
    when(clock.instant()).thenReturn(Instant.parse("2026-01-15T10:00:00Z"));

    var batch =
        TransactionBatch.builder()
            .id(1L)
            .fund(TUV100)
            .status(CONFIRMED)
            .createdBy("system")
            .metadata(new HashMap<>(Map.of("commandId", 1L)))
            .build();

    var order =
        TransactionOrder.builder()
            .batch(batch)
            .fund(TUV100)
            .instrumentIsin("IE00A")
            .transactionType(TransactionType.BUY)
            .instrumentType(InstrumentType.ETF)
            .orderAmount(new BigDecimal("100000"))
            .orderQuantity(new BigDecimal("9876.543210"))
            .orderVenue(OrderVenue.SEB)
            .orderStatus(OrderStatus.DRAFT)
            .build();

    given(orderRepository.findByBatchId(batch.getId())).willReturn(List.of(order));
    given(settlementDateCalculator.calculateSettlementDate(any(Instant.class), any(), any()))
        .willReturn(LocalDate.of(2026, 1, 19));
    given(
            modelPortfolioAllocationRepository.findLatestByFundAsOf(
                TUV100, LocalDate.of(2026, 1, 15)))
        .willReturn(List.of());
    given(exportService.generateOrdersExport(any())).willReturn(new byte[] {1});
    given(exportService.generateSebFundExport(any(), any())).willReturn(new byte[] {2});
    given(exportService.generateSebEtfExport(any(), any())).willReturn(new byte[] {3});
    given(exportService.generateFtEtfExport(any(), any(), any(), any())).willReturn(new byte[] {4});
    given(exportService.generateUuidWorkbook(any())).willReturn(new byte[] {5});
    given(driveProperties.enabled()).willReturn(true);
    given(driveProperties.rootFolderId()).willReturn("root-folder-id");
    given(exportUploader.uploadExports(any(), any(), any(), any()))
        .willReturn(Map.of("sebFundXlsx", "https://drive.google.com/file1"));

    TransactionSynchronizationManager.initSynchronization();

    finalizer.finalizeConfirmedBatch(batch);

    verifyNoInteractions(exportUploader);
    verifyNoInteractions(custodianOrderEmailSender);
    verify(eventPublisher, never()).publishEvent(any(BatchFinalizedEvent.class));

    var synchronizations = TransactionSynchronizationManager.getSynchronizations();
    assertThat(synchronizations).hasSize(1);
    synchronizations.forEach(TransactionSynchronization::afterCommit);

    verify(exportUploader).uploadExports(eq("root-folder-id"), eq(TUV100), any(), any());
    assertThat(batch.getMetadata()).containsKey("driveFileUrls");
    verify(custodianOrderEmailSender).send(eq(TUV100), any(), any());
    verify(eventPublisher).publishEvent(any(BatchFinalizedEvent.class));
  }
}
