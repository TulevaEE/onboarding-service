package ee.tuleva.onboarding.investment.fees.ocf;

import static ee.tuleva.onboarding.investment.fees.FeeType.DEPOT;
import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.investment.fees.DepotRateResolver;
import ee.tuleva.onboarding.investment.fees.FeeChargedToFundPolicy;
import ee.tuleva.onboarding.investment.fees.FeeRate;
import ee.tuleva.onboarding.investment.fees.FeeRateRepository;
import ee.tuleva.onboarding.investment.fees.InstrumentFeeRepository;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.savings.fund.nav.NavAccountLine;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcfCalculationService {

  private static final int SCALE = 8;
  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private final FeeRateRepository feeRateRepository;
  private final FeeChargedToFundPolicy feeChargedToFundPolicy;
  private final DepotRateResolver depotRateResolver;
  private final InstrumentFeeRepository instrumentFeeRepository;
  private final ModelPortfolioAllocationRepository modelPortfolioAllocationRepository;
  private final TransactionExecutionRepository transactionExecutionRepository;
  private final OcfSnapshotRepository ocfSnapshotRepository;
  private final FundNavQueryService fundNavQueryService;

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
    if (!feeChargedToFundPolicy.chargedToFund(fund, DEPOT, asOf)) {
      return ZERO;
    }
    return depotRateResolver.resolveAnnualRate(fund, asOf);
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
    return computeFromPublishedNav(fund, asOf, rateByIsin);
  }

  private BigDecimal computeFromModelPortfolio(
      TulevaFund fund, LocalDate asOf, Map<String, BigDecimal> rateByIsin) {
    var allocations = modelPortfolioAllocationRepository.findLatestByFundAsOf(fund, asOf);
    if (allocations.isEmpty()) {
      return ZERO;
    }
    return allocations.stream()
        .map(a -> a.getWeight().multiply(rateByIsin.getOrDefault(a.getIsin(), ZERO)))
        .reduce(ZERO, BigDecimal::add);
  }

  private BigDecimal computeFromPublishedNav(
      TulevaFund fund, LocalDate asOf, Map<String, BigDecimal> rateByIsin) {
    var calculation =
        fundNavQueryService
            .findLatestNavDateOnOrBefore(fund.getCode(), asOf)
            .flatMap(
                navDate -> fundNavQueryService.findPublishedCalculation(fund.getCode(), navDate))
            .orElse(null);
    if (calculation == null) {
      return ZERO;
    }
    var aum = calculation.assetsUnderManagement();
    if (aum.signum() <= 0) {
      return ZERO;
    }
    return calculation.securityLines().stream()
        .map(line -> line.value().divide(aum, SCALE, HALF_UP).multiply(rateFor(line, rateByIsin)))
        .reduce(ZERO, BigDecimal::add);
  }

  private static BigDecimal rateFor(NavAccountLine line, Map<String, BigDecimal> rateByIsin) {
    var isin = line.accountId();
    return isin == null ? ZERO : rateByIsin.getOrDefault(isin, ZERO);
  }

  BigDecimal getTransactionCostRate(TulevaFund fund, LocalDate monthEnd) {
    var periodStart = monthEnd.minusYears(1).plusDays(1);
    var navDates =
        fundNavQueryService.findPublishedNavDatesBetween(fund.getCode(), periodStart, monthEnd);

    var effectivePeriodStart = navDates.isEmpty() ? periodStart : navDates.getFirst();
    var txnCosts =
        transactionExecutionRepository.sumCommissionsForFundAndPeriod(
            fund.getCode(),
            effectivePeriodStart.atStartOfDay(ESTONIAN_ZONE).toInstant(),
            monthEnd.plusDays(1).atStartOfDay(ESTONIAN_ZONE).toInstant());
    if (txnCosts.signum() == 0) {
      return ZERO;
    }

    var avgAum = averageAum(fund, navDates);
    if (avgAum.signum() <= 0) {
      return ZERO;
    }
    return txnCosts.divide(avgAum, SCALE, HALF_UP);
  }

  private BigDecimal averageAum(TulevaFund fund, List<LocalDate> navDates) {
    if (navDates.isEmpty()) {
      return ZERO;
    }
    var total =
        navDates.stream()
            .map(date -> fundNavQueryService.findAum(fund.getCode(), date))
            .reduce(ZERO, BigDecimal::add);
    return total.divide(BigDecimal.valueOf(navDates.size()), 2, HALF_UP);
  }
}
