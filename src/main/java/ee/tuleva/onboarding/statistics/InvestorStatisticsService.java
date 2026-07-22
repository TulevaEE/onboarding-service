package ee.tuleva.onboarding.statistics;

import static ee.tuleva.onboarding.config.CacheConfiguration.INVESTOR_COUNT_CACHE;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InvestorStatisticsService {

  private final InvestorStatisticsRepository investorStatisticsRepository;

  @Cacheable(cacheManager = "ttlCacheManager", cacheNames = INVESTOR_COUNT_CACHE)
  public long getActiveInvestorCount() {
    return investorStatisticsRepository.getActiveInvestorCount();
  }
}
