package ee.tuleva.onboarding.investment.report.publishing.wordpress;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;

import java.net.http.HttpClient;
import java.util.Base64;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@EnableConfigurationProperties(WordPressProperties.class)
class WordPressConfiguration {

  @Bean
  @ConditionalOnProperty(name = "investment-report-publishing.enabled", havingValue = "true")
  WordPressMediaClient wordPressMediaClient(WordPressProperties properties) {
    var credentials = properties.username() + ":" + properties.appPassword();
    var basicAuth = Base64.getEncoder().encodeToString(credentials.getBytes());

    var requestFactory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(ofSeconds(5)).build());
    requestFactory.setReadTimeout(ofSeconds(30));

    var restClient =
        RestClient.builder()
            .baseUrl(properties.apiBase())
            .requestFactory(requestFactory)
            .requestInterceptor(
                (request, body, execution) -> {
                  request.getHeaders().set("Authorization", "Basic " + basicAuth);
                  return execution.execute(request, body);
                })
            .build();
    return new WordPressMediaClient(restClient, wordPressRetryTemplate());
  }

  private static RetryTemplate wordPressRetryTemplate() {
    var policy =
        RetryPolicy.builder()
            .includes(HttpServerErrorException.class, ResourceAccessException.class)
            .excludes(HttpClientErrorException.class)
            .maxRetries(2)
            .delay(ofMillis(500))
            .multiplier(2)
            .maxDelay(ofSeconds(2))
            .build();
    return new RetryTemplate(policy);
  }
}
