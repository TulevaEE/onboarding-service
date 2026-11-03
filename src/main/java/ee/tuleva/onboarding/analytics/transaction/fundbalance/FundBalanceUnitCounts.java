package ee.tuleva.onboarding.analytics.transaction.fundbalance;

import static ee.tuleva.onboarding.config.CacheConfiguration.FUND_INVESTOR_COUNTS_CACHE;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.analytics.FundUnitCounts;
import ee.tuleva.onboarding.fund.FundInvestorsInRegister;
import ee.tuleva.onboarding.fund.FundManagerUnitsInRegister;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class FundBalanceUnitCounts
    implements FundUnitCounts, FundManagerUnitsInRegister, FundInvestorsInRegister {

  private final FundBalanceRepository fundBalanceRepository;

  @Override
  public Optional<BigDecimal> totalUnitsAsOf(String isin, LocalDate date) {
    return fundBalanceRepository
        .findFirstByIsinAndRequestDateLessThanEqualOrderByRequestDateDesc(isin, date)
        .map(balance -> balance.getCountUnits().add(balance.getCountUnitsFm()));
  }

  @Override
  @Cacheable(cacheManager = "ttlCacheManager", cacheNames = FUND_INVESTOR_COUNTS_CACHE, sync = true)
  public Map<String, Integer> latestInvestorCounts() {
    return fundBalanceRepository.findAllOnLatestRequestDate().stream()
        .collect(
            toMap(
                FundBalance::getIsin,
                FundBalance::getCountInvestors,
                (stored, storedAgain) -> stored));
  }

  @Override
  public Optional<BigDecimal> fundManagerUnitsOn(String isin, LocalDate date) {
    return fundBalanceRepository
        .findFirstByIsinAndRequestDateLessThanEqualOrderByRequestDateDesc(isin, date)
        .filter(balance -> date.equals(balance.getRequestDate()))
        .map(FundBalance::getCountUnitsFm);
  }
}
