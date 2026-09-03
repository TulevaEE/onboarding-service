package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.investment.check.limit.CheckType.*;
import static ee.tuleva.onboarding.investment.position.AccountType.*;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider;
import ee.tuleva.onboarding.investment.portfolio.*;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionType;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class LimitCheckService {

  private final Clock clock;
  private final FundPositionRepository fundPositionRepository;
  private final FundValueProvider fundValueProvider;
  private final NavReportPositionProvider navReportPositionProvider;
  private final PositionLimitRepository positionLimitRepository;
  private final ProviderLimitRepository providerLimitRepository;
  private final FundLimitRepository fundLimitRepository;
  private final ModelPortfolioAllocationRepository modelPortfolioAllocationRepository;
  private final LimitCheckEventWriter limitCheckEventWriter;
  private final PositionLimitChecker positionLimitChecker;
  private final ProviderLimitChecker providerLimitChecker;
  private final ReserveLimitChecker reserveLimitChecker;
  private final FreeCashLimitChecker freeCashLimitChecker;
  private final TransactionOrderRepository transactionOrderRepository;

  LimitCheckRun runChecks() {
    return runChecksForFunds(List.of(TulevaFund.values()));
  }

  LimitCheckRun runChecksForFunds(List<TulevaFund> funds) {
    return runChecksForFundsAsOf(funds, LocalDate.now(clock));
  }

  LimitCheckRun runChecksAsOf(LocalDate asOfDate) {
    return runChecksForFundsAsOf(List.of(TulevaFund.values()), asOfDate);
  }

  LimitCheckRun runChecksForFundsAsOf(List<TulevaFund> funds, LocalDate asOfDate) {
    var results = new ArrayList<LimitCheckResult>();
    var fundsNotChecked = new ArrayList<TulevaFund>();
    var errors = new ArrayList<Exception>();

    for (var fund : funds) {
      var latestDate = fundPositionRepository.findLatestNavDateByFundAndAsOfDate(fund, asOfDate);
      if (latestDate.isEmpty()) {
        log.warn("No position data for fund: fund={}, asOfDate={}", fund, asOfDate);
        fundsNotChecked.add(fund);
        continue;
      }

      var checkDate = latestDate.get();
      try {
        var result = checkFund(fund, checkDate);
        results.add(result);
      } catch (Exception e) {
        log.error("Limit check failed: fund={}, checkDate={}", fund, checkDate, e);
        fundsNotChecked.add(fund);
        errors.add(e);
      }
    }

    var run = new LimitCheckRun(List.copyOf(results), List.copyOf(fundsNotChecked));
    if (!errors.isEmpty()) {
      var combined =
          new LimitCheckPartialFailureException(
              "Limit check failed for %d fund(s)".formatted(errors.size()), run);
      errors.forEach(combined::addSuppressed);
      throw combined;
    }

    return run;
  }

  List<LimitCheckResult> backfillChecks(int daysBack) {
    var today = LocalDate.now(clock);
    var allResults = new ArrayList<LimitCheckResult>();

    for (int i = daysBack; i >= 0; i--) {
      var asOfDate = today.minusDays(i);
      try {
        allResults.addAll(runChecksAsOf(asOfDate).results());
      } catch (LimitCheckPartialFailureException e) {
        allResults.addAll(e.getPartialRun().results());
      }
    }

    return allResults;
  }

  LimitCheckResult checkFund(TulevaFund fund, LocalDate checkDate) {
    var positions =
        fundPositionRepository.findByNavDateAndFundAndAccountType(checkDate, fund, SECURITY);

    var navMarketValues = navReportPositionProvider.getSecurityMarketValues(fund, checkDate);
    positions.forEach(
        p -> {
          var navValue = navMarketValues.get(p.getAccountId());
          if (navValue != null) {
            p.setMarketValue(navValue);
          }
        });

    var totalNav = computeTotalNav(fund, checkDate);

    var cashTotal =
        sumMarketValues(
            fundPositionRepository.findByNavDateAndFundAndAccountType(checkDate, fund, CASH));
    var liabilityTotal =
        sumMarketValues(
                fundPositionRepository.findByNavDateAndFundAndAccountType(
                    checkDate, fund, LIABILITY))
            .add(
                sumMarketValues(
                    fundPositionRepository.findByNavDateAndFundAndAccountType(
                        checkDate, fund, FEE)));

    var positionLimits = positionLimitRepository.findLatestByFundAsOf(fund, checkDate);
    var providerLimits = providerLimitRepository.findLatestByFundAsOf(fund, checkDate);
    var fundLimit = fundLimitRepository.findLatestByFundAsOf(fund, checkDate).orElse(null);
    var isinToProvider = buildIsinToProviderMap(fund, checkDate);

    var positionBreaches = positionLimitChecker.check(fund, positions, totalNav, positionLimits);
    var providerBreaches =
        providerLimitChecker.check(fund, positions, totalNav, isinToProvider, providerLimits);
    var reserveBreach = reserveLimitChecker.check(fund, cashTotal, fundLimit);
    var pendingCashImpact = pendingCashImpact(fund, checkDate);
    var freeCashBreach =
        freeCashLimitChecker.check(fund, cashTotal, liabilityTotal, pendingCashImpact, fundLimit);

    limitCheckEventWriter.replaceEvents(
        fund,
        checkDate,
        List.of(
            event(fund, checkDate, POSITION, positionBreaches),
            event(fund, checkDate, PROVIDER, providerBreaches),
            event(fund, checkDate, RESERVE, reserveBreach),
            event(fund, checkDate, FREE_CASH, freeCashBreach)));

    return new LimitCheckResult(
        fund, checkDate, positionBreaches, providerBreaches, reserveBreach, freeCashBreach);
  }

  private BigDecimal pendingCashImpact(TulevaFund fund, LocalDate checkDate) {
    var createdBefore = checkDate.plusDays(1).atTime(LocalTime.MIDNIGHT).toInstant(ZoneOffset.UTC);
    return transactionOrderRepository
        .findUnsettledOrdersAsOf(fund, checkDate, createdBefore)
        .stream()
        .map(this::signedCashImpact)
        .reduce(ZERO, BigDecimal::add);
  }

  private BigDecimal signedCashImpact(TransactionOrder order) {
    var amount = order.getOrderAmount();
    if (amount == null) {
      return ZERO;
    }
    return order.getTransactionType() == TransactionType.BUY ? amount : amount.negate();
  }

  private BigDecimal computeTotalNav(TulevaFund fund, LocalDate checkDate) {
    var calculatedAum = navReportPositionProvider.getCalculatedAum(fund, checkDate);
    if (calculatedAum.isPresent()) {
      return calculatedAum.get();
    }
    log.warn(
        "Calculated AUM unavailable in nav_report, falling back to units × NAV per unit: fund={}",
        fund);
    var unitsPositions =
        fundPositionRepository.findByNavDateAndFundAndAccountType(checkDate, fund, UNITS);
    if (!unitsPositions.isEmpty()) {
      var units = unitsPositions.getFirst().getQuantity();
      if (units != null && units.signum() > 0) {
        var navPerUnit = fundValueProvider.getLatestValue(fund.getIsin(), checkDate);
        if (navPerUnit.isPresent()) {
          return units.multiply(navPerUnit.get().value());
        }
      }
    }
    throw new IllegalStateException(
        "No calculated AUM or units × NAV per unit available: fund=%s, date=%s"
            .formatted(fund, checkDate));
  }

  private BigDecimal sumMarketValues(List<FundPosition> positions) {
    return positions.stream()
        .map(FundPosition::getMarketValue)
        .filter(Objects::nonNull)
        .reduce(ZERO, BigDecimal::add);
  }

  private Map<String, Provider> buildIsinToProviderMap(TulevaFund fund, LocalDate checkDate) {
    var merged =
        new HashMap<>(
            modelPortfolioAllocationRepository.findPreviousByFundAsOf(fund, checkDate).stream()
                .filter(a -> a.getIsin() != null && a.getProvider() != null)
                .collect(
                    Collectors.toMap(
                        ModelPortfolioAllocation::getIsin,
                        ModelPortfolioAllocation::getProvider,
                        (a, b) -> b)));
    modelPortfolioAllocationRepository.findLatestByFundAsOf(fund, checkDate).stream()
        .filter(a -> a.getIsin() != null && a.getProvider() != null)
        .forEach(a -> merged.put(a.getIsin(), a.getProvider()));
    return merged;
  }

  private LimitCheckEvent event(
      TulevaFund fund, LocalDate checkDate, CheckType checkType, List<?> breaches) {
    var hasBreaches =
        breaches.stream()
            .anyMatch(
                b -> {
                  if (b instanceof PositionBreach pb) return pb.severity() != BreachSeverity.OK;
                  if (b instanceof ProviderBreach pb) return pb.severity() != BreachSeverity.OK;
                  return false;
                });

    return event(fund, checkDate, checkType, hasBreaches, Map.of("breaches", breaches));
  }

  private LimitCheckEvent event(
      TulevaFund fund,
      LocalDate checkDate,
      CheckType checkType,
      @org.jspecify.annotations.Nullable ReserveBreach breach) {
    return event(
        fund,
        checkDate,
        checkType,
        breach != null && breach.severity() != BreachSeverity.OK,
        breach != null ? Map.of("breach", breach) : Map.of());
  }

  private LimitCheckEvent event(
      TulevaFund fund,
      LocalDate checkDate,
      CheckType checkType,
      @org.jspecify.annotations.Nullable FreeCashBreach breach) {
    return event(
        fund,
        checkDate,
        checkType,
        breach != null && breach.severity() != BreachSeverity.OK,
        breach != null ? Map.of("breach", breach) : Map.of());
  }

  private LimitCheckEvent event(
      TulevaFund fund,
      LocalDate checkDate,
      CheckType checkType,
      boolean breachesFound,
      Map<String, Object> result) {
    return LimitCheckEvent.builder()
        .fund(fund)
        .checkDate(checkDate)
        .checkType(checkType)
        .breachesFound(breachesFound)
        .result(result)
        .build();
  }
}
