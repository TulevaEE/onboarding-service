package ee.tuleva.onboarding.populationregister;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;

import java.net.http.HttpClient;
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

@Configuration
@EnableConfigurationProperties(PopulationRegisterProperties.class)
class PopulationRegisterConfiguration {

  @Bean
  RestClient populationRegisterRestClient(
      RestClient.Builder builder, PopulationRegisterProperties properties) {
    var httpClient = HttpClient.newBuilder().connectTimeout(ofSeconds(3)).build();
    var requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(ofSeconds(10));
    return builder.baseUrl(properties.url()).requestFactory(requestFactory).build();
  }

  @Bean
  RetryTemplate populationRegisterRetryTemplate() {
    var policy =
        RetryPolicy.builder()
            .includes(HttpServerErrorException.class, ResourceAccessException.class)
            .excludes(HttpClientErrorException.class)
            .maxRetries(1)
            .delay(ofMillis(200))
            .multiplier(2)
            .maxDelay(ofSeconds(2))
            .build();
    return new RetryTemplate(policy);
  }
}
