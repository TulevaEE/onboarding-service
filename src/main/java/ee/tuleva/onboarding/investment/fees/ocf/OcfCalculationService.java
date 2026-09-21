package ee.tuleva.onboarding.investment.fees.ocf;

import static ee.tuleva.onboarding.investment.fees.FeeType.DEPOT;
import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;
import static ee.tuleva.onboarding.investment.fees.ocf.OcfGap.DEPOT_FEE_RATE_MISSING;
import static ee.tuleva.onboarding.investment.fees.ocf.OcfGap.MANAGEMENT_FEE_RATE_MISSING;
import static ee.tuleva.onboarding.investment.fees.ocf.OcfGap.NAV_HAS_NO_POSITIVE_AUM;
import static ee.tuleva.onboarding.investment.fees.ocf.OcfGap.NO_PUBLISHED_NAV_CALCULATION;
import static ee.tuleva.onboarding.investment.fees.ocf.OcfGap.TRANSACTION_COSTS_WITHOUT_AVERAGE_AUM;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.investment.fees.DepotRateResolver;
import ee.tuleva.onboarding.investment.fees.FeeChargedToFundPolicy;
import ee.tuleva.onboarding.investment.fees.FeeRateRepository;
import ee.tuleva.onboarding.investment.fees.InstrumentFee;
import ee.tuleva.onboarding.investment.fees.InstrumentFeeRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.savings.fund.nav.NavAccountLine;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcfCalculationService {

  private static final int SCALE = 8;
  private static final RebateBasis REBATE_BASIS = RebateBasis.NET;
  private static final BigDecimal DAYS_IN_YEAR = BigDecimal.valueOf(365);
  private static final String UNIDENTIFIED_HOLDING = "<no isin>";
  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private final FeeRateRepository feeRateRepository;
  private final FeeChargedToFundPolicy feeChargedToFundPolicy;
  private final DepotRateResolver depotRateResolver;
  private final InstrumentFeeRepository instrumentFeeRepository;
  private final TransactionExecutionRepository transactionExecutionRepository;
  private final OcfSnapshotRepository ocfSnapshotRepository;
  private final FundNavQueryService fundNavQueryService;
  private final OcfJson ocfJson;

  public OcfSnapshot calculateOcf(TulevaFund fund, YearMonth month) {
    var monthEnd = month.atEndOfMonth();

    var mgmt = getManagementFee(fund, monthEnd);
    var depot = getDepotFee(fund, monthEnd);
    var underlying = getUnderlyingFundCost(fund, monthEnd);
    var txn = getTransactionCost(fund, monthEnd);
    var totalOcf = mgmt.rate().add(depot.rate()).add(underlying.rate()).add(txn.rate());

    var gaps = gaps(mgmt, depot, underlying, txn);
    var snapshot =
        OcfSnapshot.computed(
            fund.getCode(),
            month.atDay(1),
            mgmt.rate(),
            depot.rate(),
            underlying.grossRate(),
            underlying.rate(),
            REBATE_BASIS,
            txn.rate(),
            totalOcf,
            gaps.isEmpty(),
            ocfJson.checks(gaps),
            audit(mgmt, depot, underlying, txn));

    ocfSnapshotRepository.save(snapshot);

    log.info(
        "OCF calculated: fund={}, month={}, mgmt={}, depot={}, underlying={}, txn={}, total={},"
            + " complete={}, gaps={}",
        fund.getCode(),
        month,
        mgmt.rate(),
        depot.rate(),
        underlying.rate(),
        txn.rate(),
        totalOcf,
        gaps.isEmpty(),
        gaps);

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

  public boolean publish(TulevaFund fund, YearMonth month, String publishedIn) {
    var published = ocfSnapshotRepository.publish(fund.getCode(), month.atDay(1), publishedIn);
    if (published) {
      log.info(
          "OCF snapshot published: fund={}, month={}, publishedIn={}",
          fund.getCode(),
          month,
          publishedIn);
    }
    return published;
  }

  private static List<OcfGap> gaps(
      ManagementFee mgmt, DepotFee depot, UnderlyingFundCost underlying, TransactionCost txn) {
    var gaps = new ArrayList<OcfGap>();
    if (mgmt.rateId() == null) {
      gaps.add(MANAGEMENT_FEE_RATE_MISSING);
    }
    if (depot.chargedToFund() && depot.rate().signum() == 0) {
      gaps.add(DEPOT_FEE_RATE_MISSING);
    }
    gaps.addAll(underlying.gaps());
    gaps.addAll(txn.gaps());
    return List.copyOf(gaps);
  }

  private OcfAudit audit(
      ManagementFee mgmt, DepotFee depot, UnderlyingFundCost underlying, TransactionCost txn) {
    return new OcfAudit(
        underlying.navDate(),
        underlying.navCalculationId(),
        underlying.assetsUnderManagement(),
        mgmt.rateId(),
        depot.chargedToFund(),
        depot.tierAnchorDate(),
        depot.tierBasis(),
        txn.windowStart(),
        txn.windowEnd(),
        txn.commissions(),
        txn.averageAum(),
        ocfJson.navDates(txn.navDates()));
  }

  ManagementFee getManagementFee(TulevaFund fund, LocalDate asOf) {
    return feeRateRepository
        .findValidRate(fund, MANAGEMENT, asOf)
        .map(rate -> new ManagementFee(rate.annualRate(), rate.id()))
        .orElseGet(
            () -> {
              log.warn(
                  "No management fee rate, it resolves to zero: fund={}, asOf={}",
                  fund.getCode(),
                  asOf);
              return new ManagementFee(ZERO, null);
            });
  }

  DepotFee getDepotFee(TulevaFund fund, LocalDate asOf) {
    if (!feeChargedToFundPolicy.chargedToFund(fund, DEPOT, asOf)) {
      return new DepotFee(ZERO, false, null, null);
    }
    var rate = depotRateResolver.resolveRate(fund, asOf);
    return new DepotFee(rate.annualRate(), true, rate.tierAnchorDate(), rate.tierBasis());
  }

  UnderlyingFundCost getUnderlyingFundCost(TulevaFund fund, LocalDate asOf) {
    var rates = instrumentFeeRepository.findAllValidRates(asOf);
    var netByIsin =
        rates.stream()
            .collect(Collectors.toMap(InstrumentFee::isin, InstrumentFee::netOcf, (a, b) -> a));
    var grossByIsin =
        rates.stream()
            .collect(
                Collectors.toMap(
                    InstrumentFee::isin, OcfCalculationService::publishedOrNet, (a, b) -> a));

    return computeFromPublishedNav(fund, asOf, netByIsin, grossByIsin);
  }

  private static BigDecimal publishedOrNet(InstrumentFee fee) {
    return fee.publishedOcf() != null ? fee.publishedOcf() : fee.netOcf();
  }

  private UnderlyingFundCost computeFromPublishedNav(
      TulevaFund fund,
      LocalDate asOf,
      Map<String, BigDecimal> rateByIsin,
      Map<String, BigDecimal> grossByIsin) {
    var navDate =
        fundNavQueryService.findLatestPublishedNavDateOnOrBefore(fund.getCode(), asOf).orElse(null);
    var calculation =
        navDate == null
            ? null
            : fundNavQueryService.findPublishedCalculation(fund.getCode(), navDate).orElse(null);
    if (calculation == null) {
      log.warn(
          "No published NAV calculation, underlying fund cost resolves to zero: fund={}, asOf={}",
          fund.getCode(),
          asOf);
      return new UnderlyingFundCost(
          ZERO, ZERO, navDate, null, null, List.of(NO_PUBLISHED_NAV_CALCULATION));
    }
    var aum = calculation.assetsUnderManagement();
    if (aum.signum() <= 0) {
      log.warn(
          "Published NAV has no positive AUM, underlying fund cost resolves to zero: fund={},"
              + " asOf={}, aum={}",
          fund.getCode(),
          asOf,
          aum);
      return new UnderlyingFundCost(
          ZERO, ZERO, navDate, calculation.id(), aum, List.of(NAV_HAS_NO_POSITIVE_AUM));
    }
    var lines = calculation.securityLines();
    var unrated = unratedIsins(lines, rateByIsin);
    if (!unrated.isEmpty()) {
      throw new MissingInstrumentRateException(fund, asOf, unrated);
    }
    var net = weigh(lines, rateByIsin, aum);
    var gross = weigh(lines, grossByIsin, aum);
    return new UnderlyingFundCost(net, gross, navDate, calculation.id(), aum, List.of());
  }

  private static BigDecimal weigh(
      List<NavAccountLine> lines, Map<String, BigDecimal> rateByIsin, BigDecimal aum) {
    return lines.stream()
        .map(line -> line.value().multiply(rateFor(line, rateByIsin)))
        .reduce(ZERO, BigDecimal::add)
        .divide(aum, SCALE, HALF_UP);
  }

  private static List<String> unratedIsins(
      List<NavAccountLine> lines, Map<String, BigDecimal> rateByIsin) {
    return lines.stream()
        .map(NavAccountLine::accountId)
        .map(isin -> isin == null ? UNIDENTIFIED_HOLDING : isin)
        .filter(isin -> !rateByIsin.containsKey(isin))
        .distinct()
        .toList();
  }

  private static BigDecimal rateFor(NavAccountLine line, Map<String, BigDecimal> rateByIsin) {
    return requireNonNull(
        rateByIsin.get(line.accountId()), "Unrated holding passed the guard: " + line.accountId());
  }

  TransactionCost getTransactionCost(TulevaFund fund, LocalDate monthEnd) {
    var trailingYearStart = monthEnd.minusYears(1).plusDays(1);
    var periodStart = laterOf(fund.getInceptionDate(), trailingYearStart);
    if (periodStart.isAfter(monthEnd)) {
      return new TransactionCostWindow(periodStart, monthEnd, ZERO, ZERO, List.of())
          .at(ZERO, List.of());
    }
    var txnCosts =
        transactionExecutionRepository.sumCommissionsForFundAndPeriod(
            fund.getCode(),
            periodStart.atStartOfDay(ESTONIAN_ZONE).toInstant(),
            monthEnd.plusDays(1).atStartOfDay(ESTONIAN_ZONE).toInstant());
    var navDates =
        fundNavQueryService.findPublishedNavDatesBetween(fund.getCode(), periodStart, monthEnd);
    var avgAum = averageAumOverPublishedNavDates(fund, periodStart, navDates);
    var window = new TransactionCostWindow(periodStart, monthEnd, txnCosts, avgAum, navDates);

    if (txnCosts.signum() == 0) {
      return window.at(ZERO, List.of());
    }
    if (avgAum.signum() <= 0) {
      log.warn(
          "Transaction costs without an average AUM to divide by, the rate resolves to zero:"
              + " fund={}, monthEnd={}, commissions={}",
          fund.getCode(),
          monthEnd,
          txnCosts);
      return window.at(ZERO, List.of(TRANSACTION_COSTS_WITHOUT_AVERAGE_AUM));
    }
    var daysOfFundLifeInThePeriod = ChronoUnit.DAYS.between(periodStart, monthEnd) + 1;
    return window.at(
        txnCosts
            .multiply(DAYS_IN_YEAR)
            .divide(avgAum.multiply(BigDecimal.valueOf(daysOfFundLifeInThePeriod)), SCALE, HALF_UP),
        List.of());
  }

  private static LocalDate laterOf(LocalDate one, LocalDate other) {
    return one.isAfter(other) ? one : other;
  }

  private BigDecimal averageAumOverPublishedNavDates(
      TulevaFund fund, LocalDate periodStart, List<LocalDate> navDates) {
    if (navDates.isEmpty()) {
      return ZERO;
    }
    warnWhenNavObservationsStartAfterThePeriod(fund, periodStart, navDates.getFirst());
    var total =
        navDates.stream()
            .map(date -> fundNavQueryService.findAum(fund.getCode(), date))
            .reduce(ZERO, BigDecimal::add);
    return total.divide(BigDecimal.valueOf(navDates.size()), SCALE, HALF_UP);
  }

  private static void warnWhenNavObservationsStartAfterThePeriod(
      TulevaFund fund, LocalDate periodStart, LocalDate firstNavDate) {
    if (YearMonth.from(firstNavDate).isAfter(YearMonth.from(periodStart))) {
      log.warn(
          "Average NAV estimated from the months that carry a published NAV, transaction cost rate"
              + " is approximate: fund={}, periodStart={}, firstPublishedNavDate={}",
          fund.getCode(),
          periodStart,
          firstNavDate);
    }
  }

  record ManagementFee(BigDecimal rate, @Nullable Long rateId) {}

  record DepotFee(
      BigDecimal rate,
      boolean chargedToFund,
      @Nullable LocalDate tierAnchorDate,
      @Nullable BigDecimal tierBasis) {}

  record UnderlyingFundCost(
      BigDecimal rate,
      BigDecimal grossRate,
      @Nullable LocalDate navDate,
      @Nullable UUID navCalculationId,
      @Nullable BigDecimal assetsUnderManagement,
      List<OcfGap> gaps) {}

  record TransactionCost(
      BigDecimal rate,
      LocalDate windowStart,
      LocalDate windowEnd,
      BigDecimal commissions,
      BigDecimal averageAum,
      List<LocalDate> navDates,
      List<OcfGap> gaps) {}

  private record TransactionCostWindow(
      LocalDate start,
      LocalDate end,
      BigDecimal commissions,
      BigDecimal averageAum,
      List<LocalDate> navDates) {

    TransactionCost at(BigDecimal rate, List<OcfGap> gaps) {
      return new TransactionCost(rate, start, end, commissions, averageAum, navDates, gaps);
    }
  }
}
