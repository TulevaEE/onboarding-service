package ee.tuleva.onboarding.fund.statistics;

import static java.time.Duration.ofSeconds;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.http.converter.xml.Jaxb2RootElementHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestOperations;

@Service
@Slf4j
@Profile({"!dev & !staging"})
public class PensionFundStatisticsService {

  @Value("${pensionikeskus.statistics.2ndpillar.url}")
  private String secondPillarEndpoint;

  @Value("${pensionikeskus.statistics.3rdpillar.url}")
  private String thirdPillarEndpoint;

  private final RestOperations restTemplate;

  private static final String PENSION_FUND_STATISTICS_CACHE = "pensionFundStatistics";

  public PensionFundStatisticsService(RestTemplateBuilder restTemplateBuilder) {
    restTemplate =
        restTemplateBuilder
            .connectTimeout(ofSeconds(60))
            .readTimeout(ofSeconds(60))
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
    List<PensionFundStatistics> statistics = getPensionFundStatistics(secondPillarEndpoint);
    statistics.addAll(getPensionFundStatistics(thirdPillarEndpoint));
    return statistics;
  }

  List<PensionFundStatistics> getPensionFundStatistics(String endpoint) {
    try {
      PensionFundStatisticsResponse response =
          restTemplate.getForObject(endpoint, PensionFundStatisticsResponse.class);
      List<PensionFundStatistics> result = response.getPensionFundStatistics();

      if (result == null) {
        log.info("Pension fund statistics is empty");
        return new ArrayList<>();
      }
      return result;
    } catch (Exception e) {
      log.error(
          "Error getting pension fund statistics: {}, {}",
          e.getClass().getSimpleName(),
          e.getMessage());
      return new ArrayList<>();
    }
  }
}
