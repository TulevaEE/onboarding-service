package ee.tuleva.onboarding.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.support.NoOpCacheManager;

class CacheConfigurationTest {

  private final CacheConfiguration configuration = new CacheConfiguration();

  @Test
  void cacheManagerIsNoOpWhenCachingDisabled() {
    CacheManager cacheManager = configuration.cacheManager("none");

    assertThat(cacheManager).isInstanceOf(NoOpCacheManager.class);
  }

  @Test
  void cacheManagerIsNoOpWhenCachingDisabledCaseInsensitively() {
    CacheManager cacheManager = configuration.cacheManager("NONE");

    assertThat(cacheManager).isInstanceOf(NoOpCacheManager.class);
  }

  @Test
  void cacheManagerIsConcurrentMapWhenCachingEnabled() {
    CacheManager cacheManager = configuration.cacheManager("simple");

    assertThat(cacheManager).isInstanceOf(ConcurrentMapCacheManager.class);
  }

  @Test
  void ttlCacheManagerIsNoOpWhenCachingDisabled() {
    CacheManager cacheManager = configuration.ttlCacheManager("none");

    assertThat(cacheManager).isInstanceOf(NoOpCacheManager.class);
  }

  @Test
  void ttlCacheManagerHoldsTheFundTableAndInvestorCountCachesWhenCachingEnabled() {
    CacheManager cacheManager = configuration.ttlCacheManager("simple");

    assertThat(cacheManager).isInstanceOf(CaffeineCacheManager.class);
    assertThat(cacheManager.getCacheNames())
        .containsExactlyInAnyOrder(
            CacheConfiguration.FUND_TABLE_CACHE, CacheConfiguration.INVESTOR_COUNT_CACHE);
  }

  @Test
  void ttlCacheManagerAppliesA15MinuteExpiryAndA1000EntryCap() {
    CaffeineCacheManager cacheManager =
        (CaffeineCacheManager) configuration.ttlCacheManager("simple");

    com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache =
        (com.github.benmanes.caffeine.cache.Cache<Object, Object>)
            cacheManager.getCache(CacheConfiguration.FUND_TABLE_CACHE).getNativeCache();

    assertThat(nativeCache.policy().expireAfterWrite().orElseThrow().getExpiresAfter())
        .isEqualTo(Duration.ofMinutes(15));
    assertThat(nativeCache.policy().eviction().orElseThrow().getMaximum()).isEqualTo(1_000L);
  }
}
