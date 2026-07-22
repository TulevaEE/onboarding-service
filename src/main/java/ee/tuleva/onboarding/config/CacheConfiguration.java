package ee.tuleva.onboarding.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableCaching
@NullMarked
public class CacheConfiguration {

  public static final String FUND_TABLE_CACHE = "fundTable";
  public static final String INVESTOR_COUNT_CACHE = "investorCount";

  private static final Duration TTL = Duration.ofMinutes(15);

  @Bean
  @Primary
  CacheManager cacheManager(@Value("${spring.cache.type:simple}") String cacheType) {
    if (cachingDisabled(cacheType)) {
      return new NoOpCacheManager();
    }
    return new ConcurrentMapCacheManager();
  }

  @Bean
  CacheManager ttlCacheManager(@Value("${spring.cache.type:simple}") String cacheType) {
    if (cachingDisabled(cacheType)) {
      return new NoOpCacheManager();
    }
    CaffeineCacheManager cacheManager =
        new CaffeineCacheManager(FUND_TABLE_CACHE, INVESTOR_COUNT_CACHE);
    cacheManager.setCaffeine(Caffeine.newBuilder().expireAfterWrite(TTL).maximumSize(1_000));
    return cacheManager;
  }

  private static boolean cachingDisabled(String cacheType) {
    return "none".equalsIgnoreCase(cacheType);
  }
}
