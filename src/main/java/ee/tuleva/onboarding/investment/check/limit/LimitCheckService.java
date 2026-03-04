package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.investment.check.limit.CheckType.*;
import static ee.tuleva.onboarding.investment.position.AccountType.*;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.calculation.PositionCalculationRepository;
import ee.tuleva.onboarding.investment.portfolio.*;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
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
  private final PositionCalculationRepository positionCalculationRepository;
  private final FundPositionRepository fundPositionRepository;
  private final PositionLimitRepository positionLimitRepository;
  private final ProviderLimitRepository providerLimitRepository;
  private final FundLimitRepository fundLimitRepository;
  private final ModelPortfolioAllocationRepository modelPortfolioAllocationRepository;
  private final LimitCheckEventRepository limitCheckEventRepository;
  private final PositionLimitChecker positionLimitChecker;
  private final ProviderLimitChecker providerLimitChecker;
  private final ReserveLimitChecker reserveLimitChecker;
  private final FreeCashLimitChecker freeCashLimitChecker;

  List<LimitCheckResult> runChecks() {
    var today = LocalDate.now(clock);
    var results = new ArrayList<LimitCheckResult>();

    for (var fund : TulevaFund.values()) {
      var latestDate = positionCalculationRepository.getLatestDateUpTo(fund, today);
      if (latestDate.isEmpty()) {
        log.warn("No position data for fund: fund={}", fund);
        continue;
      }

      var checkDate = latestDate.get();
      var result = checkFund(fund, checkDate);
      results.add(result);
    }

    return results;
  }

  private LimitCheckResult checkFund(TulevaFund fund, LocalDate checkDate) {
    var positions = positionCalculationRepository.findByFundAndDate(fund, checkDate);
    var totalNav =
        fundPositionRepository.sumMarketValueByFundAndAccountTypes(
            fund, checkDate, List.of(CASH, SECURITY, RECEIVABLES, LIABILITY));

    var cashTotal =
        sumMarketValues(
            fundPositionRepository.findByNavDateAndFundAndAccountType(checkDate, fund, CASH));
    var liabilityTotal =
        sumMarketValues(
            fundPositionRepository.findByNavDateAndFundAndAccountType(checkDate, fund, LIABILITY));

    var positionLimits = positionLimitRepository.findLatestByFund(fund);
    var providerLimits = providerLimitRepository.findLatestByFund(fund);
    var fundLimit = fundLimitRepository.findLatestByFund(fund).orElse(null);
    var isinToProvider = buildIsinToProviderMap(fund);

    var positionBreaches = positionLimitChecker.check(fund, positions, totalNav, positionLimits);
    var providerBreaches =
        providerLimitChecker.check(fund, positions, totalNav, isinToProvider, providerLimits);
    var reserveBreach = reserveLimitChecker.check(fund, cashTotal, fundLimit);
    var freeCashBreach = freeCashLimitChecker.check(fund, cashTotal, liabilityTotal, fundLimit);

    saveEvent(fund, checkDate, POSITION, positionBreaches);
    saveEvent(fund, checkDate, PROVIDER, providerBreaches);
    saveEvent(fund, checkDate, RESERVE, reserveBreach);
    saveEvent(fund, checkDate, FREE_CASH, freeCashBreach);

    return new LimitCheckResult(
        fund, checkDate, positionBreaches, providerBreaches, reserveBreach, freeCashBreach);
  }

  private BigDecimal sumMarketValues(List<FundPosition> positions) {
    return positions.stream()
        .map(FundPosition::getMarketValue)
        .filter(Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private Map<String, Provider> buildIsinToProviderMap(TulevaFund fund) {
    return modelPortfolioAllocationRepository.findLatestByFund(fund).stream()
        .filter(a -> a.getIsin() != null && a.getProvider() != null)
        .collect(
            Collectors.toMap(
                ModelPortfolioAllocation::getIsin, ModelPortfolioAllocation::getProvider));
  }

  private void saveEvent(
      TulevaFund fund, LocalDate checkDate, CheckType checkType, List<?> breaches) {
    var hasBreaches =
        breaches.stream()
            .anyMatch(
                b -> {
                  if (b instanceof PositionBreach pb) return pb.severity() != BreachSeverity.OK;
                  if (b instanceof ProviderBreach pb) return pb.severity() != BreachSeverity.OK;
                  return false;
                });

    var event =
        LimitCheckEvent.builder()
            .fund(fund)
            .checkDate(checkDate)
            .checkType(checkType)
            .breachesFound(hasBreaches)
            .result(Map.of("breaches", breaches))
            .build();

    limitCheckEventRepository.save(event);
  }

  private void saveEvent(
      TulevaFund fund,
      LocalDate checkDate,
      CheckType checkType,
      @org.jspecify.annotations.Nullable ReserveBreach breach) {
    var hasBreaches = breach != null && breach.severity() != BreachSeverity.OK;

    var event =
        LimitCheckEvent.builder()
            .fund(fund)
            .checkDate(checkDate)
            .checkType(checkType)
            .breachesFound(hasBreaches)
            .result(breach != null ? Map.of("breach", breach) : Map.of())
            .build();

    limitCheckEventRepository.save(event);
  }

  private void saveEvent(
      TulevaFund fund,
      LocalDate checkDate,
      CheckType checkType,
      @org.jspecify.annotations.Nullable FreeCashBreach breach) {
    var hasBreaches = breach != null && breach.severity() != BreachSeverity.OK;

    var event =
        LimitCheckEvent.builder()
            .fund(fund)
            .checkDate(checkDate)
            .checkType(checkType)
            .breachesFound(hasBreaches)
            .result(breach != null ? Map.of("breach", breach) : Map.of())
            .build();

    limitCheckEventRepository.save(event);
  }
}
