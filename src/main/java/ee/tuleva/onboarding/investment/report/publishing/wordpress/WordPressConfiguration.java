package ee.tuleva.onboarding.investment.report.publishing.wordpress;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;

import java.net.http.HttpClient;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Configuration
@EnableConfigurationProperties(WordPressProperties.class)
class WordPressConfiguration {

  @Bean
  @ConditionalOnProperty(name = "investment-report-publishing.enabled", havingValue = "true")
  WordPressMediaClient wordPressMediaClient(WordPressProperties properties) {
    var missingProperties = properties.missingPropertyNames();
    if (!missingProperties.isEmpty()) {
      log.warn(
          "WordPress report publishing is enabled but not configured, so publishing will fail"
              + " until it is: missing={}",
          missingProperties);
    }
    return new WordPressMediaClient(
        authenticatedRestClient(properties), wordPressRetryTemplate(), missingProperties);
  }

  @Bean
  WordPressPageReader wordPressPageReader(WordPressProperties properties) {
    return new WordPressPageReader(
        wordPressRestClientBuilder(properties).build(), wordPressRetryTemplate());
  }

  private static RestClient authenticatedRestClient(WordPressProperties properties) {
    var basicAuth = basicAuth(properties);
    return wordPressRestClientBuilder(properties)
        .requestInterceptor(
            (request, body, execution) -> {
              request.getHeaders().set("Authorization", "Basic " + basicAuth);
              return execution.execute(request, body);
            })
        .build();
  }

  private static RestClient.Builder wordPressRestClientBuilder(WordPressProperties properties) {
    var requestFactory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(ofSeconds(5)).build());
    requestFactory.setReadTimeout(ofSeconds(30));

    var builder = RestClient.builder().requestFactory(requestFactory);
    var apiBase = properties.apiBase();
    if (apiBase != null) {
      builder.baseUrl(apiBase);
    }
    return builder;
  }

  private static String basicAuth(WordPressProperties properties) {
    var credentials = properties.username() + ":" + properties.appPassword();
    return Base64.getEncoder().encodeToString(credentials.getBytes(UTF_8));
  }

  private RetryTemplate wordPressRetryTemplate() {
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
