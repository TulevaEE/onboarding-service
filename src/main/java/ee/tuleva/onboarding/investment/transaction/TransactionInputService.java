package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.R16_BUFFER_PERCENT;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.R16_ROUNDING_STEP;
import static ee.tuleva.onboarding.investment.epis.PevaRavaPhase.DONE;
import static ee.tuleva.onboarding.investment.position.AccountType.CASH;
import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.CEILING;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import ee.tuleva.onboarding.investment.epis.FundCycleTimeline;
import ee.tuleva.onboarding.investment.epis.PevaRavaFlowService;
import ee.tuleva.onboarding.investment.epis.PevaRavaFlows;
import ee.tuleva.onboarding.investment.epis.PevaRavaPeriodService;
import ee.tuleva.onboarding.investment.epis.R16FlowCalculationService;
import ee.tuleva.onboarding.investment.epis.R16FundFlow;
import ee.tuleva.onboarding.investment.epis.R16PhaseCalculator;
import ee.tuleva.onboarding.investment.epis.R45ReportService;
import ee.tuleva.onboarding.investment.epis.R45Result;
import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.investment.fees.FeeType;
import ee.tuleva.onboarding.investment.portfolio.FundLimit;
import ee.tuleva.onboarding.investment.portfolio.FundLimitRepository;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.portfolio.PositionLimit;
import ee.tuleva.onboarding.investment.portfolio.PositionLimitRepository;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@NullMarked
public class TransactionInputService {

  private static final Set<TulevaFund> PEVA_RAVA_FUNDS = Set.of(TUK75, TUK00);
  private static final Set<TulevaFund> R16_FUNDS = Set.of(TUK75, TUK00, TUV100);
  private static final BigDecimal MODEL_WEIGHT_SUM_TOLERANCE = new BigDecimal("0.0001");

  private final FundPositionRepository fundPositionRepository;
  private final FeeAccrualRepository feeAccrualRepository;
  private final ModelPortfolioAllocationRepository modelPortfolioAllocationRepository;
  private final FundLimitRepository fundLimitRepository;
  private final PositionLimitRepository positionLimitRepository;
  private final NavLedgerRepository navLedgerRepository;
  private final FundValueRepository fundValueRepository;
  private final TransactionOrderRepository orderRepository;
  private final PevaRavaPeriodService pevaRavaPeriodService;
  private final PevaRavaFlowService pevaRavaFlowService;
  private final R45ReportService r45ReportService;
  private final R16FlowCalculationService r16FlowCalculationService;
  private final R16PhaseCalculator r16PhaseCalculator;
  private final InvestmentParameterRepository investmentParameterRepository;

