package ee.tuleva.onboarding.fund;

import static ee.tuleva.onboarding.config.CacheConfiguration.FUND_TABLE_CACHE;

import ee.tuleva.onboarding.fund.Fund.FundStatus;
import java.util.List;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.repository.CrudRepository;

public interface FundRepository extends CrudRepository<Fund, Long> {

  @Cacheable(
      cacheManager = "ttlCacheManager",
      cacheNames = FUND_TABLE_CACHE,
      key = "'byManager:' + #fundManagerName.toLowerCase()")
  Iterable<Fund> findAllByFundManagerNameIgnoreCase(String fundManagerName);

  @Cacheable(
      cacheManager = "ttlCacheManager",
      cacheNames = FUND_TABLE_CACHE,
      key = "'byIsin:' + #isin",
      unless = "#result == null")
  Fund findByIsin(String isin);

  @Cacheable(
      cacheManager = "ttlCacheManager",
      cacheNames = FUND_TABLE_CACHE,
      key = "'byPillar:' + #pillar")
  List<Fund> findAllByPillar(Integer pillar);

  @Cacheable(
      cacheManager = "ttlCacheManager",
      cacheNames = FUND_TABLE_CACHE,
      key = "'byStatus:' + #status")
  List<Fund> findAllByStatus(FundStatus status);

  @Cacheable(
      cacheManager = "ttlCacheManager",
      cacheNames = FUND_TABLE_CACHE,
      key = "'byPillarAndStatus:' + #pillar + ':' + #status")
  List<Fund> findAllByPillarAndStatus(Integer pillar, FundStatus status);

  @Cacheable(cacheManager = "ttlCacheManager", cacheNames = FUND_TABLE_CACHE, key = "'all'")
  @Override
  Iterable<Fund> findAll();

  @CacheEvict(
      cacheManager = "ttlCacheManager",
      cacheNames = FUND_TABLE_CACHE,
      allEntries = true,
      beforeInvocation = true)
  @Override
  <S extends Fund> S save(S entity);
}
