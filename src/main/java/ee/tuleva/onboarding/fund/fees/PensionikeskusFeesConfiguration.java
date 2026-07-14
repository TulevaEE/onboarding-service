package ee.tuleva.onboarding.fund.fees;

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
@EnableConfigurationProperties(PensionikeskusFeesProperties.class)
class PensionikeskusFeesConfiguration {

  @Bean
  RestClient pensionikeskusFeesRestClient(RestClient.Builder builder) {
    var httpClient = HttpClient.newBuilder().connectTimeout(ofSeconds(10)).build();
    var requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(ofSeconds(30));
    return builder.requestFactory(requestFactory).build();
  }

  @Bean
  RetryTemplate pensionikeskusFeesRetryTemplate() {
    var policy =
        RetryPolicy.builder()
            .includes(HttpServerErrorException.class, ResourceAccessException.class)
            .excludes(HttpClientErrorException.class)
            .maxRetries(3)
            .delay(ofSeconds(1))
            .multiplier(2)
            .maxDelay(ofSeconds(10))
            .build();
    return new RetryTemplate(policy);
  }
}