  public FundTransactionInput gatherInput(
      TulevaFund fund, LocalDate asOfDate, Map<String, Object> manualAdjustments) {
    LocalDate positionDate =
        fundPositionRepository
            .findLatestNavDateByFundAndAsOfDate(fund, asOfDate)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No position data found: fund=" + fund + ", asOfDate=" + asOfDate));

    log.info(
        "Gathering transaction input: fund={}, asOfDate={}, positionDate={}",
        fund,
        asOfDate,
        positionDate);

    List<PositionSnapshot> positions = getPositions(fund, positionDate);
    BigDecimal cash = getCashBalance(fund, positionDate);
    BigDecimal accruedFees = getAccruedFees(fund, asOfDate);
    List<ModelPortfolioAllocation> allocations = getModelAllocations(fund, asOfDate);
    List<ModelPortfolioAllocation> previousAllocations =
        modelPortfolioAllocationRepository.findPreviousByFundAsOf(fund, asOfDate).stream()
            .filter(a -> a.getIsin() != null)
            .toList();
    List<ModelWeight> modelWeights = toModelWeights(allocations);
    assertModelWeightsSumToOne(fund, modelWeights);
    BigDecimal cashBuffer = getCashBuffer(fund, asOfDate);
    BigDecimal minTransaction = getMinTransaction(fund, asOfDate);
    Map<String, PositionLimitSnapshot> positionLimits = getPositionLimits(fund, asOfDate);
    Set<String> fastSellIsins = getFastSellIsins(allocations, previousAllocations);
    Map<String, InstrumentType> instrumentTypes =
        getInstrumentTypes(allocations, previousAllocations);
    Map<String, OrderVenue> orderVenues = getOrderVenues(allocations, previousAllocations);

    BigDecimal securityValue =
        positions.stream()
            .map(PositionSnapshot::marketValue)
            .filter(Objects::nonNull)
            .reduce(ZERO, BigDecimal::add);
    BigDecimal grossPortfolioValue = securityValue.add(cash);

    BigDecimal liabilities = accruedFees;
    BigDecimal receivables = ZERO;

    if (fund == TKF100) {
      BigDecimal unreconciledBankReceipts =
          navLedgerRepository.getSystemAccountBalance("UNRECONCILED_BANK_RECEIPTS");
      BigDecimal fundUnitsReservedValue = getFundUnitsReservedValue();
      liabilities = liabilities.add(unreconciledBankReceipts).add(fundUnitsReservedValue);
      receivables = navLedgerRepository.getSystemAccountBalance("INCOMING_PAYMENTS_CLEARING");
    }

    liabilities = liabilities.add(getPevaRavaLiquidity(fund, asOfDate));
    liabilities = liabilities.add(getR16Outflow(fund, asOfDate));

    BigDecimal r45Net = getR45Net(fund);
    liabilities = liabilities.add(ZERO.max(r45Net.negate()));
    receivables = receivables.add(ZERO.max(r45Net));

    liabilities = liabilities.add(getAdjustment(manualAdjustments, "additionalLiabilities"));
    receivables = receivables.add(getAdjustment(manualAdjustments, "additionalReceivables"));

    BigDecimal pendingCash = getPendingCashImpact(fund, asOfDate);
    BigDecimal freeCash =
        cash.subtract(cashBuffer).subtract(liabilities).add(receivables).subtract(pendingCash);

    return FundTransactionInput.builder()
        .fund(fund)
        .positions(positions)
        .modelWeights(modelWeights)
        .grossPortfolioValue(grossPortfolioValue)
        .cashBuffer(cashBuffer)
        .liabilities(liabilities)
        .receivables(receivables)
        .freeCash(freeCash)
        .minTransactionThreshold(minTransaction)
        .positionLimits(positionLimits)
        .fastSellIsins(fastSellIsins)
        .instrumentTypes(instrumentTypes)
        .orderVenues(orderVenues)
        .build();
  }

  private List<PositionSnapshot> getPositions(TulevaFund fund, LocalDate date) {
    return fundPositionRepository.findByNavDateAndFundAndAccountType(date, fund, SECURITY).stream()
        .filter(position -> position.getMarketValue() != null)
        .map(position -> new PositionSnapshot(position.getAccountId(), position.getMarketValue()))
        .toList();
  }

  private BigDecimal getCashBalance(TulevaFund fund, LocalDate date) {
    List<FundPosition> cashPositions =
        fundPositionRepository.findByNavDateAndFundAndAccountType(date, fund, CASH);
    return cashPositions.stream()
        .map(FundPosition::getMarketValue)
        .filter(Objects::nonNull)
        .reduce(ZERO, BigDecimal::add);
  }

  private BigDecimal getAccruedFees(TulevaFund fund, LocalDate asOfDate) {
    LocalDate feeMonth = asOfDate.withDayOfMonth(1);
    return feeAccrualRepository.getAccruedFeesForMonth(
        fund, feeMonth, List.of(FeeType.MANAGEMENT, FeeType.DEPOT), asOfDate);
  }

  private List<ModelPortfolioAllocation> getModelAllocations(TulevaFund fund, LocalDate asOfDate) {
    return modelPortfolioAllocationRepository.findLatestByFundAsOf(fund, asOfDate).stream()
        .filter(allocation -> allocation.getIsin() != null)
        .toList();
  }

  private List<ModelWeight> toModelWeights(List<ModelPortfolioAllocation> allocations) {
    return allocations.stream()
        .map(allocation -> new ModelWeight(allocation.getIsin(), allocation.getWeight()))
        .toList();
  }

