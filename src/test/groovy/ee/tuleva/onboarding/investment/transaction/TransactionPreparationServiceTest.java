package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static ee.tuleva.onboarding.investment.transaction.BatchStatus.*;
import static ee.tuleva.onboarding.investment.transaction.CommandStatus.*;
import static ee.tuleva.onboarding.investment.transaction.TransactionMode.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionMode.SELL;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class TransactionPreparationServiceTest {

  @Mock private TransactionInputService inputService;
  @Mock private TradeCalculationEngine calculationEngine;
  @Mock private TransactionBatchRepository batchRepository;
  @Mock private TransactionOrderRepository orderRepository;
  @Mock private TransactionAuditEventRepository auditEventRepository;
  @Mock private TransactionCommandRepository commandRepository;
  @Mock private SettlementDateCalculator settlementDateCalculator;
  @Mock private TransactionExportService exportService;
  @Mock private ModelPortfolioAllocationRepository modelPortfolioAllocationRepository;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private GoogleDriveProperties driveProperties;
  @Mock private TransactionExportUploader exportUploader;
  @Mock private CustodianOrderEmailSender custodianOrderEmailSender;
  @Mock private PositionPriceResolver positionPriceResolver;
  @Mock private Clock clock;

  @InjectMocks private TransactionPreparationService service;

  @AfterEach
  void clearSynchronization() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void processCommand_createsBatchAndOrders() {
    var command =
        TransactionCommand.builder()
            .id(1L)
            .fund(TUV100)
            .mode(BUY)
            .asOfDate(LocalDate.of(2026, 1, 15))
            .manualAdjustments(Map.of())
            .status(PROCESSING)
            .build();

    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(List.of(new PositionSnapshot("IE00A", new BigDecimal("500000"))))
            .modelWeights(List.of(new ModelWeight("IE00A", new BigDecimal("1.00"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(new BigDecimal("50000"))
            .liabilities(ZERO)
            .freeCash(new BigDecimal("100000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var trades =
        List.of(
            new TradeCalculation(
                "IE00A", new BigDecimal("100000"), new BigDecimal("0.60"), LimitStatus.OK));

    var calculationResult = new FundCalculationResult(TUV100, BUY, input, trades);

    when(inputService.gatherInput(TUV100, command.getAsOfDate(), Map.of())).thenReturn(input);
    when(calculationEngine.calculate(input, BUY)).thenReturn(calculationResult);
    when(batchRepository.save(any(TransactionBatch.class)))
        .thenAnswer(
            invocation -> {
              TransactionBatch batch = invocation.getArgument(0);
              batch.setId(1L);
              return batch;
            });

    var result = service.processCommand(command);

    assertThat(result.batch().getFund()).isEqualTo(TUV100);
    assertThat(result.orders())
        .singleElement()
        .satisfies(
            order -> {
              assertThat(order.getOrderUuid()).isNotNull();
              assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
            });

    verify(auditEventRepository).save(any(TransactionAuditEvent.class));

    assertThat(command.getStatus()).isEqualTo(CALCULATED);
    assertThat(command.getBatchId()).isNotNull();
  }

  @Test
  void processCommand_persistsManualAdjustmentsInCalculationSnapshot() {
    var manualAdjustments = Map.<String, Object>of("IE00A", "25000");
    var command =
        TransactionCommand.builder()
            .id(8L)
            .fund(TUV100)
            .mode(BUY)
            .asOfDate(LocalDate.of(2026, 1, 15))
            .manualAdjustments(manualAdjustments)
            .status(PROCESSING)
            .build();

    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(List.of(new PositionSnapshot("IE00A", new BigDecimal("500000"))))
            .modelWeights(List.of(new ModelWeight("IE00A", new BigDecimal("1.00"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(new BigDecimal("50000"))
            .liabilities(ZERO)
            .freeCash(new BigDecimal("100000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var trades =
        List.of(
            new TradeCalculation(
                "IE00A", new BigDecimal("100000"), new BigDecimal("0.60"), LimitStatus.OK));

    var calculationResult = new FundCalculationResult(TUV100, BUY, input, trades);

    given(inputService.gatherInput(TUV100, command.getAsOfDate(), manualAdjustments))
        .willReturn(input);
    given(calculationEngine.calculate(input, BUY)).willReturn(calculationResult);
    given(batchRepository.save(any(TransactionBatch.class)))
        .willAnswer(
            invocation -> {
              TransactionBatch batch = invocation.getArgument(0);
              batch.setId(1L);
              return batch;
            });

    service.processCommand(command);

    var expectedEvent =
        TransactionAuditEvent.builder()
            .eventType("CALCULATION_COMPLETED")
            .actor("system")
            .createdAt(Instant.now(clock))
            .payload(
                Map.of(
                    "input", TransactionPreparationService.serializeInput(input, manualAdjustments),
                    "output", TransactionPreparationService.serializeTrades(trades),
                    "summary",
                        Map.of(
                            "fund", TUV100.name(),
                            "mode", BUY.name(),
                            "tradeCount", 1)))
            .build();
    verify(auditEventRepository)
        .save(
            argThat(
                event ->
                    "CALCULATION_COMPLETED".equals(event.getEventType())
                        && event.getPayload().equals(expectedEvent.getPayload())));
  }

  @Test
  void serializeInput_serializesAllFieldsToPlainStrings() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(List.of(new PositionSnapshot("IE00A", new BigDecimal("500000"))))
            .modelWeights(List.of(new ModelWeight("IE00A", new BigDecimal("1.00"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(new BigDecimal("50000"))
            .liabilities(ZERO)
            .receivables(ZERO)
            .freeCash(new BigDecimal("100000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(
                Map.of(
                    "IE00A",
                    new PositionLimitSnapshot(new BigDecimal("0.10"), new BigDecimal("0.15"))))
            .fastSellIsins(Set.of("IE00A"))
            .build();

    var manualAdjustments = Map.<String, Object>of("IE00A", "10000");

    var result = TransactionPreparationService.serializeInput(input, manualAdjustments);

    assertThat(result).containsEntry("grossPortfolioValue", "1000000");
    assertThat(result).containsEntry("freeCash", "100000");
    assertThat(result).containsEntry("cashBuffer", "50000");
    assertThat(result).containsEntry("liabilities", "0");
    assertThat(result).containsEntry("receivables", "0");
    assertThat(result).containsEntry("minTransactionThreshold", "5000");
    assertThat(result).containsKey("positions");
    assertThat(result).containsKey("modelWeights");
    assertThat(result).containsKey("positionLimits");
    assertThat(result).containsKey("fastSellIsins");
    assertThat(result).containsEntry("manualAdjustments", manualAdjustments);
  }

  @Test
  void serializeTrades_serializesAllFieldsToPlainStrings() {
    var trades =
        List.of(
            new TradeCalculation(
                "IE00A", new BigDecimal("100000"), new BigDecimal("0.60"), LimitStatus.OK));

    var result = TransactionPreparationService.serializeTrades(trades);

    assertThat(result)
        .singleElement()
        .satisfies(
            trade -> {
              assertThat(trade).containsEntry("isin", "IE00A");
              assertThat(trade).containsEntry("tradeAmount", "100000");
              assertThat(trade).containsEntry("projectedWeight", "0.60");
              assertThat(trade).containsEntry("limitStatus", "OK");
            });
  }

  @Test
  void processCommand_onError_setsFailedStatus() {
    var command =
        TransactionCommand.builder()
            .id(1L)
            .fund(TUV100)
            .mode(BUY)
            .asOfDate(LocalDate.of(2026, 1, 15))
            .manualAdjustments(Map.of())
            .status(PROCESSING)
            .build();

    when(clock.instant()).thenReturn(Instant.parse("2026-01-15T10:00:00Z"));
    when(inputService.gatherInput(any(), any(), any()))
        .thenThrow(new IllegalStateException("No position data found"));

    var result = service.processCommand(command);

    assertThat(result).isNull();
    assertThat(command.getStatus()).isEqualTo(FAILED);
    assertThat(command.getErrorMessage()).isNotNull();
    assertThat(command.getProcessedAt()).isNotNull();
    verify(commandRepository).save(command);
  }

  @Test
  void finalizeConfirmedBatch_setsOrderTimestampsAndSettlementDatesAndStoresExports() {
    when(clock.instant()).thenReturn(Instant.parse("2026-01-15T10:00:00Z"));
    when(clock.getZone()).thenReturn(ZoneId.of("Europe/Tallinn"));

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
            .orderStatus(OrderStatus.PENDING)
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
    when(settlementDateCalculator.calculateSettlementDate(any(), eq(InstrumentType.ETF), any()))
        .thenReturn(LocalDate.of(2026, 1, 19));
    when(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUV100, LocalDate.of(2026, 1, 15)))
        .thenReturn(List.of(allocation));
    when(exportService.generateOrdersExport(any())).thenReturn(new byte[] {1, 2, 3});
    when(exportService.generateSebFundExport(any(), any())).thenReturn(new byte[] {4, 5});
    when(exportService.generateSebEtfExport(any(), any())).thenReturn(new byte[] {6, 7});
    when(exportService.generateFtEtfExport(any(), any(), any())).thenReturn(new byte[] {8, 9});

    service.finalizeConfirmedBatch(batch);

    assertThat(order.getOrderTimestamp()).isNotNull();
    assertThat(order.getExpectedSettlementDate()).isEqualTo(LocalDate.of(2026, 1, 19));
    assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.SENT);
    assertThat(batch.getStatus()).isEqualTo(SENT);
    assertThat(batch.getMetadata()).containsKey("xlsxExport");
    assertThat(batch.getMetadata()).containsKey("sebFundXlsx");
    assertThat(batch.getMetadata()).containsKey("sebEtfXlsx");
    assertThat(batch.getMetadata()).containsKey("ftEtfXlsx");

    verify(exportService).generateSebFundExport(eq(List.of(order)), any());
    verify(exportService).generateSebEtfExport(eq(List.of(order)), any());
    verify(exportService).generateFtEtfExport(eq(List.of(order)), any(), any());
    verify(orderRepository).saveAll(List.of(order));
    verify(batchRepository).save(batch);
    verify(auditEventRepository).save(any(TransactionAuditEvent.class));
    verify(eventPublisher).publishEvent(any(BatchFinalizedEvent.class));
  }

  @Test
  void processCommand_usesInstrumentTypeAndVenueFromInput() {
    var command =
        TransactionCommand.builder()
            .id(2L)
            .fund(TUV100)
            .mode(BUY)
            .asOfDate(LocalDate.of(2026, 1, 15))
            .manualAdjustments(Map.of())
            .status(PROCESSING)
            .build();

    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00ETF", new BigDecimal("300000")),
                    new PositionSnapshot("LU00FUND", new BigDecimal("200000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00ETF", new BigDecimal("0.60")),
                    new ModelWeight("LU00FUND", new BigDecimal("0.40"))))
            .grossPortfolioValue(new BigDecimal("600000"))
            .cashBuffer(new BigDecimal("10000"))
            .liabilities(ZERO)
            .freeCash(new BigDecimal("90000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .instrumentTypes(Map.of("IE00ETF", InstrumentType.ETF, "LU00FUND", InstrumentType.FUND))
            .orderVenues(Map.of("IE00ETF", OrderVenue.SEB, "LU00FUND", OrderVenue.FT))
            .build();

    var trades =
        List.of(
            new TradeCalculation(
                "IE00ETF", new BigDecimal("50000"), new BigDecimal("0.58"), LimitStatus.OK),
            new TradeCalculation(
                "LU00FUND", new BigDecimal("40000"), new BigDecimal("0.40"), LimitStatus.OK));

    var calculationResult = new FundCalculationResult(TUV100, BUY, input, trades);

    when(inputService.gatherInput(TUV100, command.getAsOfDate(), Map.of())).thenReturn(input);
    when(calculationEngine.calculate(input, BUY)).thenReturn(calculationResult);
    when(batchRepository.save(any(TransactionBatch.class)))
        .thenAnswer(
            invocation -> {
              TransactionBatch batch = invocation.getArgument(0);
              batch.setId(1L);
              return batch;
            });

    var result = service.processCommand(command);

    assertThat(result.orders()).hasSize(2);

    var etfOrder =
        result.orders().stream()
            .filter(order -> order.getInstrumentIsin().equals("IE00ETF"))
            .findFirst()
            .orElseThrow();
    assertThat(etfOrder.getInstrumentType()).isEqualTo(InstrumentType.ETF);
    assertThat(etfOrder.getOrderVenue()).isEqualTo(OrderVenue.SEB);

    var fundOrder =
        result.orders().stream()
            .filter(order -> order.getInstrumentIsin().equals("LU00FUND"))
            .findFirst()
            .orElseThrow();
    assertThat(fundOrder.getInstrumentType()).isEqualTo(InstrumentType.FUND);
    assertThat(fundOrder.getOrderVenue()).isEqualTo(OrderVenue.FT);
  }

  @Test
  void processCommand_setsEtfOrderQuantityFromLatestPriceAndLeavesFundOrderQuantityNull() {
    var command =
        TransactionCommand.builder()
            .id(4L)
            .fund(TUV100)
            .mode(BUY)
            .asOfDate(LocalDate.of(2026, 1, 15))
            .manualAdjustments(Map.of())
            .status(PROCESSING)
            .build();

    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00ETF", new BigDecimal("300000")),
                    new PositionSnapshot("IE00ETF2", new BigDecimal("100000")),
                    new PositionSnapshot("LU00FUND", new BigDecimal("200000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00ETF", new BigDecimal("0.50")),
                    new ModelWeight("IE00ETF2", new BigDecimal("0.10")),
                    new ModelWeight("LU00FUND", new BigDecimal("0.40"))))
            .grossPortfolioValue(new BigDecimal("600000"))
            .cashBuffer(new BigDecimal("10000"))
            .liabilities(ZERO)
            .freeCash(new BigDecimal("90000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .instrumentTypes(
                Map.of(
                    "IE00ETF", InstrumentType.ETF,
                    "IE00ETF2", InstrumentType.ETF,
                    "LU00FUND", InstrumentType.FUND))
            .orderVenues(Map.of())
            .build();

    var trades =
        List.of(
            new TradeCalculation(
                "IE00ETF", new BigDecimal("50000"), new BigDecimal("0.55"), LimitStatus.OK),
            new TradeCalculation(
                "IE00ETF2", new BigDecimal("-30000"), new BigDecimal("0.08"), LimitStatus.OK),
            new TradeCalculation(
                "LU00FUND", new BigDecimal("40000"), new BigDecimal("0.40"), LimitStatus.OK));

    var calculationResult = new FundCalculationResult(TUV100, BUY, input, trades);

    when(inputService.gatherInput(TUV100, command.getAsOfDate(), Map.of())).thenReturn(input);
    when(calculationEngine.calculate(input, BUY)).thenReturn(calculationResult);
    when(batchRepository.save(any(TransactionBatch.class)))
        .thenAnswer(
            invocation -> {
              TransactionBatch batch = invocation.getArgument(0);
              batch.setId(1L);
              return batch;
            });
    when(positionPriceResolver.resolve("IE00ETF", LocalDate.of(2026, 1, 15)))
        .thenReturn(Optional.of(ResolvedPrice.builder().usedPrice(new BigDecimal("3")).build()));
    when(positionPriceResolver.resolve("IE00ETF2", LocalDate.of(2026, 1, 15)))
        .thenReturn(Optional.of(ResolvedPrice.builder().usedPrice(new BigDecimal("8")).build()));

    var result = service.processCommand(command);

    var etfBuyOrder = orderByIsin(result, "IE00ETF");
    assertThat(etfBuyOrder.getOrderQuantity()).isEqualTo(new BigDecimal("16666.666667"));

    var etfSellOrder = orderByIsin(result, "IE00ETF2");
    assertThat(etfSellOrder.getOrderQuantity()).isEqualTo(new BigDecimal("3750.000000"));

    var fundOrder = orderByIsin(result, "LU00FUND");
    assertThat(fundOrder.getOrderQuantity()).isNull();
  }

  @Test
  void processCommand_leavesEtfOrderQuantityNullWhenNoPriceAvailable() {
    var command =
        TransactionCommand.builder()
            .id(5L)
            .fund(TUV100)
            .mode(BUY)
            .asOfDate(LocalDate.of(2026, 1, 15))
            .manualAdjustments(Map.of())
            .status(PROCESSING)
            .build();

    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(List.of(new PositionSnapshot("IE00ETF", new BigDecimal("300000"))))
            .modelWeights(List.of(new ModelWeight("IE00ETF", new BigDecimal("1.00"))))
            .grossPortfolioValue(new BigDecimal("600000"))
            .cashBuffer(new BigDecimal("10000"))
            .liabilities(ZERO)
            .freeCash(new BigDecimal("90000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .instrumentTypes(Map.of("IE00ETF", InstrumentType.ETF))
            .orderVenues(Map.of())
            .build();

    var trades =
        List.of(
            new TradeCalculation(
                "IE00ETF", new BigDecimal("50000"), new BigDecimal("0.55"), LimitStatus.OK));

    var calculationResult = new FundCalculationResult(TUV100, BUY, input, trades);

    when(inputService.gatherInput(TUV100, command.getAsOfDate(), Map.of())).thenReturn(input);
    when(calculationEngine.calculate(input, BUY)).thenReturn(calculationResult);
    when(batchRepository.save(any(TransactionBatch.class)))
        .thenAnswer(
            invocation -> {
              TransactionBatch batch = invocation.getArgument(0);
              batch.setId(1L);
              return batch;
            });
    when(positionPriceResolver.resolve("IE00ETF", LocalDate.of(2026, 1, 15)))
        .thenReturn(Optional.empty());

    var result = service.processCommand(command);

    var etfOrder = orderByIsin(result, "IE00ETF");
    assertThat(etfOrder.getOrderQuantity()).isNull();
  }

  @Test
  void processCommand_leavesEtfOrderQuantityNullWhenPriceIsZero() {
    var command = givenSingleEtfBuyCommandPricedAt(ZERO);

    var result = service.processCommand(command);

    assertThat(orderByIsin(result, "IE00ETF").getOrderQuantity()).isNull();
  }

  @Test
  void processCommand_leavesEtfOrderQuantityNullWhenPriceIsNegative() {
    var command = givenSingleEtfBuyCommandPricedAt(new BigDecimal("-3"));

    var result = service.processCommand(command);

    assertThat(orderByIsin(result, "IE00ETF").getOrderQuantity()).isNull();
  }

  @Test
  void processCommand_onUnexpectedRuntimeException_setsFailedStatusInsteadOfPropagating() {
    var command =
        TransactionCommand.builder()
            .id(10L)
            .fund(TUV100)
            .mode(BUY)
            .asOfDate(LocalDate.of(2026, 1, 15))
            .manualAdjustments(Map.of())
            .status(PROCESSING)
            .build();

    given(clock.instant()).willReturn(Instant.parse("2026-01-15T10:00:00Z"));
    given(inputService.gatherInput(any(), any(), any()))
        .willThrow(new ArithmeticException("/ by zero"));

    var result = service.processCommand(command);

    assertThat(result).isNull();
    assertThat(command.getStatus()).isEqualTo(FAILED);
    assertThat(command.getErrorMessage()).isNotNull();
    assertThat(command.getProcessedAt()).isNotNull();
  }

  @Test
  void finalizeConfirmedBatch_failsWhenNonAmountOrderHasNullQuantity_withoutGeneratingExports() {
    given(clock.instant()).willReturn(Instant.parse("2026-01-15T10:00:00Z"));
    given(clock.getZone()).willReturn(ZoneId.of("Europe/Tallinn"));

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
            .orderStatus(OrderStatus.PENDING)
            .build();

    given(orderRepository.findByBatchId(batch.getId()))
        .willReturn(List.of(etfOrderWithoutQuantity));

    assertThatThrownBy(() -> service.finalizeConfirmedBatch(batch))
        .isInstanceOf(IllegalStateException.class);

    assertThat(batch.getStatus()).isEqualTo(CONFIRMED);
    verifyNoInteractions(exportService);
    verify(orderRepository, never()).saveAll(any());
  }

  private TransactionCommand givenSingleEtfBuyCommandPricedAt(BigDecimal price) {
    var command =
        TransactionCommand.builder()
            .id(9L)
            .fund(TUV100)
            .mode(BUY)
            .asOfDate(LocalDate.of(2026, 1, 15))
            .manualAdjustments(Map.of())
            .status(PROCESSING)
            .build();
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(List.of(new PositionSnapshot("IE00ETF", new BigDecimal("300000"))))
            .modelWeights(List.of(new ModelWeight("IE00ETF", new BigDecimal("1.00"))))
            .grossPortfolioValue(new BigDecimal("600000"))
            .cashBuffer(new BigDecimal("10000"))
            .liabilities(ZERO)
            .freeCash(new BigDecimal("90000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .instrumentTypes(Map.of("IE00ETF", InstrumentType.ETF))
            .orderVenues(Map.of())
            .build();
    var trades =
        List.of(
            new TradeCalculation(
                "IE00ETF", new BigDecimal("50000"), new BigDecimal("0.55"), LimitStatus.OK));
    var calculationResult = new FundCalculationResult(TUV100, BUY, input, trades);
    given(inputService.gatherInput(TUV100, command.getAsOfDate(), Map.of())).willReturn(input);
    given(calculationEngine.calculate(input, BUY)).willReturn(calculationResult);
    given(batchRepository.save(any(TransactionBatch.class)))
        .willAnswer(
            invocation -> {
              TransactionBatch batch = invocation.getArgument(0);
              batch.setId(1L);
              return batch;
            });
    given(positionPriceResolver.resolve("IE00ETF", LocalDate.of(2026, 1, 15)))
        .willReturn(Optional.of(ResolvedPrice.builder().usedPrice(price).build()));
    return command;
  }

  @Test
  void processCommand_setsFundSellOrderQuantityFromLatestPriceAndLeavesFundBuyQuantityNull() {
    var command =
        TransactionCommand.builder()
            .id(6L)
            .fund(TUV100)
            .mode(SELL)
            .asOfDate(LocalDate.of(2026, 1, 15))
            .manualAdjustments(Map.of())
            .status(PROCESSING)
            .build();

    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("LU00FUND", new BigDecimal("300000")),
                    new PositionSnapshot("LU00FUND2", new BigDecimal("100000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("LU00FUND", new BigDecimal("0.60")),
                    new ModelWeight("LU00FUND2", new BigDecimal("0.40"))))
            .grossPortfolioValue(new BigDecimal("600000"))
            .cashBuffer(new BigDecimal("10000"))
            .liabilities(ZERO)
            .freeCash(new BigDecimal("-50000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .instrumentTypes(
                Map.of(
                    "LU00FUND", InstrumentType.FUND,
                    "LU00FUND2", InstrumentType.FUND))
            .orderVenues(Map.of())
            .build();

    var trades =
        List.of(
            new TradeCalculation(
                "LU00FUND", new BigDecimal("-50000"), new BigDecimal("0.55"), LimitStatus.OK),
            new TradeCalculation(
                "LU00FUND2", new BigDecimal("40000"), new BigDecimal("0.42"), LimitStatus.OK));

    var calculationResult = new FundCalculationResult(TUV100, SELL, input, trades);

    given(inputService.gatherInput(TUV100, command.getAsOfDate(), Map.of())).willReturn(input);
    given(calculationEngine.calculate(input, SELL)).willReturn(calculationResult);
    given(batchRepository.save(any(TransactionBatch.class)))
        .willAnswer(
            invocation -> {
              TransactionBatch batch = invocation.getArgument(0);
              batch.setId(1L);
              return batch;
            });
    given(positionPriceResolver.resolve("LU00FUND", LocalDate.of(2026, 1, 15)))
        .willReturn(Optional.of(ResolvedPrice.builder().usedPrice(new BigDecimal("12.5")).build()));

    var result = service.processCommand(command);

    var fundSellOrder = orderByIsin(result, "LU00FUND");
    assertThat(fundSellOrder.getTransactionType()).isEqualTo(TransactionType.SELL);
    assertThat(fundSellOrder.getOrderQuantity()).isEqualTo(new BigDecimal("4000.000000"));

    var fundBuyOrder = orderByIsin(result, "LU00FUND2");
    assertThat(fundBuyOrder.getTransactionType()).isEqualTo(TransactionType.BUY);
    assertThat(fundBuyOrder.getOrderQuantity()).isNull();
  }

  @Test
  void processCommand_leavesFundSellOrderQuantityNullWhenNoPriceAvailable() {
    var command =
        TransactionCommand.builder()
            .id(7L)
            .fund(TUV100)
            .mode(SELL)
            .asOfDate(LocalDate.of(2026, 1, 15))
            .manualAdjustments(Map.of())
            .status(PROCESSING)
            .build();

    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(List.of(new PositionSnapshot("LU00FUND", new BigDecimal("300000"))))
            .modelWeights(List.of(new ModelWeight("LU00FUND", new BigDecimal("1.00"))))
            .grossPortfolioValue(new BigDecimal("600000"))
            .cashBuffer(new BigDecimal("10000"))
            .liabilities(ZERO)
            .freeCash(new BigDecimal("-50000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .instrumentTypes(Map.of("LU00FUND", InstrumentType.FUND))
            .orderVenues(Map.of())
            .build();

    var trades =
        List.of(
            new TradeCalculation(
                "LU00FUND", new BigDecimal("-50000"), new BigDecimal("0.55"), LimitStatus.OK));

    var calculationResult = new FundCalculationResult(TUV100, SELL, input, trades);

    given(inputService.gatherInput(TUV100, command.getAsOfDate(), Map.of())).willReturn(input);
    given(calculationEngine.calculate(input, SELL)).willReturn(calculationResult);
    given(batchRepository.save(any(TransactionBatch.class)))
        .willAnswer(
            invocation -> {
              TransactionBatch batch = invocation.getArgument(0);
              batch.setId(1L);
              return batch;
            });
    given(positionPriceResolver.resolve("LU00FUND", LocalDate.of(2026, 1, 15)))
        .willReturn(Optional.empty());

    var result = service.processCommand(command);

    var fundSellOrder = orderByIsin(result, "LU00FUND");
    assertThat(fundSellOrder.getOrderQuantity()).isNull();
  }

  private static TransactionOrder orderByIsin(ProcessCommandResult result, String isin) {
    return result.orders().stream()
        .filter(order -> order.getInstrumentIsin().equals(isin))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void processCommand_withNegativeTradeAmount_createsSellOrder() {
    var command =
        TransactionCommand.builder()
            .id(3L)
            .fund(TUV100)
            .mode(SELL)
            .asOfDate(LocalDate.of(2026, 1, 15))
            .manualAdjustments(Map.of())
            .status(PROCESSING)
            .build();

    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(List.of(new PositionSnapshot("IE00A", new BigDecimal("500000"))))
            .modelWeights(List.of(new ModelWeight("IE00A", new BigDecimal("1.00"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(new BigDecimal("50000"))
            .liabilities(ZERO)
            .freeCash(new BigDecimal("-100000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var trades =
        List.of(
            new TradeCalculation(
                "IE00A", new BigDecimal("-100000"), new BigDecimal("0.40"), LimitStatus.OK));

    var calculationResult = new FundCalculationResult(TUV100, SELL, input, trades);

    when(inputService.gatherInput(TUV100, command.getAsOfDate(), Map.of())).thenReturn(input);
    when(calculationEngine.calculate(input, SELL)).thenReturn(calculationResult);
    when(batchRepository.save(any(TransactionBatch.class)))
        .thenAnswer(
            invocation -> {
              TransactionBatch batch = invocation.getArgument(0);
              batch.setId(1L);
              return batch;
            });

    var result = service.processCommand(command);

    assertThat(result.orders())
        .singleElement()
        .satisfies(
            order -> {
              assertThat(order.getTransactionType()).isEqualTo(TransactionType.SELL);
              assertThat(order.getOrderAmount()).isEqualByComparingTo(new BigDecimal("100000"));
            });
  }

  @Test
  void finalizeConfirmedBatch_uploadsToDriveWhenEnabled() {
    when(clock.instant()).thenReturn(Instant.parse("2026-01-15T10:00:00Z"));
    when(clock.getZone()).thenReturn(ZoneId.of("Europe/Tallinn"));

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
            .orderStatus(OrderStatus.PENDING)
            .build();

    when(orderRepository.findByBatchId(batch.getId())).thenReturn(List.of(order));
    when(settlementDateCalculator.calculateSettlementDate(any(), any(), any()))
        .thenReturn(LocalDate.of(2026, 1, 19));
    when(modelPortfolioAllocationRepository.findLatestByFundAsOf(TUV100, LocalDate.of(2026, 1, 15)))
        .thenReturn(List.of());
    when(exportService.generateOrdersExport(any())).thenReturn(new byte[] {1});
    when(exportService.generateSebFundExport(any(), any())).thenReturn(new byte[] {2});
    when(exportService.generateSebEtfExport(any(), any())).thenReturn(new byte[] {3});
    when(exportService.generateFtEtfExport(any(), any(), any())).thenReturn(new byte[] {4});
    when(driveProperties.enabled()).thenReturn(true);
    when(driveProperties.rootFolderId()).thenReturn("root-folder-id");
    when(exportUploader.uploadExports(any(), any(), any(), any()))
        .thenReturn(Map.of("sebFundXlsx", "https://drive.google.com/file1"));

    service.finalizeConfirmedBatch(batch);

    assertThat(batch.getMetadata()).containsKey("driveFileUrls");
    verify(exportUploader).uploadExports(eq("root-folder-id"), eq(TUV100), any(), any());
  }

  @Test
  void finalizeConfirmedBatch_uploadsToDriveOnlyAfterCommit() {
    when(clock.instant()).thenReturn(Instant.parse("2026-01-15T10:00:00Z"));
    when(clock.getZone()).thenReturn(ZoneId.of("Europe/Tallinn"));

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
            .orderStatus(OrderStatus.PENDING)
            .build();

    given(orderRepository.findByBatchId(batch.getId())).willReturn(List.of(order));
    given(settlementDateCalculator.calculateSettlementDate(any(), any(), any()))
        .willReturn(LocalDate.of(2026, 1, 19));
    given(
            modelPortfolioAllocationRepository.findLatestByFundAsOf(
                TUV100, LocalDate.of(2026, 1, 15)))
        .willReturn(List.of());
    given(exportService.generateOrdersExport(any())).willReturn(new byte[] {1});
    given(exportService.generateSebFundExport(any(), any())).willReturn(new byte[] {2});
    given(exportService.generateSebEtfExport(any(), any())).willReturn(new byte[] {3});
    given(exportService.generateFtEtfExport(any(), any(), any())).willReturn(new byte[] {4});
    given(driveProperties.enabled()).willReturn(true);
    given(driveProperties.rootFolderId()).willReturn("root-folder-id");
    given(exportUploader.uploadExports(any(), any(), any(), any()))
        .willReturn(Map.of("sebFundXlsx", "https://drive.google.com/file1"));

    TransactionSynchronizationManager.initSynchronization();

    service.finalizeConfirmedBatch(batch);

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
