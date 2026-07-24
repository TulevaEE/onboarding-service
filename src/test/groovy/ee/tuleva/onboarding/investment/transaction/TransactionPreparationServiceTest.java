package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static ee.tuleva.onboarding.investment.epis.SettlementTimingWarning.Type.PEVA_DEADLINE_MISS;
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
import ee.tuleva.onboarding.comparisons.fundvalue.PriceSource;
import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.comparisons.fundvalue.ValidationStatus;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
  @Mock private SettlementTimingWarningService settlementTimingWarningService;
  @Mock private Clock clock;

  @InjectMocks private TransactionPreparationService service;

  @AfterEach
  void clearSynchronization() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  private static BigDecimal netInvestable(FundTransactionInput input) {
    return input
        .grossPortfolioValue()
        .subtract(input.cashBuffer())
        .subtract(input.liabilities())
        .add(input.receivables() == null ? ZERO : input.receivables());
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

    var calculationResult =
        new FundCalculationResult(TUV100, BUY, input, trades, netInvestable(input), null);

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
    assertThat(result.batch().getStatus()).isEqualTo(BatchStatus.DRAFT);
    assertThat(result.orders())
        .singleElement()
        .satisfies(
            order -> {
              assertThat(order.getOrderUuid()).isNotNull();
              assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.DRAFT);
            });

    verify(auditEventRepository).save(any(TransactionAuditEvent.class));

    assertThat(command.getStatus()).isEqualTo(CALCULATED);
    assertThat(command.getBatchId()).isNotNull();
  }

  @Test
  void processCommand_withCommandCash_passesCashOverrideToInputService() {
    var command =
        TransactionCommand.builder()
            .id(20L)
            .fund(TUV100)
            .mode(BUY)
            .asOfDate(LocalDate.of(2026, 1, 15))
            .manualAdjustments(Map.of())
            .cash(new BigDecimal("40000"))
            .status(PROCESSING)
            .build();
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(List.of())
            .modelWeights(List.of())
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(new BigDecimal("40000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();
    var calculationResult =
        new FundCalculationResult(TUV100, BUY, input, List.of(), netInvestable(input), null);
    given(
            inputService.gatherInput(
                TUV100, command.getAsOfDate(), Map.of(), new BigDecimal("40000")))
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

    verify(inputService)
        .gatherInput(TUV100, command.getAsOfDate(), Map.of(), new BigDecimal("40000"));
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

    var calculationResult =
        new FundCalculationResult(TUV100, BUY, input, trades, netInvestable(input), null);

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

    var expectedInput = TransactionPreparationService.serializeInput(input, manualAdjustments);
    verify(auditEventRepository)
        .save(
            argThat(
                event ->
                    "CALCULATION_COMPLETED".equals(event.getEventType())
                        && event.getPayload().get("input").equals(expectedInput)
                        && event.getPayload().containsKey("output")
                        && event.getPayload().containsKey("priceResolutions")
                        && summaryTradeCount(event).equals(1)));
  }

  @SuppressWarnings("unchecked")
  private static Object summaryTradeCount(TransactionAuditEvent event) {
    return ((Map<String, Object>) event.getPayload().get("summary")).get("tradeCount");
  }

  @SuppressWarnings("unchecked")
  private static Object summaryValue(TransactionAuditEvent event, String key) {
    return ((Map<String, Object>) event.getPayload().get("summary")).get(key);
  }

  @Test
  @SuppressWarnings("unchecked")
  void processCommand_recordsFundScopedSettlementWarnings() {
    var asOfDate = LocalDate.of(2026, 1, 15);
    var command =
        TransactionCommand.builder()
            .id(12L)
            .fund(TUK75)
            .mode(BUY)
            .asOfDate(asOfDate)
            .manualAdjustments(Map.of())
            .status(PROCESSING)
            .build();
    var input =
        FundTransactionInput.builder()
            .fund(TUK75)
            .positions(List.of())
            .modelWeights(List.of())
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(ZERO)
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();
    var calculationResult =
        new FundCalculationResult(TUK75, BUY, input, List.of(), netInvestable(input), null);
    given(inputService.gatherInput(TUK75, asOfDate, Map.of())).willReturn(input);
    given(calculationEngine.calculate(input, BUY)).willReturn(calculationResult);
    given(batchRepository.save(any(TransactionBatch.class)))
        .willAnswer(
            invocation -> {
              TransactionBatch batch = invocation.getArgument(0);
              batch.setId(1L);
              return batch;
            });
    var warning =
        new SettlementTimingWarning(
            PEVA_DEADLINE_MISS,
            TUK75,
            LocalDate.of(2026, 5, 4),
            LocalDate.of(2026, 5, 1),
            "FUND sell settles after execution");
    given(settlementTimingWarningService.activeWarnings(TUK75, asOfDate))
        .willReturn(List.of(warning));

    service.processCommand(command);

    verify(auditEventRepository)
        .save(
            argThat(
                event -> {
                  var warnings =
                      (List<Map<String, Object>>) event.getPayload().get("settlementWarnings");
                  return warnings.size() == 1
                      && "PEVA_DEADLINE_MISS".equals(warnings.get(0).get("type"))
                      && "TUK75".equals(warnings.get(0).get("fund"))
                      && "FUND sell settles after execution".equals(warnings.get(0).get("message"));
                }));
  }

  @Test
  void processCommand_recordsNoTradeReasonInSummaryWhenPresent() {
    var command =
        TransactionCommand.builder()
            .id(13L)
            .fund(TUV100)
            .mode(BUY)
            .asOfDate(LocalDate.of(2026, 1, 15))
            .manualAdjustments(Map.of())
            .status(PROCESSING)
            .build();
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(List.of())
            .modelWeights(List.of())
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(new BigDecimal("1000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();
    var calculationResult =
        new FundCalculationResult(
            TUV100,
            BUY,
            input,
            List.of(),
            netInvestable(input),
            "No trades: mode=BUY, reason=freeCashBelowMinTransactionThreshold");
    given(inputService.gatherInput(TUV100, command.getAsOfDate(), Map.of())).willReturn(input);
    given(calculationEngine.calculate(input, BUY)).willReturn(calculationResult);
    given(batchRepository.save(any(TransactionBatch.class)))
        .willAnswer(
            invocation -> {
              TransactionBatch batch = invocation.getArgument(0);
              batch.setId(1L);
              return batch;
            });

    service.processCommand(command);

    verify(auditEventRepository)
        .save(
            argThat(
                event ->
                    "No trades: mode=BUY, reason=freeCashBelowMinTransactionThreshold"
                        .equals(summaryValue(event, "noTradeReason"))));
  }

  @Test
  void processCommand_usesCommandActorForBatchCreatorAndAuditActor() {
    var command =
        TransactionCommand.builder()
            .id(16L)
            .fund(TUV100)
            .mode(BUY)
            .asOfDate(LocalDate.of(2026, 1, 15))
            .manualAdjustments(Map.of())
            .actor("operator-7")
            .status(PROCESSING)
            .build();
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(List.of())
            .modelWeights(List.of())
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(ZERO)
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();
    var calculationResult =
        new FundCalculationResult(TUV100, BUY, input, List.of(), netInvestable(input), null);
    given(inputService.gatherInput(TUV100, command.getAsOfDate(), Map.of())).willReturn(input);
    given(calculationEngine.calculate(input, BUY)).willReturn(calculationResult);
    given(batchRepository.save(any(TransactionBatch.class)))
        .willAnswer(
            invocation -> {
              TransactionBatch batch = invocation.getArgument(0);
              batch.setId(1L);
              return batch;
            });

    var result = service.processCommand(command);

    assertThat(result.batch().getCreatedBy()).isEqualTo("operator-7");
    verify(auditEventRepository).save(argThat(event -> "operator-7".equals(event.getActor())));
  }

  @Test
  void processCommand_actorlessCommandFallsBackToSystemActor() {
    var command =
        TransactionCommand.builder()
            .id(17L)
            .fund(TUV100)
            .mode(BUY)
            .asOfDate(LocalDate.of(2026, 1, 15))
            .manualAdjustments(Map.of())
            .status(PROCESSING)
            .build();
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(List.of())
            .modelWeights(List.of())
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(ZERO)
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();
    var calculationResult =
        new FundCalculationResult(TUV100, BUY, input, List.of(), netInvestable(input), null);
    given(inputService.gatherInput(TUV100, command.getAsOfDate(), Map.of())).willReturn(input);
    given(calculationEngine.calculate(input, BUY)).willReturn(calculationResult);
    given(batchRepository.save(any(TransactionBatch.class)))
        .willAnswer(
            invocation -> {
              TransactionBatch batch = invocation.getArgument(0);
              batch.setId(1L);
              return batch;
            });

    var result = service.processCommand(command);

    assertThat(result.batch().getCreatedBy()).isEqualTo("system");
    verify(auditEventRepository).save(argThat(event -> "system".equals(event.getActor())));
  }

  @Test
  void processCommand_recordsNetInvestableInSummary() {
    var command =
        TransactionCommand.builder()
            .id(11L)
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
    var trades = List.<TradeCalculation>of();
    var calculationResult =
        new FundCalculationResult(TUV100, BUY, input, trades, new BigDecimal("950000"), null);
    given(inputService.gatherInput(TUV100, command.getAsOfDate(), Map.of())).willReturn(input);
    given(calculationEngine.calculate(input, BUY)).willReturn(calculationResult);
    given(batchRepository.save(any(TransactionBatch.class)))
        .willAnswer(
            invocation -> {
              TransactionBatch batch = invocation.getArgument(0);
              batch.setId(1L);
              return batch;
            });

    service.processCommand(command);

    verify(auditEventRepository)
        .save(argThat(event -> "950000".equals(summaryValue(event, "netInvestable"))));
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
  @SuppressWarnings("unchecked")
  void serializeInput_serializesPositionQuantityUnitPriceAndCurrentWeight() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot(
                        "IE00A",
                        new BigDecimal("500000"),
                        new BigDecimal("1000"),
                        new BigDecimal("500"))))
            .modelWeights(List.of(new ModelWeight("IE00A", new BigDecimal("1.00"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(new BigDecimal("50000"))
            .liabilities(ZERO)
            .receivables(ZERO)
            .freeCash(new BigDecimal("100000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var result = TransactionPreparationService.serializeInput(input, Map.of());

    var positions = (List<Map<String, Object>>) result.get("positions");
    assertThat(positions)
        .singleElement()
        .satisfies(
            position -> {
              assertThat(position).containsEntry("isin", "IE00A");
              assertThat(position).containsEntry("marketValue", "500000");
              assertThat(position).containsEntry("quantity", "1000");
              assertThat(position).containsEntry("unitPrice", "500");
              assertThat(position).containsEntry("currentWeight", "0.500000");
            });
  }

  @Test
  @SuppressWarnings("unchecked")
  void serializeInput_omitsCurrentWeightWhenGrossPortfolioValueIsZero() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(List.of(new PositionSnapshot("IE00A", new BigDecimal("500000"))))
            .modelWeights(List.of())
            .grossPortfolioValue(ZERO)
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .receivables(ZERO)
            .freeCash(ZERO)
            .minTransactionThreshold(ZERO)
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var result = TransactionPreparationService.serializeInput(input, Map.of());

    var positions = (List<Map<String, Object>>) result.get("positions");
    assertThat(positions)
        .singleElement()
        .satisfies(position -> assertThat(position).doesNotContainKey("currentWeight"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void serializeInput_serializesLiabilityBreakdownAndCashTriple() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(List.of())
            .modelWeights(List.of())
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(ZERO)
            .liabilities(new BigDecimal("9000"))
            .receivables(ZERO)
            .freeCash(new BigDecimal("91000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .liabilityBreakdown(
                new LiabilityBreakdown(
                    new BigDecimal("3000"),
                    new BigDecimal("2000"),
                    ZERO,
                    ZERO,
                    new BigDecimal("-4000"),
                    new BigDecimal("1500"),
                    new BigDecimal("500"),
                    ZERO,
                    ZERO,
                    ZERO))
            .reportCash(new BigDecimal("100000"))
            .appliedCash(new BigDecimal("100000"))
            .ledgerCash(new BigDecimal("95000"))
            .build();

    var result = TransactionPreparationService.serializeInput(input, Map.of());

    var breakdown = (Map<String, Object>) result.get("liabilityBreakdown");
    assertThat(breakdown)
        .containsEntry("managementFee", "3000")
        .containsEntry("depotFee", "2000")
        .containsEntry("r45Net", "-4000")
        .containsEntry("pendingBuys", "1500")
        .containsEntry("pendingSells", "500");
    assertThat(result).containsEntry("reportCash", "100000");
    assertThat(result).containsEntry("appliedCash", "100000");
    assertThat(result).containsEntry("ledgerCash", "95000");
    assertThat(result).containsEntry("cashDifference", "5000");
  }

  @Test
  void serializeTrades_serializesUntradedIsinsWithBaseShape() {
    var trades =
        List.of(
            new TradeCalculation(
                "IE00A", new BigDecimal("100000"), new BigDecimal("0.60"), LimitStatus.OK));

    var result = TransactionPreparationService.serializeTrades(trades, Map.of());

    assertThat(result)
        .singleElement()
        .satisfies(
            trade -> {
              assertThat(trade)
                  .containsOnlyKeys("isin", "tradeAmount", "projectedWeight", "limitStatus");
              assertThat(trade).containsEntry("isin", "IE00A");
              assertThat(trade).containsEntry("tradeAmount", "100000");
              assertThat(trade).containsEntry("projectedWeight", "0.60");
              assertThat(trade).containsEntry("limitStatus", "OK");
            });
  }

  @Test
  void serializeTrades_enrichesExecutedTradesFromOrders() {
    var trades =
        List.of(
            new TradeCalculation(
                "IE00ETF", new BigDecimal("50000"), new BigDecimal("0.55"), LimitStatus.OK),
            new TradeCalculation("LU00UNTRADED", ZERO, new BigDecimal("0.10"), LimitStatus.OK));
    var order =
        TransactionOrder.builder()
            .instrumentIsin("IE00ETF")
            .transactionType(TransactionType.BUY)
            .instrumentType(InstrumentType.ETF)
            .orderVenue(OrderVenue.SEB)
            .orderQuantity(new BigDecimal("16666.666667"))
            .build();

    var result = TransactionPreparationService.serializeTrades(trades, Map.of("IE00ETF", order));

    assertThat(result.get(0))
        .containsEntry("isin", "IE00ETF")
        .containsEntry("tradeAmount", "50000")
        .containsEntry("quantity", "16666.666667")
        .containsEntry("side", "BUY")
        .containsEntry("venue", "SEB")
        .containsEntry("instrumentType", "ETF");
    assertThat(result.get(1))
        .containsOnlyKeys("isin", "tradeAmount", "projectedWeight", "limitStatus");
  }

  @Test
  void serializePriceResolutions_serializesResolvedPriceFieldsAndTolersatesNull() {
    var resolved =
        ResolvedPrice.builder()
            .usedPrice(new BigDecimal("12.5"))
            .priceSource(PriceSource.EODHD)
            .validationStatus(ValidationStatus.OK)
            .priceDate(LocalDate.of(2026, 1, 14))
            .build();
    var resolutions = new java.util.LinkedHashMap<String, ResolvedPrice>();
    resolutions.put("IE00ETF", resolved);
    resolutions.put("LU00NOPRICE", null);

    var result = TransactionPreparationService.serializePriceResolutions(resolutions);

    assertThat(result.get(0))
        .containsEntry("isin", "IE00ETF")
        .containsEntry("price", "12.5")
        .containsEntry("priceDate", "2026-01-14")
        .containsEntry("priceSource", "EODHD")
        .containsEntry("validationStatus", "OK");
    assertThat(result.get(1)).containsOnlyKeys("isin");
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
  void processCommand_onEarlyFailure_writesCalculationFailedWithoutBatchOrInput() {
    var command =
        TransactionCommand.builder()
            .id(14L)
            .fund(TUV100)
            .mode(BUY)
            .asOfDate(LocalDate.of(2026, 1, 15))
            .manualAdjustments(Map.of("additionalLiabilities", "1000"))
            .status(PROCESSING)
            .build();

    given(clock.instant()).willReturn(Instant.parse("2026-01-15T10:00:00Z"));
    given(inputService.gatherInput(any(), any(), any()))
        .willThrow(new IllegalStateException("No position data found"));

    service.processCommand(command);

    verify(auditEventRepository)
        .save(
            argThat(
                event ->
                    "CALCULATION_FAILED".equals(event.getEventType())
                        && "14".equals(event.getDedupKey())
                        && event.getBatch() == null
                        && "TUV100".equals(event.getPayload().get("fund"))
                        && "2026-01-15".equals(event.getPayload().get("asOfDate"))
                        && "java.lang.IllegalStateException"
                            .equals(event.getPayload().get("exceptionClass"))
                        && "No position data found".equals(event.getPayload().get("errorMessage"))
                        && event.getPayload().containsKey("manualAdjustments")
                        && !event.getPayload().containsKey("input")));
  }

  @Test
  void processCommand_onFailureAfterBatchCreated_attachesBatchAndInput() {
    var command =
        TransactionCommand.builder()
            .id(15L)
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
            .receivables(ZERO)
            .freeCash(new BigDecimal("100000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();
    var trades =
        List.of(
            new TradeCalculation(
                "IE00A", new BigDecimal("100000"), new BigDecimal("0.60"), LimitStatus.OK));
    var calculationResult =
        new FundCalculationResult(TUV100, BUY, input, trades, netInvestable(input), null);

    given(clock.instant()).willReturn(Instant.parse("2026-01-15T10:00:00Z"));
    given(inputService.gatherInput(TUV100, command.getAsOfDate(), Map.of())).willReturn(input);
    given(calculationEngine.calculate(input, BUY)).willReturn(calculationResult);
    given(batchRepository.save(any(TransactionBatch.class)))
        .willAnswer(
            invocation -> {
              TransactionBatch batch = invocation.getArgument(0);
              batch.setId(1L);
              return batch;
            });
    given(orderRepository.saveAll(any())).willThrow(new IllegalStateException("db down"));

    service.processCommand(command);

    verify(auditEventRepository)
        .save(
            argThat(
                event ->
                    "CALCULATION_FAILED".equals(event.getEventType())
                        && event.getBatch() != null
                        && TUV100.equals(event.getBatch().getFund())
                        && event.getPayload().containsKey("input")));
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

    service.finalizeConfirmedBatch(batch);

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
    verify(auditEventRepository).save(any(TransactionAuditEvent.class));
    verify(eventPublisher).publishEvent(any(BatchFinalizedEvent.class));
  }

  @Test
  void finalizeConfirmedBatch_derivesTradeDateInTallinnZoneNotClockZone() {
    // 23:30 UTC on 2026-01-15 is already 01:30 on 2026-01-16 in Europe/Tallinn. The production
    // clock is Clock.systemUTC(), so deriving the trade date from the clock's own zone would stamp
    // 2026-01-15; the operational trade date is the Tallinn business day 2026-01-16.
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

    service.finalizeConfirmedBatch(batch);

    verify(modelPortfolioAllocationRepository)
        .findLatestByFundAsOf(TUV100, LocalDate.of(2026, 1, 16));
    verify(auditEventRepository)
        .save(argThat(event -> "2026-01-16".equals(event.getPayload().get("tradeDate"))));
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

    var calculationResult =
        new FundCalculationResult(TUV100, BUY, input, trades, netInvestable(input), null);

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

    var calculationResult =
        new FundCalculationResult(TUV100, BUY, input, trades, netInvestable(input), null);

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

    var calculationResult =
        new FundCalculationResult(TUV100, BUY, input, trades, netInvestable(input), null);

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

    assertThatThrownBy(() -> service.finalizeConfirmedBatch(batch))
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

    assertThatThrownBy(() -> service.finalizeConfirmedBatch(batch))
        .isInstanceOf(IllegalStateException.class);

    verifyNoInteractions(exportService);
    verify(orderRepository, never()).saveAll(any());
  }

  private TransactionCommand givenSingleEtfBuyCommandPricedAt(BigDecimal price) {
    return givenSingleEtfBuyCommandPricedAt(price, LocalDate.of(2026, 1, 15), null);
  }

  private TransactionCommand givenSingleEtfBuyCommandPricedAt(
      BigDecimal price, LocalDate priceDate, PriceSource priceSource) {
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
    var calculationResult =
        new FundCalculationResult(TUV100, BUY, input, trades, netInvestable(input), null);
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
        .willReturn(
            Optional.of(
                ResolvedPrice.builder()
                    .usedPrice(price)
                    .priceDate(priceDate)
                    .priceSource(priceSource)
                    .build()));
    return command;
  }

  @Test
  void processCommand_flagsOrderCommentWhenResolvedPriceIsStale() {
    var command =
        givenSingleEtfBuyCommandPricedAt(
            new BigDecimal("3"), LocalDate.of(2026, 1, 10), PriceSource.EODHD);

    var result = service.processCommand(command);

    var order = orderByIsin(result, "IE00ETF");
    assertThat(order.getComment()).contains("2026-01-10", "5", "EODHD");
  }

  @Test
  void processCommand_doesNotFlagOrderCommentWhenResolvedPriceIsWithinStalenessThreshold() {
    var command =
        givenSingleEtfBuyCommandPricedAt(
            new BigDecimal("3"), LocalDate.of(2026, 1, 12), PriceSource.EODHD);

    var result = service.processCommand(command);

    var order = orderByIsin(result, "IE00ETF");
    assertThat(order.getComment()).isNull();
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

    var calculationResult =
        new FundCalculationResult(TUV100, SELL, input, trades, netInvestable(input), null);

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

    var calculationResult =
        new FundCalculationResult(TUV100, SELL, input, trades, netInvestable(input), null);

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

    var calculationResult =
        new FundCalculationResult(TUV100, SELL, input, trades, netInvestable(input), null);

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

    service.finalizeConfirmedBatch(batch);

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
