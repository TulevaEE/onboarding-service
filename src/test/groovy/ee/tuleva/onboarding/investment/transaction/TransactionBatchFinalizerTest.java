package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.BatchStatus.CONFIRMED;
import static ee.tuleva.onboarding.investment.transaction.BatchStatus.SENT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
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
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class TransactionBatchFinalizerTest {

  @Mock private TransactionOrderRepository orderRepository;
  @Mock private TransactionBatchRepository batchRepository;
  @Mock private TransactionAuditEventRepository auditEventRepository;
  @Mock private SettlementDateCalculator settlementDateCalculator;
  @Mock private TransactionExportService exportService;
  @Mock private BrokerFileGenerator brokerFileGenerator;
  @Mock private ModelPortfolioAllocationRepository modelPortfolioAllocationRepository;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private GoogleDriveProperties driveProperties;
  @Mock private TransactionExportUploader exportUploader;
  @Mock private CustodianOrderEmailSender custodianOrderEmailSender;
  @Mock private Runnable sendTheOrders;
  @Mock private PositionPriceResolver positionPriceResolver;
  @Mock private PlatformTransactionManager transactionManager;
  @Mock private Clock clock;

  private static final OrderSender SENDER =
      new OrderSender("fundmanager@tuleva.ee", "Fund Manager");

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
            brokerFileGenerator,
            modelPortfolioAllocationRepository,
            eventPublisher,
            driveProperties,
            exportUploader,
            custodianOrderEmailSender,
            orderFactory,
            transactionManager,
            clock);
  }

  @AfterEach
  void clearSynchronization() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void
      finalizeConfirmedBatches_setsOrderTimestampsAndSettlementDates_andStoresTheFilesThatWereSent() {
    when(clock.instant()).thenReturn(Instant.parse("2026-01-15T10:00:00Z"));

    var batch =
        TransactionBatch.builder()
            .id(1L)
            .fund(TUV100)
            .status(CONFIRMED)
            .createdBy("system")
            .createdAt(Instant.parse("2026-01-15T07:00:00Z"))
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
            .orderQuantity(new BigDecimal("9876"))
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
    when(exportService.generateOrdersExport(List.of(order))).thenReturn(new byte[] {1, 2, 3});
    when(brokerFileGenerator.generate(
            eq(List.of(order)), any(), any(), any(), eq(LocalDate.of(2026, 1, 15))))
        .thenReturn(
            Map.of(
                ExportFile.SEB_ETF, new byte[] {6, 7},
                ExportFile.UUID_WORKBOOK, new byte[] {10, 11}));

    finalizer.finalizeConfirmedBatches(List.of(batch), SENDER);

    assertThat(order.getOrderTimestamp()).isNotNull();
    assertThat(order.getExpectedSettlementDate()).isEqualTo(LocalDate.of(2026, 1, 19));
    assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.SENT);
    assertThat(batch.getStatus()).isEqualTo(SENT);
    assertThat(batch.getMetadata())
        .isEqualTo(
            Map.of(
                "commandId", 1L,
                "xlsxExport", "AQID",
                "sebEtfXlsx", "Bgc=",
                "uuidWorkbookXlsx", "Cgs="));
    verify(orderRepository).saveAll(List.of(order));
    verify(batchRepository).save(batch);
    verify(auditEventRepository).save(argThat(event -> "system".equals(event.getActor())));
    verify(eventPublisher).publishEvent(any(BatchFinalizedEvent.class));
  }

  @Test
  void finalizeConfirmedBatches_aBatchPreparedOnAnEarlierDay_isRefusedAndNothingIsSent() {
    given(clock.instant()).willReturn(Instant.parse("2026-01-15T10:00:00Z"));
    var batch =
        TransactionBatch.builder()
            .id(1L)
            .fund(TUV100)
            .status(CONFIRMED)
            .createdBy("system")
            .createdAt(Instant.parse("2026-01-14T15:00:00Z"))
            .metadata(new HashMap<>(Map.of("commandId", 1L)))
            .build();

    assertThatThrownBy(() -> finalizer.finalizeConfirmedBatches(List.of(batch), SENDER))
        .isInstanceOf(TransactionBatchRefusedException.class);

    verifyNoInteractions(orderRepository, exportService, batchRepository, eventPublisher);
    assertThat(batch.getStatus()).isEqualTo(CONFIRMED);
  }

  @Test
  void finalizeConfirmedBatches_aBatchPreparedLateTheEveningBeforeInTallinn_isRefused() {
    given(clock.instant()).willReturn(Instant.parse("2026-01-15T07:00:00Z"));
    var batch =
        TransactionBatch.builder()
            .id(1L)
            .fund(TUV100)
            .status(CONFIRMED)
            .createdBy("system")
            .createdAt(Instant.parse("2026-01-14T21:30:00Z"))
            .metadata(new HashMap<>(Map.of("commandId", 1L)))
            .build();

    assertThatThrownBy(() -> finalizer.finalizeConfirmedBatches(List.of(batch), SENDER))
        .isInstanceOf(TransactionBatchRefusedException.class);
  }

  @Test
  void finalizeConfirmedBatches_aBatchWithoutAPreparationTime_isRefused() {
    given(clock.instant()).willReturn(Instant.parse("2026-01-15T10:00:00Z"));
    var batch =
        TransactionBatch.builder()
            .id(1L)
            .fund(TUV100)
            .status(CONFIRMED)
            .createdBy("system")
            .metadata(new HashMap<>(Map.of("commandId", 1L)))
            .build();

    assertThatThrownBy(() -> finalizer.finalizeConfirmedBatches(List.of(batch), SENDER))
        .isInstanceOf(TransactionBatchRefusedException.class);
  }

  @Test
  void finalizeConfirmedBatches_usesBatchConfirmedByAsAuditActorWhenPresent() {
    when(clock.instant()).thenReturn(Instant.parse("2026-01-15T10:00:00Z"));

    var batch =
        TransactionBatch.builder()
            .id(1L)
            .fund(TUV100)
            .status(CONFIRMED)
            .createdBy("system")
            .createdAt(Instant.parse("2026-01-15T07:00:00Z"))
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
            .orderQuantity(new BigDecimal("9876"))
            .orderVenue(OrderVenue.SEB)
            .orderStatus(OrderStatus.DRAFT)
            .build();

    when(orderRepository.findByBatchId(batch.getId())).thenReturn(List.of(order));
    when(settlementDateCalculator.calculateSettlementDate(any(Instant.class), any(), any()))
        .thenReturn(LocalDate.of(2026, 1, 19));
    when(exportService.generateOrdersExport(any())).thenReturn(new byte[] {1});

    finalizer.finalizeConfirmedBatches(List.of(batch), SENDER);

    verify(auditEventRepository).save(argThat(event -> "approver-3".equals(event.getActor())));
  }

  @Test
  void finalizeConfirmedBatches_excludesAllocationsMissingTickerFromRicLookup() {
    when(clock.instant()).thenReturn(Instant.parse("2026-01-15T10:00:00Z"));

    var batch =
        TransactionBatch.builder()
            .id(1L)
            .fund(TUV100)
            .status(CONFIRMED)
            .createdBy("system")
            .createdAt(Instant.parse("2026-01-15T07:00:00Z"))
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
            .orderQuantity(new BigDecimal("9876"))
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

    finalizer.finalizeConfirmedBatches(List.of(batch), SENDER);

    verify(brokerFileGenerator)
        .generate(
            eq(List.of(order)),
            any(),
            argThat(
                ricByIsin ->
                    "ESGM.DE".equals(ricByIsin.get("IE00A"))
                        && !ricByIsin.containsKey("IE00TICKERLESS")),
            any(),
            eq(LocalDate.of(2026, 1, 15)));
  }

  @Test
  void finalizeConfirmedBatches_prefersCurrentAllocationOverPreviousForSameIsin() {
    when(clock.instant()).thenReturn(Instant.parse("2026-01-15T10:00:00Z"));

    var batch =
        TransactionBatch.builder()
            .id(1L)
            .fund(TUV100)
            .status(CONFIRMED)
            .createdBy("system")
            .createdAt(Instant.parse("2026-01-15T07:00:00Z"))
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
            .orderQuantity(new BigDecimal("9876"))
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

    finalizer.finalizeConfirmedBatches(List.of(batch), SENDER);

    verify(brokerFileGenerator)
        .generate(
            eq(List.of(order)),
            argThat(labelsByIsin -> "New Label".equals(labelsByIsin.get("IE00A"))),
            argThat(ricByIsin -> "NEW.TICKER".equals(ricByIsin.get("IE00A"))),
            argThat(bbgByIsin -> "NEW GY".equals(bbgByIsin.get("IE00A"))),
            eq(LocalDate.of(2026, 1, 15)));
  }

  @Test
  void finalizeConfirmedBatches_derivesTradeDateInTallinnZoneNotClockZone() {
    given(clock.instant()).willReturn(Instant.parse("2026-01-15T23:30:00Z"));

    var batch =
        TransactionBatch.builder()
            .id(1L)
            .fund(TUV100)
            .status(CONFIRMED)
            .createdBy("system")
            .createdAt(Instant.parse("2026-01-15T22:30:00Z"))
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
            .orderQuantity(new BigDecimal("9876"))
            .orderVenue(OrderVenue.SEB)
            .orderStatus(OrderStatus.DRAFT)
            .build();

    given(orderRepository.findByBatchId(batch.getId())).willReturn(List.of(order));
    given(settlementDateCalculator.calculateSettlementDate(any(Instant.class), any(), any()))
        .willReturn(LocalDate.of(2026, 1, 20));
    given(exportService.generateOrdersExport(any())).willReturn(new byte[] {1});

    finalizer.finalizeConfirmedBatches(List.of(batch), SENDER);

    verify(modelPortfolioAllocationRepository)
        .findLatestByFundAsOf(TUV100, LocalDate.of(2026, 1, 16));
    verify(auditEventRepository)
        .save(argThat(event -> "2026-01-16".equals(event.getPayload().get("tradeDate"))));
  }

  @Test
  void finalizeConfirmedBatches_failsWhenNonAmountOrderHasNullQuantity_withoutGeneratingExports() {
    given(clock.instant()).willReturn(Instant.parse("2026-01-15T10:00:00Z"));

    var batch =
        TransactionBatch.builder()
            .id(1L)
            .fund(TUV100)
            .status(CONFIRMED)
            .createdBy("system")
            .createdAt(Instant.parse("2026-01-15T07:00:00Z"))
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

    assertThatThrownBy(() -> finalizer.finalizeConfirmedBatches(List.of(batch), SENDER))
        .isInstanceOf(TransactionBatchRefusedException.class);

    assertThat(batch.getStatus()).isEqualTo(CONFIRMED);
    verifyNoInteractions(exportService);
    verify(orderRepository, never()).saveAll(any());
  }

  @Test
  void finalizeConfirmedBatches_sendsEveryFundsOrdersAsOneDay_inTheSendersOwnName() {
    given(clock.instant()).willReturn(Instant.parse("2026-01-15T10:00:00Z"));
    var tuk75 = confirmedBatch(1L, TUK75);
    var tuv100 = confirmedBatch(2L, TUV100);
    var tuk75Order = etfOrder(tuk75, "IE00A");
    var tuv100Order = etfOrder(tuv100, "IE00B");
    given(orderRepository.findByBatchId(1L)).willReturn(List.of(tuk75Order));
    given(orderRepository.findByBatchId(2L)).willReturn(List.of(tuv100Order));
    given(settlementDateCalculator.calculateSettlementDate(any(Instant.class), any(), any()))
        .willReturn(LocalDate.of(2026, 1, 19));
    given(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUK75, LocalDate.of(2026, 1, 15)))
        .willReturn(List.of(allocation(TUK75, "IE00A", "ETF A")));
    given(
            modelPortfolioAllocationRepository.findLatestByFundAsOf(
                TUV100, LocalDate.of(2026, 1, 15)))
        .willReturn(List.of(allocation(TUV100, "IE00B", "ETF B")));
    given(exportService.generateOrdersExport(any())).willReturn(new byte[] {1});
    Map<ExportFile, byte[]> theDaysFiles = Map.of(ExportFile.FT_ETF, new byte[] {8, 9});
    given(
            brokerFileGenerator.generate(
                List.of(tuk75Order, tuv100Order),
                Map.of("IE00A", "ETF A", "IE00B", "ETF B"),
                Map.of("IE00A", "A.DE", "IE00B", "B.DE"),
                Map.of("IE00A", "A GY", "IE00B", "B GY"),
                LocalDate.of(2026, 1, 15)))
        .willReturn(theDaysFiles);

    finalizer.finalizeConfirmedBatches(List.of(tuk75, tuv100), SENDER);

    then(custodianOrderEmailSender)
        .should()
        .prepareSend(
            new OrderDay(
                List.of(1L, 2L),
                LocalDate.of(2026, 1, 15),
                Instant.parse("2026-01-15T10:00:00Z"),
                SENDER,
                List.of(tuk75Order, tuv100Order),
                theDaysFiles));
    assertThat(List.of(tuk75, tuv100)).extracting(TransactionBatch::getStatus).containsOnly(SENT);
    verify(auditEventRepository, times(2))
        .save(argThat(event -> event.getPayload().get("sentTogetherWith").equals(List.of(1L, 2L))));
    verify(eventPublisher)
        .publishEvent(
            new BatchFinalizedEvent(List.of(1L, 2L), 2, LocalDate.parse("2026-01-15"), Map.of()));
  }

  @Test
  void finalizeConfirmedBatches_oneBatchPreparedEarlier_refusesTheWholeDayBeforeTouchingAny() {
    given(clock.instant()).willReturn(Instant.parse("2026-01-15T10:00:00Z"));
    var today = confirmedBatch(1L, TUK75);
    var yesterday =
        TransactionBatch.builder()
            .id(2L)
            .fund(TUV100)
            .status(CONFIRMED)
            .createdBy("system")
            .createdAt(Instant.parse("2026-01-14T15:00:00Z"))
            .metadata(new HashMap<>())
            .build();

    assertThatThrownBy(() -> finalizer.finalizeConfirmedBatches(List.of(today, yesterday), SENDER))
        .isInstanceOf(TransactionBatchRefusedException.class);

    verifyNoInteractions(orderRepository, exportService, custodianOrderEmailSender);
    assertThat(today.getStatus()).isEqualTo(CONFIRMED);
  }

  private static TransactionBatch confirmedBatch(Long id, TulevaFund fund) {
    return TransactionBatch.builder()
        .id(id)
        .fund(fund)
        .status(CONFIRMED)
        .createdBy("system")
        .createdAt(Instant.parse("2026-01-15T07:00:00Z"))
        .confirmedBy("operator-7")
        .metadata(new HashMap<>())
        .build();
  }

  private static TransactionOrder etfOrder(TransactionBatch batch, String isin) {
    return TransactionOrder.builder()
        .batch(batch)
        .fund(batch.getFund())
        .instrumentIsin(isin)
        .transactionType(TransactionType.BUY)
        .instrumentType(InstrumentType.ETF)
        .orderAmount(new BigDecimal("100000"))
        .orderQuantity(new BigDecimal("9876"))
        .orderVenue(OrderVenue.FT)
        .orderStatus(OrderStatus.DRAFT)
        .build();
  }

  private static ModelPortfolioAllocation allocation(TulevaFund fund, String isin, String label) {
    String symbol = isin.substring(isin.length() - 1);
    return ModelPortfolioAllocation.builder()
        .fund(fund)
        .isin(isin)
        .label(label)
        .ticker(symbol + ".DE")
        .bbgTicker(symbol + " GY")
        .weight(new BigDecimal("0.50"))
        .effectiveDate(LocalDate.of(2026, 1, 1))
        .build();
  }

  @Test
  void finalizeConfirmedBatches_alreadySentBatch_doesNotReFinalizeOrReSend() {
    var batch =
        TransactionBatch.builder()
            .id(1L)
            .fund(TUV100)
            .status(SENT)
            .createdBy("system")
            .createdAt(Instant.parse("2026-01-15T07:00:00Z"))
            .metadata(new HashMap<>(Map.of("commandId", 1L)))
            .build();

    assertThatThrownBy(() -> finalizer.finalizeConfirmedBatches(List.of(batch), SENDER))
        .isInstanceOf(IllegalStateException.class);

    verifyNoInteractions(exportService);
    verify(orderRepository, never()).saveAll(any());
  }

  @Test
  void finalizeConfirmedBatches_uploadsToDriveWhenEnabled() {
    when(clock.instant()).thenReturn(Instant.parse("2026-01-15T10:00:00Z"));

    var batch =
        TransactionBatch.builder()
            .id(1L)
            .fund(TUV100)
            .status(CONFIRMED)
            .createdBy("system")
            .createdAt(Instant.parse("2026-01-15T07:00:00Z"))
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
            .orderQuantity(new BigDecimal("9876"))
            .orderVenue(OrderVenue.SEB)
            .orderStatus(OrderStatus.DRAFT)
            .build();

    when(orderRepository.findByBatchId(batch.getId())).thenReturn(List.of(order));
    when(settlementDateCalculator.calculateSettlementDate(any(Instant.class), any(), any()))
        .thenReturn(LocalDate.of(2026, 1, 19));
    when(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUV100, LocalDate.of(2026, 1, 15)))
        .thenReturn(List.of());
    when(exportService.generateOrdersExport(any())).thenReturn(new byte[] {1});
    when(driveProperties.enabled()).thenReturn(true);
    when(driveProperties.rootFolderId()).thenReturn("root-folder-id");
    when(exportUploader.uploadExports(any(), any(), any(), any()))
        .thenReturn(Map.of("sebFundXlsx", "https://drive.google.com/file1"));
    var committedBatch = committedCopyOf(batch);
    given(batchRepository.findById(1L)).willReturn(Optional.of(committedBatch));

    finalizer.finalizeConfirmedBatches(List.of(batch), SENDER);

    assertThat(committedBatch.getMetadata())
        .isEqualTo(
            Map.of(
                "commandId",
                1L,
                "driveFileUrls",
                Map.of("sebFundXlsx", "https://drive.google.com/file1")));
    verify(batchRepository).save(committedBatch);
    verify(exportUploader)
        .uploadExports(eq("root-folder-id"), eq(LocalDate.of(2026, 1, 15)), any(), any());
  }

  @Test
  void finalizeConfirmedBatches_driveLinksThatCannotBeStored_stillSendOrdersToCustodian() {
    given(clock.instant()).willReturn(Instant.parse("2026-01-15T10:00:00Z"));

    var batch =
        TransactionBatch.builder()
            .id(1L)
            .fund(TUV100)
            .status(CONFIRMED)
            .createdBy("system")
            .createdAt(Instant.parse("2026-01-15T07:00:00Z"))
            .metadata(new HashMap<>(Map.of("commandId", 1L)))
            .build();

    given(orderRepository.findByBatchId(batch.getId())).willReturn(List.of());
    given(exportService.generateOrdersExport(any())).willReturn(new byte[] {1});
    given(driveProperties.enabled()).willReturn(true);
    given(driveProperties.rootFolderId()).willReturn("root-folder-id");
    var driveFileUrls = Map.of("sebFundXlsx", "https://drive.google.com/file1");
    given(exportUploader.uploadExports(any(), any(), any(), any())).willReturn(driveFileUrls);
    given(batchRepository.findById(1L))
        .willThrow(new DataAccessResourceFailureException("database unavailable"));
    given(custodianOrderEmailSender.prepareSend(any())).willReturn(sendTheOrders);

    finalizer.finalizeConfirmedBatches(List.of(batch), SENDER);

    then(sendTheOrders).should().run();
    verify(eventPublisher)
        .publishEvent(
            new BatchFinalizedEvent(List.of(1L), 0, LocalDate.parse("2026-01-15"), driveFileUrls));
  }

  @Test
  void
      finalizeConfirmedBatches_recordsTheSendAttemptBeforeCommit_andEmailsBeforeUploadingToDrive() {
    when(clock.instant()).thenReturn(Instant.parse("2026-01-15T10:00:00Z"));

    var batch =
        TransactionBatch.builder()
            .id(1L)
            .fund(TUV100)
            .status(CONFIRMED)
            .createdBy("system")
            .createdAt(Instant.parse("2026-01-15T07:00:00Z"))
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
            .orderQuantity(new BigDecimal("9876"))
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
    given(driveProperties.enabled()).willReturn(true);
    given(driveProperties.rootFolderId()).willReturn("root-folder-id");
    given(exportUploader.uploadExports(any(), any(), any(), any()))
        .willReturn(Map.of("sebFundXlsx", "https://drive.google.com/file1"));
    var committedBatch = committedCopyOf(batch);
    given(batchRepository.findById(1L)).willReturn(Optional.of(committedBatch));

    given(custodianOrderEmailSender.prepareSend(any())).willReturn(sendTheOrders);
    TransactionSynchronizationManager.initSynchronization();

    finalizer.finalizeConfirmedBatches(List.of(batch), SENDER);

    verifyNoInteractions(exportUploader);
    then(custodianOrderEmailSender).should().prepareSend(any());
    then(sendTheOrders).shouldHaveNoInteractions();
    verify(eventPublisher, never()).publishEvent(any(BatchFinalizedEvent.class));

    var synchronizations = TransactionSynchronizationManager.getSynchronizations();
    assertThat(synchronizations).hasSize(1);
    synchronizations.forEach(TransactionSynchronization::afterCommit);

    verify(exportUploader)
        .uploadExports(eq("root-folder-id"), eq(LocalDate.of(2026, 1, 15)), any(), any());
    assertThat(committedBatch.getMetadata())
        .isEqualTo(
            Map.of(
                "commandId",
                1L,
                "driveFileUrls",
                Map.of("sebFundXlsx", "https://drive.google.com/file1")));
    verify(batchRepository).save(committedBatch);
    var ordersBeforeDrive = inOrder(sendTheOrders, exportUploader);
    then(sendTheOrders).should(ordersBeforeDrive).run();
    then(exportUploader)
        .should(ordersBeforeDrive)
        .uploadExports(eq("root-folder-id"), eq(LocalDate.of(2026, 1, 15)), any(), any());
    verify(eventPublisher).publishEvent(any(BatchFinalizedEvent.class));
  }

  private static TransactionBatch committedCopyOf(TransactionBatch batch) {
    return TransactionBatch.builder()
        .id(batch.getId())
        .fund(batch.getFund())
        .status(SENT)
        .createdBy(batch.getCreatedBy())
        .metadata(Map.of("commandId", 1L))
        .build();
  }
}
