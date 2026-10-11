package ee.tuleva.onboarding.accounting.directo;

import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(DirectoProperties.class)
class DirectoConfiguration {

  private final DirectoProperties properties;

  @Bean
  RetryTemplate directoRetryTemplate() {
    var policy =
        RetryPolicy.builder()
            .includes(HttpServerErrorException.class, ResourceAccessException.class)
            .excludes(HttpClientErrorException.class)
            .maxRetries(2)
            .delay(properties.retryDelay())
            .multiplier(2)
            .build();
    return new RetryTemplate(policy);
  }

  @Bean
  DirectoClient directoClient(RestClient.Builder builder) {
    properties
        .missingKeyEnvironmentVariables()
        .forEach(
            environmentVariable ->
                log.warn(
                    "Directo API key missing, so that entity's general ledger sync fails until it"
                        + " is set: environmentVariable={}",
                    environmentVariable));
    var restClient =
        builder.baseUrl(properties.url()).defaultHeader(ACCEPT, APPLICATION_JSON_VALUE).build();
    return new DirectoClient(restClient, directoRetryTemplate(), properties);
  }
}
