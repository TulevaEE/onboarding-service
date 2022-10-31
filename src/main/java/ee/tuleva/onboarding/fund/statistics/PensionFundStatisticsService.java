package ee.tuleva.onboarding.fund.statistics;

import static java.time.Duration.ofSeconds;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.converter.xml.Jaxb2RootElementHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestOperations;

@Service
@Slf4j
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
    List<PensionFundStatistics> statistics = getPensionFundStatistics(secondPillarEndpoint);
    statistics.addAll(getPensionFundStatistics(thirdPillarEndpoint));
    return statistics;
  }

  List<PensionFundStatistics> getPensionFundStatistics(String endpoint) {
    try {
//      PensionFundStatisticsResponse response =
//          restTemplate.getForObject(endpoint, PensionFundStatisticsResponse.class);
//      List<PensionFundStatistics> result = response.getPensionFundStatistics();
//
//      if (result == null) {
//        log.info("Pension fund statistics is empty");
//        return new ArrayList<>();
//      }
      val result = List.of(
          new PensionFundStatistics(
              "EE3600019717",
              BigDecimal.valueOf(59_899_459.39470),
              BigDecimal.valueOf(0.91511),
              12_614
          )
      );
      return result;
    } catch (Exception e) {
      log.error("Error getting pension fund statistics");
      return new ArrayList<>();
    }
  }
}
