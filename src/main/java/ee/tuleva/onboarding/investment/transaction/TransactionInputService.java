package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.config.InvestmentParameter.R16_BUFFER_PERCENT;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.R16_ROUNDING_STEP;
import static ee.tuleva.onboarding.investment.epis.PevaRavaPhase.DONE;
import static ee.tuleva.onboarding.investment.position.AccountType.CASH;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.CEILING;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueQueries;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import ee.tuleva.onboarding.investment.epis.FundCycleTimeline;
import ee.tuleva.onboarding.investment.epis.PevaRavaFlowService;
import ee.tuleva.onboarding.investment.epis.PevaRavaFlows;
import ee.tuleva.onboarding.investment.epis.PevaRavaPeriod;
import ee.tuleva.onboarding.investment.epis.PevaRavaPeriodService;
import ee.tuleva.onboarding.investment.epis.R16FlowCalculationService;
import ee.tuleva.onboarding.investment.epis.R16FundFlow;
import ee.tuleva.onboarding.investment.epis.R16PhaseCalculator;
import ee.tuleva.onboarding.investment.epis.R45ReportService;
import ee.tuleva.onboarding.investment.epis.R45Result;
import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.investment.fees.FeeChargedToFundPolicy;
import ee.tuleva.onboarding.investment.fees.FeeType;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.ledger.SystemAccount;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@NullMarked
public class TransactionInputService {

  private static final Set<TulevaFund> PEVA_RAVA_FUNDS = Set.of(TUK75, TUK00);
  private static final Set<TulevaFund> R16_FUNDS = Set.of(TUK75, TUK00, TUV100);

  private final FundPositionRepository fundPositionRepository;
  private final FeeAccrualRepository feeAccrualRepository;
  private final FeeChargedToFundPolicy feeChargedToFundPolicy;
  private final NavLedgerRepository navLedgerRepository;
  private final FundValueQueries fundValueQueries;
  private final PevaRavaPeriodService pevaRavaPeriodService;
  private final PevaRavaFlowService pevaRavaFlowService;
  private final R45ReportService r45ReportService;
  private final R16FlowCalculationService r16FlowCalculationService;
  private final R16PhaseCalculator r16PhaseCalculator;
  private final InvestmentParameterRepository investmentParameterRepository;
  private final PendingOrderImpactService pendingOrderImpactService;
  private final PositionAssembler positionAssembler;
  private final TransactionParameterLoader transactionParameterLoader;

  public FundTransactionInput gatherInput(
      TulevaFund fund, LocalDate asOfDate, Map<String, Object> manualAdjustments) {
    return gatherInput(fund, asOfDate, manualAdjustments, null);
  }

