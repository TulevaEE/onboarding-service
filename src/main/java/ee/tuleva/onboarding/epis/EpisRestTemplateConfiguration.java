package ee.tuleva.onboarding.epis;

import ee.tuleva.onboarding.error.RestResponseErrorHandler;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@Slf4j
public class EpisRestTemplateConfiguration {

  @Bean
  public RestTemplate episRestTemplate(
      RestTemplateBuilder restTemplateBuilder,
      RestResponseErrorHandler errorHandler,
      @Value("${epis.service.read-timeout:60s}") Duration readTimeout) {
    log.info("Creating episRestTemplate: readTimeout={}", readTimeout);
    return restTemplateBuilder
        .errorHandler(errorHandler)
        .connectTimeout(Duration.ofSeconds(30))
        .readTimeout(readTimeout)
        .build();
  }

  @Bean
  public RestTemplate episLongRequestRestTemplate(
      RestTemplateBuilder restTemplateBuilder,
      RestResponseErrorHandler errorHandler,
      @Value("${epis.service.long-request-read-timeout:15m}") Duration readTimeout) {
    log.info("Creating episLongRequestRestTemplate: readTimeout={}", readTimeout);
    return restTemplateBuilder
        .errorHandler(errorHandler)
        .connectTimeout(Duration.ofSeconds(30))
        .readTimeout(readTimeout)
        .build();
  }
}
