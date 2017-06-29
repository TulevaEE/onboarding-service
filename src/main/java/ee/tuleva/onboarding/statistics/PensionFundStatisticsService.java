package ee.tuleva.onboarding.statistics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestOperations;

@Service
@Slf4j
public class PensionFundStatisticsService {

  @Value("${pensionikeskus.statistics.endpoint.url}")
  private String statisticsEndpoint;

  private final RestOperations restTemplate;

  public PensionFundStatisticsService(RestTemplateBuilder restTemplateBuilder) {
    restTemplate = restTemplateBuilder.build();
  }

  public PensionFundStatisticsResponse getPensionFundStatistics() {
    return restTemplate.getForObject(statisticsEndpoint, PensionFundStatisticsResponse.class);
  }

}