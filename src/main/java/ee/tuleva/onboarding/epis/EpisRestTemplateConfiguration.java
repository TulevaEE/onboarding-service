package ee.tuleva.onboarding.epis;

import ee.tuleva.onboarding.error.RestResponseErrorHandler;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@Slf4j
public class EpisRestTemplateConfiguration {

  @Bean
  public RestTemplate episRestTemplate(
      RestTemplateBuilder restTemplateBuilder, RestResponseErrorHandler errorHandler) {
    log.info("Creating dedicated 'episRestTemplate' with 320 seconds timeouts.");
    return restTemplateBuilder
        .errorHandler(errorHandler)
        .connectTimeout(Duration.ofSeconds(320))
        .readTimeout(Duration.ofSeconds(320))
        .build();
  }
}
