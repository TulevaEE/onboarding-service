package ee.tuleva.onboarding.fund.statistics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.converter.xml.Jaxb2RootElementHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestOperations;

import java.util.List;

import static java.time.Duration.ofSeconds;
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
        .setConnectTimeout(ofSeconds(60))
        .setReadTimeout(ofSeconds(60))
        .additionalMessageConverters(new Jaxb2RootElementHttpMessageConverter())
      .build();
  }

  @Cacheable(value = PENSION_FUND_STATISTICS_CACHE, unless = "#result.isEmpty()")
  public List<PensionFundStatistics> getCachedStatistics() {
    return getPensionFundStatistics();
  }

  @CachePut(value = PENSION_FUND_STATISTICS_CACHE, unless = "#result.isEmpty()")
  public List<PensionFundStatistics> refreshCachedStatistics() {
    return getPensionFundStatistics();
  }

  List<PensionFundStatistics> getPensionFundStatistics() {
    try {
        PensionFundStatisticsResponse response = restTemplate.getForObject(statisticsEndpoint,
            PensionFundStatisticsResponse.class);
        List<PensionFundStatistics> result = response.getPensionFundStatistics();

      if(result == null) {
        log.info("Pension fund statistics is empty");
        return emptyList();
      }
      return result;
    } catch (Exception e) {
      log.error("Error getting pension fund statistics");
      return emptyList();
    }
  }

}