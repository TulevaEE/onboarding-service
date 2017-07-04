package ee.tuleva.onboarding.fund.statistics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestOperations;

import java.util.List;

import static java.util.Collections.emptyList;

@Service
@Slf4j
public class PensionFundStatisticsService {

  @Value("${pensionikeskus.statistics.endpoint.url}")
  private String statisticsEndpoint;

  private final RestOperations restTemplate;

  private static final String PENSION_FUND_STATISTICS_CACHE = "pensionFundStatistics";

  public PensionFundStatisticsService(RestTemplateBuilder restTemplateBuilder) {
    restTemplate = restTemplateBuilder
      .setConnectTimeout(30_000)
      .setReadTimeout(60_000)
      .build();
  }

  @Cacheable(PENSION_FUND_STATISTICS_CACHE)
  public List<PensionFundStatistics> getCachedStatistics() {
    return getPensionFundStatistics();
  }

  @CachePut(PENSION_FUND_STATISTICS_CACHE)
  public List<PensionFundStatistics> refreshCachedStatistics() {
    return getPensionFundStatistics();
  }

  List<PensionFundStatistics> getPensionFundStatistics() {
    try {
      return restTemplate.getForObject(statisticsEndpoint, PensionFundStatisticsResponse.class).getPensionFundStatistics();
    } catch (Exception e) {
      log.error("Error getting pension fund statistics", e);
      return emptyList();
    }
  }

}