  private void assertModelWeightsSumToOne(TulevaFund fund, List<ModelWeight> modelWeights) {
    if (modelWeights.isEmpty()) {
      return;
    }
    BigDecimal totalWeight =
        modelWeights.stream().map(ModelWeight::weight).reduce(ZERO, BigDecimal::add);
    if (totalWeight.subtract(ONE).abs().compareTo(MODEL_WEIGHT_SUM_TOLERANCE) > 0) {
      throw new IllegalStateException(
          "Model weights do not sum to 1: fund=" + fund + ", sum=" + totalWeight.toPlainString());
    }
  }

  private BigDecimal getCashBuffer(TulevaFund fund, LocalDate asOfDate) {
    return getFundLimitValue(fund, asOfDate, FundLimit::getReserveSoft, "reserveSoft");
  }

  private BigDecimal getMinTransaction(TulevaFund fund, LocalDate asOfDate) {
    return getFundLimitValue(fund, asOfDate, FundLimit::getMinTransaction, "minTransaction");
  }

  private BigDecimal getFundLimitValue(
      TulevaFund fund,
      LocalDate asOfDate,
      Function<FundLimit, BigDecimal> extractor,
      String fieldName) {
    FundLimit limit =
        fundLimitRepository
            .findLatestByFundAsOf(fund, asOfDate)
            .orElseThrow(() -> new IllegalStateException("No fund limit found: fund=" + fund));
    BigDecimal value = extractor.apply(limit);
    if (value == null) {
      throw new IllegalStateException(
          "Fund limit field is missing: fund=" + fund + ", field=" + fieldName);
    }
    return value;
  }

  private Map<String, PositionLimitSnapshot> getPositionLimits(
      TulevaFund fund, LocalDate asOfDate) {
    return positionLimitRepository.findLatestByFundAsOf(fund, asOfDate).stream()
        .collect(
            Collectors.toMap(
                PositionLimit::getIsin,
                limit ->
                    new PositionLimitSnapshot(
                        limit.getSoftLimitPercent(), limit.getHardLimitPercent()),
                (a, b) -> b));
  }

  private Set<String> getFastSellIsins(
      List<ModelPortfolioAllocation> current, List<ModelPortfolioAllocation> previous) {
    var merged =
        new HashSet<>(
            previous.stream()
                .filter(ModelPortfolioAllocation::isFastSell)
                .map(ModelPortfolioAllocation::getIsin)
                .collect(Collectors.toSet()));
    current.stream()
        .filter(ModelPortfolioAllocation::isFastSell)
        .map(ModelPortfolioAllocation::getIsin)
        .forEach(merged::add);
    return merged;
  }

  private Map<String, InstrumentType> getInstrumentTypes(
      List<ModelPortfolioAllocation> current, List<ModelPortfolioAllocation> previous) {
    var merged =
        new HashMap<>(
            previous.stream()
                .filter(a -> a.getInstrumentType() != null)
                .collect(
                    Collectors.toMap(
                        ModelPortfolioAllocation::getIsin,
                        ModelPortfolioAllocation::getInstrumentType,
                        (a, b) -> b)));
    current.stream()
        .filter(a -> a.getInstrumentType() != null)
        .forEach(a -> merged.put(a.getIsin(), a.getInstrumentType()));
    return merged;
  }

  private BigDecimal getFundUnitsReservedValue() {
    BigDecimal units = navLedgerRepository.getFundUnitsBalance("FUND_UNITS_RESERVED");
    if (units.signum() == 0) {
      return ZERO;
    }
    BigDecimal nav =
        fundValueRepository
            .findLastValueForFund(TKF100.getIsin())
            .map(FundValue::value)
            .orElse(ZERO);
    return units.multiply(nav);
  }

  private BigDecimal getPevaRavaLiquidity(TulevaFund fund, LocalDate asOfDate) {
    if (!PEVA_RAVA_FUNDS.contains(fund)) {
      return ZERO;
    }
    return pevaRavaPeriodService
        .getCurrentPeriod(asOfDate)
        .filter(period -> period.phase() != DONE)
        .map(period -> period.timelineFor(fund))
        .filter(FundCycleTimeline::dActive)
        .map(timeline -> getPevaRavaLiquidity(fund, asOfDate, timeline))
        .orElse(ZERO);
  }

