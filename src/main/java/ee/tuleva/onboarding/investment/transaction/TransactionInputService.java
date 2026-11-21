package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.position.AccountType.CASH;
import static ee.tuleva.onboarding.investment.transaction.CalculationWarningType.FEE_POLICY_UNRESOLVED;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.investment.epis.R45ReportService;
import ee.tuleva.onboarding.investment.epis.R45Result;
import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.investment.fees.FeeChargedToFundPolicy;
import ee.tuleva.onboarding.investment.fees.FeePolicyUnresolvedException;
import ee.tuleva.onboarding.investment.fees.FeeType;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.investment.transaction.GatewayCash.CurrentDayBalance;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.ledger.SystemAccount;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
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

  private final FundPositionRepository fundPositionRepository;
  private final FeeAccrualRepository feeAccrualRepository;
  private final FeeChargedToFundPolicy feeChargedToFundPolicy;
  private final NavLedgerRepository navLedgerRepository;
  private final UnitRegisterCashFlows unitRegisterCashFlows;
  private final PevaRavaForecastReader pevaRavaForecastReader;
  private final R45ReportService r45ReportService;
  private final R16OutflowReader r16OutflowReader;
  private final PendingOrderImpactService pendingOrderImpactService;
  private final PositionAssembler positionAssembler;
  private final UnsettledTradeReader unsettledTradeReader;
  private final UnitReceivableReader unitReceivableReader;
  private final TransactionParameterLoader transactionParameterLoader;
  private final SameDayCashReader sameDayCashReader;
  private final TradeSettlementsInTheBalanceReader tradeSettlementsReader;
  private final GatewayNetting gatewayNetting;

  FundTransactionInput gatherInput(
      TulevaFund fund,
      LocalDate asOfDate,
      Map<String, Object> manualAdjustments,
      TradePrepPricing pricing) {
    return gatherInput(fund, asOfDate, manualAdjustments, null, pricing);
  }

  FundTransactionInput gatherInput(
      TulevaFund fund,
      LocalDate asOfDate,
      Map<String, Object> manualAdjustments,
      @Nullable BigDecimal cashOverride,
      TradePrepPricing pricing) {
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

    BigDecimal reportCash = getCashBalance(fund, positionDate);
    Optional<BigDecimal> pevaRavaInAnActiveCycle =
        pevaRavaForecastReader.liquidityInAnActiveCycle(fund, asOfDate);
    CashSourceReport cashSources =
        sameDayCashReader.read(
            fund,
            asOfDate,
            cashOverride,
            reportCash,
            pevaRavaInAnActiveCycle.isPresent()
                ? PevaRavaCycleState.ACTIVE
                : PevaRavaCycleState.OUTSIDE_A_CYCLE);
    Optional<CurrentDayBalance> gatewayBalance = cashSources.gatewayBalanceInUse();
    Optional<NettingWindow> netting =
        gatewayBalance.map(
            balance ->
                new NettingWindow(
                    fund,
                    positionDate,
                    asOfDate,
                    balance.pendingEntryReferences(),
                    balance.bookedTodayEntryReferences()));
    BigDecimal appliedCash =
        gatewayBalance
            .map(CurrentDayBalance::balance)
            .orElse(cashOverride == null ? reportCash : cashOverride);
    Optional<TradeSettlementsInTheBalance> settlements = netting.map(tradeSettlementsReader::read);

    PendingOrderImpact pendingOrders =
        pendingOrderImpactService.calculate(
            fund,
            asOfDate,
            positionDate,
            pricing,
            settlements.map(TradeSettlementsInTheBalance::carriedByPendingFills).orElse(Set.of()));
    List<PositionSnapshot> positions =
        positionAssembler.assemble(fund, positionDate, pendingOrders);
    List<CalculationWarning> inputWarnings = new ArrayList<>();
    BigDecimal managementFee = getAccruedFees(fund, asOfDate, FeeType.MANAGEMENT, inputWarnings);
    BigDecimal depotFee = getAccruedFees(fund, asOfDate, FeeType.DEPOT, inputWarnings);
    TransactionParameters parameters = transactionParameterLoader.load(fund, asOfDate);

    BigDecimal securityValue =
        positions.stream()
            .map(PositionSnapshot::marketValue)
            .filter(Objects::nonNull)
            .reduce(ZERO, BigDecimal::add);
    BigDecimal grossPortfolioValue = securityValue.add(appliedCash);

    UnitRegisterCash registerCash = unitRegisterCashFlows.read(fund);
    BigDecimal liabilities = managementFee.add(depotFee).add(registerCash.liabilities());
    BigDecimal receivables = registerCash.incomingPaymentsClearing();

    BigDecimal pevaRava = pevaRavaInAnActiveCycle.orElse(ZERO);
    liabilities = liabilities.add(pevaRava);
    R16Reserve r16Reserve =
        r16OutflowReader.read(
            fund,
            asOfDate,
            netting
                .<CashHorizon>map(window -> window)
                .orElse(new CashHorizon.ThroughTheReportDate(positionDate)),
            parameters.r16Reserves());
    BigDecimal r16 = r16Reserve.reserved();
    liabilities = liabilities.add(r16);

    BigDecimal r45Net = getR45Net(fund, asOfDate);
    BigDecimal r45Reserved = ZERO.max(r45Net.negate());
    Optional<NettedForecast> r45OnTheBalance =
        netting.map(
            window ->
                r45ReportService
                    .getRowsOn(fund, asOfDate)
                    .map(rows -> NettedForecast.netted("r45", gatewayNetting.r45(window, rows)))
                    .orElse(NettedForecast.r45RowsUnavailable(r45Reserved)));
    BigDecimal r45Used = r45OnTheBalance.map(NettedForecast::after).orElse(r45Reserved);
    liabilities = liabilities.add(r45Used);

    liabilities = liabilities.add(getAdjustment(manualAdjustments, "additionalLiabilities"));
    receivables = receivables.add(getAdjustment(manualAdjustments, "additionalReceivables"));

    UnsettledTrades reportedTrades = unsettledTradeReader.read(fund, positionDate);
    Netted<UnsettledTrades> nettedTrades =
        settlements
            .map(inTheBalance -> inTheBalance.net(reportedTrades))
            .orElse(new Netted<>(reportedTrades, reportedTrades));
    UnsettledTrades unsettledTrades = nettedTrades.after();
    liabilities = liabilities.add(pendingOrders.pendingBuys()).add(unsettledTrades.payables());
    receivables = receivables.add(pendingOrders.pendingSells()).add(unsettledTrades.receivables());

    UnitReceivable unitReceivable = unitReceivableReader.read(fund, positionDate, asOfDate);
    BigDecimal unitReceivableDue = unitReceivableDue(cashSources.source(), unitReceivable);
    receivables = receivables.add(unitReceivableDue);

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
            r45OnTheBalance.isPresent() ? r45Used.negate() : r45Net,
            pendingOrders.pendingBuys(),
            pendingOrders.pendingSells(),
            unsettledTrades.payables(),
            unsettledTrades.receivables(),
            registerCash.unreconciledBankReceipts(),
            registerCash.fundUnitsReservedValue(),
            registerCash.incomingPaymentsClearing(),
            unitReceivableDue,
            unitReceivable.reported(),
            unitReceivable.receivedSinceReport());

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
        .cashSection(
            new CashSection(
                cashSources,
                netting.isPresent()
                    ? NettedForecast.onTheGatewayBalance(
                        nettedTrades,
                        pendingOrders,
                        r45OnTheBalance.orElseThrow(),
                        new Netted<>(unitReceivable.dueOnTopOfReportCash(), unitReceivableDue),
                        registerCash.incomingPaymentsClearing())
                    : List.of(),
                settlements.map(TradeSettlementsInTheBalance::unmatched).orElse(List.of()),
                r16Reserve))
        .ledgerCash(ledgerCash)
        .positionDate(positionDate)
        .modelEffectiveDate(parameters.modelEffectiveDate())
        .inputWarnings(List.copyOf(inputWarnings))
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

  private BigDecimal getAccruedFees(
      TulevaFund fund,
      LocalDate asOfDate,
      FeeType feeType,
      List<CalculationWarning> inputWarnings) {
    LocalDate feeMonth = asOfDate.withDayOfMonth(1);
    Map<LocalDate, BigDecimal> accrualsByDate =
        feeAccrualRepository.getAccruedFeesByDateForMonth(
            fund, feeMonth, List.of(feeType), asOfDate);
    FeeChargedToFundPolicy.Resolver resolver;
    try {
      resolver = feeChargedToFundPolicy.resolverFor(fund, feeType);
    } catch (FeePolicyUnresolvedException e) {
      log.warn("Fee policy does not resolve, reserving every accrual day", e);
      inputWarnings.add(
          unresolvedFeePolicyWarning(
              fund, asOfDate, feeType, accrualsByDate.size(), e.getReason()));
      return sumOf(accrualsByDate.values());
    }
    return sumReservingUnresolvedDays(
        resolver, accrualsByDate, fund, asOfDate, feeType, inputWarnings);
  }

  private BigDecimal sumReservingUnresolvedDays(
      FeeChargedToFundPolicy.Resolver resolver,
      Map<LocalDate, BigDecimal> accrualsByDate,
      TulevaFund fund,
      LocalDate asOfDate,
      FeeType feeType,
      List<CalculationWarning> inputWarnings) {
    Map<LocalDate, String> unresolvedReasons = unresolvedReasons(resolver, accrualsByDate.keySet());
    if (!unresolvedReasons.isEmpty()) {
      inputWarnings.add(
          unresolvedFeePolicyWarning(
              fund,
              asOfDate,
              feeType,
              unresolvedReasons.size(),
              unresolvedReasons.values().iterator().next()));
    }
    return sumOf(
        accrualsByDate.entrySet().stream()
            .filter(
                entry ->
                    unresolvedReasons.containsKey(entry.getKey())
                        || resolver.chargedOn(entry.getKey()))
            .map(Map.Entry::getValue)
            .toList());
  }

  private Map<LocalDate, String> unresolvedReasons(
      FeeChargedToFundPolicy.Resolver resolver, Set<LocalDate> accrualDates) {
    Map<LocalDate, String> reasons = new LinkedHashMap<>();
    for (LocalDate date : accrualDates) {
      try {
        resolver.chargedOn(date);
      } catch (FeePolicyUnresolvedException e) {
        log.warn("Fee policy does not resolve for one accrual day, reserving it", e);
        reasons.put(date, e.getReason());
      }
    }
    return reasons;
  }

  private CalculationWarning unresolvedFeePolicyWarning(
      TulevaFund fund, LocalDate asOfDate, FeeType feeType, int unresolvedDays, String reason) {
    String message =
        ("Fee policy does not resolve, reserving the unresolved days as if they were charged to the"
                + " fund: fund=%s, feeType=%s, asOfDate=%s, unresolvedDays=%d, reason=%s")
            .formatted(fund.name(), feeType, asOfDate, unresolvedDays, reason);
    log.warn(message);
    return new CalculationWarning(FEE_POLICY_UNRESOLVED, message);
  }

  private BigDecimal sumOf(Collection<BigDecimal> amounts) {
    return amounts.stream().reduce(ZERO, BigDecimal::add);
  }

  private static BigDecimal unitReceivableDue(CashSource source, UnitReceivable unitReceivable) {
    return switch (source) {
      case POSITION_REPORT -> unitReceivable.dueOnTopOfReportCash();
      case GATEWAY_BALANCE -> unitReceivable.dueOnTopOfGatewayBalance();
      case OPERATOR_AMOUNT -> ZERO;
    };
  }

  private BigDecimal getR45Net(TulevaFund fund, LocalDate asOfDate) {
    if (r45ReportService.getIncompleteFundsOn(asOfDate).contains(fund)) {
      throw new IllegalStateException(
          "R45 summary incomplete: fund="
              + fund
              + ", reason=unvalued R45 rows missing NAV, action=supply NAV and reprocess R45");
    }
    return Optional.ofNullable(r45ReportService.getFlowsOn(asOfDate).get(fund))
        .map(R45Result::netEur)
        .orElse(ZERO);
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
