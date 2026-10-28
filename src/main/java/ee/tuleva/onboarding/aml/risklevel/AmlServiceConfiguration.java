package ee.tuleva.onboarding.aml.risklevel;

import static java.time.Duration.ofMillis;

import java.net.http.HttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Slf4j
@Configuration
@EnableConfigurationProperties(AmlServiceProperties.class)
class AmlServiceConfiguration {

  @Bean
  RestClient amlServiceRestClient(RestClient.Builder builder, AmlServiceProperties properties) {
    if (properties.enabled() && properties.configuredApiToken().isEmpty()) {
      log.warn(
          "aml-service API token is not configured, so the 05:30 risk level run falls back to the"
              + " materialized views each night until it is: missing=api-token"
              + " (AML_SERVICE_API_TOKEN)");
    }
    var httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
    var requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(properties.readTimeout());
    return builder.baseUrl(properties.url()).requestFactory(requestFactory).build();
  }

  @Bean
  RetryTemplate amlServiceRetryTemplate() {
    var policy =
        RetryPolicy.builder()
            .includes(HttpServerErrorException.class, ResourceAccessException.class)
            .excludes(HttpClientErrorException.class)
            .maxRetries(1)
            .delay(ofMillis(500))
            .build();
    return new RetryTemplate(policy);
  }
}