  private BigDecimal getPevaRavaLiquidity(
      TulevaFund fund, LocalDate asOfDate, FundCycleTimeline timeline) {
    PevaRavaFlows flows = pevaRavaFlowService.calculateFlows(asOfDate).get(fund);
    if (flows == null) {
      return ZERO;
    }
    return timeline.sellByReached() ? flows.tradeBufferedLiquidity() : flows.liquidityRequired();
  }

  private BigDecimal getR16Outflow(TulevaFund fund, LocalDate asOfDate) {
    if (!R16_FUNDS.contains(fund)) {
      return ZERO;
    }
    return r16FlowCalculationService
        .calculateFlows(fund, asOfDate)
        .map(flow -> getR16Outflow(flow, asOfDate))
        .orElse(ZERO);
  }

  private BigDecimal getR16Outflow(R16FundFlow flow, LocalDate asOfDate) {
    return switch (r16PhaseCalculator.phaseFor(flow, asOfDate)) {
      case ACTIVE -> flow.totalOutflowEur().abs();
      case BUFFERED -> bufferedR16Outflow(flow.totalOutflowEur(), asOfDate);
      case IGNORE, VISIBLE -> ZERO;
    };
  }

  private BigDecimal bufferedR16Outflow(BigDecimal totalOutflowEur, LocalDate asOfDate) {
    BigDecimal bufferPercent =
        investmentParameterRepository.findLatestValue(R16_BUFFER_PERCENT, asOfDate);
    BigDecimal step = investmentParameterRepository.findLatestValue(R16_ROUNDING_STEP, asOfDate);
    if (step.signum() <= 0) {
      throw new IllegalStateException(
          "Invalid investment parameter: parameter=R16_ROUNDING_STEP, value="
              + step.toPlainString());
    }
    return totalOutflowEur
        .abs()
        .multiply(ONE.add(bufferPercent))
        .divide(step, 0, CEILING)
        .multiply(step);
  }

  private BigDecimal getR45Net(TulevaFund fund) {
    if (r45ReportService.getIncompleteFunds().contains(fund)) {
      throw new IllegalStateException(
          "R45 summary incomplete: fund="
              + fund
              + ", reason=unvalued R45 rows missing NAV, action=supply NAV and reprocess R45");
    }
    R45Result result = r45ReportService.getLatestFlows().get(fund);
    return result == null ? ZERO : result.netEur();
  }

  private BigDecimal getAdjustment(Map<String, Object> adjustments, String key) {
    Object value = adjustments.get(key);
    if (value == null) {
      return ZERO;
    }
    try {
      return new BigDecimal(value.toString());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          "Invalid manual adjustment: key=%s, value=%s".formatted(key, value), e);
    }
  }

  private Map<String, OrderVenue> getOrderVenues(
      List<ModelPortfolioAllocation> current, List<ModelPortfolioAllocation> previous) {
    var merged =
        new HashMap<>(
            previous.stream()
                .filter(a -> a.getOrderVenue() != null)
                .collect(
                    Collectors.toMap(
                        ModelPortfolioAllocation::getIsin,
                        ModelPortfolioAllocation::getOrderVenue,
                        (a, b) -> b)));
    current.stream()
        .filter(a -> a.getOrderVenue() != null)
        .forEach(a -> merged.put(a.getIsin(), a.getOrderVenue()));
    return merged;
  }

  private BigDecimal getPendingCashImpact(TulevaFund fund, LocalDate asOfDate) {
    List<TransactionOrder> unsettledOrders = orderRepository.findUnsettledOrders(fund, asOfDate);
    BigDecimal pendingBuys =
        unsettledOrders.stream()
            .filter(order -> order.getTransactionType() == TransactionType.BUY)
            .map(TransactionOrder::getOrderAmount)
            .filter(Objects::nonNull)
            .reduce(ZERO, BigDecimal::add);
    BigDecimal pendingSells =
        unsettledOrders.stream()
            .filter(order -> order.getTransactionType() == TransactionType.SELL)
            .map(TransactionOrder::getOrderAmount)
            .filter(Objects::nonNull)
            .reduce(ZERO, BigDecimal::add);
    return pendingBuys.subtract(pendingSells);
  }
}
