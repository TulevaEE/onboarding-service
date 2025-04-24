package ee.tuleva.onboarding.epis;

import ee.tuleva.onboarding.error.RestResponseErrorHandler;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@Slf4j
public class EpisRestTemplateConfiguration {

  @Bean
  @Qualifier("episRestTemplate")
  public RestTemplate episRestTemplate(
      RestTemplateBuilder restTemplateBuilder, RestResponseErrorHandler errorHandler) {
    log.info("Creating dedicated 'episRestTemplate' with 320 seconds timeouts.");
    return restTemplateBuilder
        .errorHandler(errorHandler)
        .setConnectTimeout(Duration.ofSeconds(320))
        .setReadTimeout(Duration.ofSeconds(320))
        .build();
  }
}
