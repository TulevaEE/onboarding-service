package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.investment.position.AccountType.CASH;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.calculation.PositionCalculationRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionInputService {

  private static final BigDecimal DEFAULT_MIN_TRANSACTION = new BigDecimal("50000");
  private static final BigDecimal DEFAULT_CASH_BUFFER = ZERO;

  private final PositionCalculationRepository positionCalculationRepository;
  private final FundPositionRepository fundPositionRepository;
  private final FeeAccrualRepository feeAccrualRepository;
  private final ModelPortfolioAllocationRepository modelPortfolioAllocationRepository;
  private final FundLimitRepository fundLimitRepository;
  private final PositionLimitRepository positionLimitRepository;
  private final NavLedgerRepository navLedgerRepository;
  private final FundValueRepository fundValueRepository;
  private final TransactionOrderRepository orderRepository;

  public FundTransactionInput gatherInput(
      TulevaFund fund, LocalDate asOfDate, Map<String, Object> manualAdjustments) {
    LocalDate positionDate =
        positionCalculationRepository
            .getLatestDateUpTo(fund, asOfDate)
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
    List<ModelPortfolioAllocation> allocations = getModelAllocations(fund);
    List<ModelWeight> modelWeights = toModelWeights(allocations);
    BigDecimal cashBuffer = getCashBuffer(fund);
    BigDecimal minTransaction = getMinTransaction(fund);
    Map<String, PositionLimitSnapshot> positionLimits = getPositionLimits(fund);
    Set<String> fastSellIsins = getFastSellIsins(allocations);
    Map<String, InstrumentType> instrumentTypes = getInstrumentTypes(allocations);
    Map<String, OrderVenue> orderVenues = getOrderVenues(allocations);

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
    return positionCalculationRepository.findByFundAndDate(fund, date).stream()
        .filter(position -> position.getCalculatedMarketValue() != null)
        .map(
            position ->
                new PositionSnapshot(position.getIsin(), position.getCalculatedMarketValue()))
        .toList();
  }

  private BigDecimal getCashBalance(TulevaFund fund, LocalDate date) {
    List<FundPosition> cashPositions =
        fundPositionRepository.findByReportingDateAndFundAndAccountType(date, fund, CASH);
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

  private List<ModelPortfolioAllocation> getModelAllocations(TulevaFund fund) {
    return modelPortfolioAllocationRepository.findLatestByFund(fund).stream()
        .filter(allocation -> allocation.getIsin() != null)
        .toList();
  }

  private List<ModelWeight> toModelWeights(List<ModelPortfolioAllocation> allocations) {
    return allocations.stream()
        .map(allocation -> new ModelWeight(allocation.getIsin(), allocation.getWeight()))
        .toList();
  }

  private BigDecimal getCashBuffer(TulevaFund fund) {
    return getFundLimitValue(fund, limit -> limit.getReserveSoft(), DEFAULT_CASH_BUFFER);
  }

  private BigDecimal getMinTransaction(TulevaFund fund) {
    return getFundLimitValue(fund, limit -> limit.getMinTransaction(), DEFAULT_MIN_TRANSACTION);
  }

  private BigDecimal getFundLimitValue(
      TulevaFund fund, Function<FundLimit, BigDecimal> extractor, BigDecimal defaultValue) {
    return fundLimitRepository
        .findLatestByFund(fund)
        .map(limit -> extractor.apply(limit) != null ? extractor.apply(limit) : defaultValue)
        .orElse(defaultValue);
  }

  private Map<String, PositionLimitSnapshot> getPositionLimits(TulevaFund fund) {
    return positionLimitRepository.findLatestByFund(fund).stream()
        .collect(
            Collectors.toMap(
                PositionLimit::getIsin,
                limit ->
                    new PositionLimitSnapshot(
                        limit.getSoftLimitPercent(), limit.getHardLimitPercent()),
                (a, b) -> b));
  }

  private Set<String> getFastSellIsins(List<ModelPortfolioAllocation> allocations) {
    return allocations.stream()
        .filter(ModelPortfolioAllocation::isFastSell)
        .map(ModelPortfolioAllocation::getIsin)
        .collect(Collectors.toSet());
  }

  private Map<String, InstrumentType> getInstrumentTypes(
      List<ModelPortfolioAllocation> allocations) {
    return allocations.stream()
        .filter(allocation -> allocation.getInstrumentType() != null)
        .collect(
            Collectors.toMap(
                ModelPortfolioAllocation::getIsin,
                ModelPortfolioAllocation::getInstrumentType,
                (a, b) -> b));
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

  private Map<String, OrderVenue> getOrderVenues(List<ModelPortfolioAllocation> allocations) {
    return allocations.stream()
        .filter(allocation -> allocation.getOrderVenue() != null)
        .collect(
            Collectors.toMap(
                ModelPortfolioAllocation::getIsin,
                ModelPortfolioAllocation::getOrderVenue,
                (a, b) -> b));
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
