package ee.tuleva.onboarding.aml.risklevel;

import static java.time.Duration.ofMillis;

import java.net.http.HttpClient;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Slf4j
@Configuration
@Profile({"!dev & !staging"})
@EnableConfigurationProperties(AmlServiceProperties.class)
class AmlServiceConfiguration {

  @Bean
  AmlServiceClient amlServiceClient(RestClient.Builder builder, AmlServiceProperties properties) {
    warnOfWhatAMissingApiTokenStops(properties);
    return new AmlServiceClient(
        amlServiceRestClient(builder, properties), amlServiceRetryTemplate(), properties);
  }

  private static void warnOfWhatAMissingApiTokenStops(AmlServiceProperties properties) {
    if (properties.configuredApiToken().isEmpty()) {
      log.warn(
          "aml-service API token is not configured, so every KYC survey submission fails, the"
              + " nightly risk level run fails, and every person and company redemption that"
              + " screening does not freeze is held, at its screening deadline at the latest, until"
              + " it is: missing=api-token (AML_SERVICE_API_TOKEN)");
    }
  }

  static RestClient amlServiceRestClient(
      RestClient.Builder builder, AmlServiceProperties properties) {
    return restClient(
        builder, properties.url(), properties.connectTimeout(), properties.readTimeout());
  }

  static RestClient restClient(
      RestClient.Builder builder, String url, Duration connectTimeout, Duration readTimeout) {
    var httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    var requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(readTimeout);
    return builder.baseUrl(url).requestFactory(requestFactory).build();
  }

  static RetryTemplate amlServiceRetryTemplate() {
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
