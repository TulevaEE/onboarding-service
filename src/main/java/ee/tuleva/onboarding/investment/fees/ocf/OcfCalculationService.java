package ee.tuleva.onboarding.investment.fees.ocf;

import static ee.tuleva.onboarding.investment.fees.FeeType.DEPOT;
import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;
import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.fees.DepotFeeTierRepository;
import ee.tuleva.onboarding.investment.fees.FeeRate;
import ee.tuleva.onboarding.investment.fees.FeeRateRepository;
import ee.tuleva.onboarding.investment.fees.InstrumentFeeRepository;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcfCalculationService {

  private static final int SCALE = 8;

  private final FeeRateRepository feeRateRepository;
  private final DepotFeeTierRepository depotFeeTierRepository;
  private final InstrumentFeeRepository instrumentFeeRepository;
  private final FundPositionRepository fundPositionRepository;
  private final ModelPortfolioAllocationRepository modelPortfolioAllocationRepository;
  private final TransactionExecutionRepository transactionExecutionRepository;
  private final OcfSnapshotRepository ocfSnapshotRepository;

  @Transactional
  public OcfSnapshot calculateOcf(TulevaFund fund, YearMonth month) {
    var monthEnd = month.atEndOfMonth();

    var mgmtRate = getManagementFeeRate(fund, monthEnd);
    var depotRate = getDepotFeeRate(fund, monthEnd);
    var underlyingCost = getUnderlyingFundCost(fund, monthEnd);
    var txnCostRate = getTransactionCostRate(fund, monthEnd);
    var totalOcf = mgmtRate.add(depotRate).add(underlyingCost).add(txnCostRate);

    var snapshot =
        new OcfSnapshot(
            null,
            fund.getCode(),
            month.atDay(1),
            mgmtRate,
            depotRate,
            underlyingCost,
            txnCostRate,
            totalOcf);

    ocfSnapshotRepository.save(snapshot);

    log.info(
        "OCF calculated: fund={}, month={}, mgmt={}, depot={}, underlying={}, txn={}, total={}",
        fund.getCode(),
        month,
        mgmtRate,
        depotRate,
        underlyingCost,
        txnCostRate,
        totalOcf);

    return snapshot;
  }

  public void calculateForAllFunds(YearMonth month) {
    for (var fund : TulevaFund.values()) {
      try {
        calculateOcf(fund, month);
      } catch (Exception e) {
        log.error("OCF calculation failed: fund={}, month={}", fund.getCode(), month, e);
      }
    }
  }

  public void backfillMonths(int monthsBack, Clock clock) {
    var now = YearMonth.now(clock);
    for (int i = 1; i <= monthsBack; i++) {
      calculateForAllFunds(now.minusMonths(i));
    }
  }

  BigDecimal getManagementFeeRate(TulevaFund fund, LocalDate asOf) {
    return feeRateRepository
        .findValidRate(fund, MANAGEMENT, asOf)
        .map(FeeRate::annualRate)
        .orElse(ZERO);
  }

  BigDecimal getDepotFeeRate(TulevaFund fund, LocalDate asOf) {
    return feeRateRepository
        .findValidRate(fund, DEPOT, asOf)
        .map(FeeRate::annualRate)
        .orElseGet(() -> getDepotRateFromTier(asOf));
  }

  private BigDecimal getDepotRateFromTier(LocalDate asOf) {
    var latestNavDate = fundPositionRepository.findLatestSecurityNavDateUpTo(asOf).orElse(null);
    if (latestNavDate == null) {
      return ZERO;
    }
    var totalSecurityAum = fundPositionRepository.sumSecurityMarketValueAllFunds(latestNavDate);
    return depotFeeTierRepository.findRateForAum(totalSecurityAum, asOf);
  }

  BigDecimal getUnderlyingFundCost(TulevaFund fund, LocalDate asOf) {
    var rates = instrumentFeeRepository.findAllValidRates(asOf);
    if (rates.isEmpty()) {
      return ZERO;
    }
    var rateByIsin =
        rates.stream().collect(Collectors.toMap(r -> r.isin(), r -> r.netOcf(), (a, b) -> a));

    if (fund == TulevaFund.TKF100) {
      return computeFromModelPortfolio(fund, asOf, rateByIsin);
    }
    return computeFromPositions(fund, asOf, rateByIsin);
  }

  private BigDecimal computeFromModelPortfolio(
      TulevaFund fund, LocalDate asOf, java.util.Map<String, BigDecimal> rateByIsin) {
    var allocations = modelPortfolioAllocationRepository.findLatestByFundAsOf(fund, asOf);
    if (allocations.isEmpty()) {
      return ZERO;
    }
    return allocations.stream()
        .map(a -> a.getWeight().multiply(rateByIsin.getOrDefault(a.getIsin(), ZERO)))
        .reduce(ZERO, BigDecimal::add);
  }

  private BigDecimal computeFromPositions(
      TulevaFund fund, LocalDate asOf, java.util.Map<String, BigDecimal> rateByIsin) {
    var latestNavDate =
        fundPositionRepository.findLatestNavDateByFundAndAsOfDate(fund, asOf).orElse(null);
    if (latestNavDate == null) {
      return ZERO;
    }
    var positions =
        fundPositionRepository.findByNavDateAndFundAndAccountType(latestNavDate, fund, SECURITY);
    if (positions.isEmpty()) {
      return ZERO;
    }
    var totalValue = positions.stream().map(p -> p.getMarketValue()).reduce(ZERO, BigDecimal::add);
    if (totalValue.signum() <= 0) {
      return ZERO;
    }
    return positions.stream()
        .map(
            p -> {
              var weight = p.getMarketValue().divide(totalValue, SCALE, HALF_UP);
              return weight.multiply(rateByIsin.getOrDefault(p.getAccountId(), ZERO));
            })
        .reduce(ZERO, BigDecimal::add);
  }

  BigDecimal getTransactionCostRate(TulevaFund fund, LocalDate monthEnd) {
    var periodStart = monthEnd.minusYears(1).plusDays(1);
    var txnCosts =
        transactionExecutionRepository.sumCommissionsForFundAndPeriod(
            fund.getCode(), periodStart, monthEnd);
    if (txnCosts.signum() == 0) {
      return ZERO;
    }

    var avgAum = computeAverageSecurityAum(fund, periodStart, monthEnd);
    if (avgAum.signum() <= 0) {
      return ZERO;
    }
    return txnCosts.divide(avgAum, SCALE, HALF_UP);
  }

  private BigDecimal computeAverageSecurityAum(TulevaFund fund, LocalDate from, LocalDate to) {
    var navDates = fundPositionRepository.findDistinctNavDatesByFund(fund);
    var datesInRange = navDates.stream().filter(d -> !d.isBefore(from) && !d.isAfter(to)).toList();
    if (datesInRange.isEmpty()) {
      return ZERO;
    }
    var total = ZERO;
    for (var date : datesInRange) {
      total =
          total.add(
              fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                  fund, date, java.util.List.of(SECURITY)));
    }
    return total.divide(BigDecimal.valueOf(datesInRange.size()), 2, HALF_UP);
  }
}