  public FundTransactionInput gatherInput(
      TulevaFund fund,
      LocalDate asOfDate,
      Map<String, Object> manualAdjustments,
      @Nullable BigDecimal cashOverride) {
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

    PendingOrderImpact pendingOrders =
        pendingOrderImpactService.calculate(fund, asOfDate, positionDate);
    List<PositionSnapshot> positions =
        positionAssembler.assemble(fund, positionDate, pendingOrders);
    BigDecimal reportCash = getCashBalance(fund, positionDate);
    BigDecimal appliedCash = cashOverride == null ? reportCash : cashOverride;
    BigDecimal managementFee = getAccruedFees(fund, asOfDate, FeeType.MANAGEMENT);
    BigDecimal depotFee = getAccruedFees(fund, asOfDate, FeeType.DEPOT);
    TransactionParameters parameters = transactionParameterLoader.load(fund, asOfDate);

    BigDecimal securityValue =
        positions.stream()
            .map(PositionSnapshot::marketValue)
            .filter(Objects::nonNull)
            .reduce(ZERO, BigDecimal::add);
    BigDecimal grossPortfolioValue = securityValue.add(appliedCash);

    BigDecimal unreconciledBankReceipts = ZERO;
    BigDecimal fundUnitsReservedValue = ZERO;
    BigDecimal incomingPaymentsClearing = ZERO;

    BigDecimal liabilities = managementFee.add(depotFee);
    BigDecimal receivables = ZERO;

    if (fund == TKF100) {
      unreconciledBankReceipts =
          navLedgerRepository.getSystemAccountBalance("UNRECONCILED_BANK_RECEIPTS");
      fundUnitsReservedValue = getFundUnitsReservedValue();
      liabilities = liabilities.add(unreconciledBankReceipts).add(fundUnitsReservedValue);
      incomingPaymentsClearing =
          navLedgerRepository.getSystemAccountBalance("INCOMING_PAYMENTS_CLEARING");
      receivables = incomingPaymentsClearing;
    }

    BigDecimal pevaRava = getPevaRavaLiquidity(fund, asOfDate);
    liabilities = liabilities.add(pevaRava);
    BigDecimal r16 = getR16Outflow(fund, asOfDate);
    liabilities = liabilities.add(r16);

    BigDecimal r45Net = getR45Net(fund);
    liabilities = liabilities.add(ZERO.max(r45Net.negate()));
    receivables = receivables.add(ZERO.max(r45Net));

    liabilities = liabilities.add(getAdjustment(manualAdjustments, "additionalLiabilities"));
    receivables = receivables.add(getAdjustment(manualAdjustments, "additionalReceivables"));

    liabilities = liabilities.add(pendingOrders.pendingBuys());
    receivables = receivables.add(pendingOrders.pendingSells());

    BigDecimal freeCash =
        appliedCash.subtract(parameters.cashBuffer()).subtract(liabilities).add(receivables);

    BigDecimal ledgerCash =
        navLedgerRepository.getSystemAccountBalance(
            SystemAccount.CASH_POSITION.getAccountName(fund));

    LiabilityBreakdown liabilityBreakdown =
        new LiabilityBreakdown(
            managementFee,
            depotFee,
            pevaRava,
            r16,
            r45Net,
            pendingOrders.pendingBuys(),
            pendingOrders.pendingSells(),
            unreconciledBankReceipts,
            fundUnitsReservedValue,
            incomingPaymentsClearing);

    return FundTransactionInput.builder()
        .fund(fund)
        .positions(positions)
        .modelWeights(parameters.modelWeights())
        .grossPortfolioValue(grossPortfolioValue)
        .cashBuffer(parameters.cashBuffer())
        .liabilities(liabilities)
        .receivables(receivables)
        .freeCash(freeCash)
        .minTransactionThreshold(parameters.minTransaction())
        .positionLimits(parameters.positionLimits())
        .fastSellIsins(parameters.fastSellIsins())
        .instrumentTypes(parameters.instrumentTypes())
        .orderVenues(parameters.orderVenues())
        .liabilityBreakdown(liabilityBreakdown)
        .reportCash(reportCash)
        .appliedCash(appliedCash)
        .ledgerCash(ledgerCash)
        .positionDate(positionDate)
        .modelEffectiveDate(parameters.modelEffectiveDate())
        .build();
  }

  private BigDecimal getCashBalance(TulevaFund fund, LocalDate date) {
    List<FundPosition> cashPositions =
        fundPositionRepository.findByNavDateAndFundAndAccountType(date, fund, CASH);
    return cashPositions.stream()
        .map(FundPosition::getMarketValue)
        .filter(Objects::nonNull)
        .reduce(ZERO, BigDecimal::add);
  }

  private BigDecimal getAccruedFees(TulevaFund fund, LocalDate asOfDate, FeeType feeType) {
    LocalDate feeMonth = asOfDate.withDayOfMonth(1);
    // Per accrual date, not once for asOfDate: a policy that flips mid-month must not hand its
    // post-flip answer to the days before it.
    return feeChargedToFundPolicy
        .resolverFor(fund, feeType)
        .sumChargedDays(
            feeAccrualRepository.getAccruedFeesByDateForMonth(
                fund, feeMonth, List.of(feeType), asOfDate));
  }

  private BigDecimal getFundUnitsReservedValue() {
    BigDecimal units = navLedgerRepository.getFundUnitsBalance("FUND_UNITS_RESERVED");
    if (units.signum() == 0) {
      return ZERO;
    }
    BigDecimal nav =
        fundValueQueries.findLastValueForFund(TKF100.getIsin()).map(FundValue::value).orElse(ZERO);
    return units.multiply(nav);
  }

  private BigDecimal getPevaRavaLiquidity(TulevaFund fund, LocalDate asOfDate) {
    if (!PEVA_RAVA_FUNDS.contains(fund)) {
      return ZERO;
    }
    Optional<PevaRavaPeriod> period =
        pevaRavaPeriodService.getCurrentPeriod(asOfDate).filter(p -> p.phase() != DONE);
    if (period.isEmpty()) {
      return ZERO;
    }
    FundCycleTimeline timeline = period.get().timelineFor(fund);
    if (!timeline.dActive()) {
      return ZERO;
    }
    return getPevaRavaLiquidity(fund, asOfDate, timeline);
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
    Optional<R16FundFlow> flow = r16FlowCalculationService.calculateFlows(fund, asOfDate);
    if (flow.isEmpty()) {
      return ZERO;
    }
    return getR16Outflow(flow.get(), asOfDate);
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
}
