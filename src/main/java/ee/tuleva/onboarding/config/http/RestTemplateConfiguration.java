package ee.tuleva.onboarding.config.http;

import ee.tuleva.onboarding.error.RestResponseErrorHandler;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.boot.restclient.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
@Slf4j
public class RestTemplateConfiguration {

  @Bean
  @Primary
  RestTemplate restTemplate(
      RestTemplateBuilder restTemplateBuilder, RestResponseErrorHandler errorHandler) {
    return restTemplateBuilder
        .errorHandler(errorHandler)
        .connectTimeout(Duration.ofSeconds(60))
        .readTimeout(Duration.ofSeconds(60))
        .build();
  }

  @Bean
  RestTemplateCustomizer loggingRestTemplateCustomizer() {
    return restTemplate -> {
      SimpleClientHttpRequestFactory simpleClient = new SimpleClientHttpRequestFactory();
      restTemplate.setRequestFactory(new BufferingClientHttpRequestFactory(simpleClient));
      restTemplate
          .getInterceptors()
          .add(
              (request, body, execution) -> {
                log.info("Sending {} request to {}", request.getMethod(), request.getURI());
                ClientHttpResponse response = execution.execute(request, body);
                log.info("Response status {}", response.getStatusCode());
                log.debug(
                    "Response body \n {}",
                    IOUtils.toString(response.getBody(), StandardCharsets.UTF_8));
                return response;
              });
    };
  }
}
