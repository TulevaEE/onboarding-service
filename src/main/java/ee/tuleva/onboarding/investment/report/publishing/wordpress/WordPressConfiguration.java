package ee.tuleva.onboarding.investment.report.publishing.wordpress;

import java.util.Base64;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(WordPressProperties.class)
class WordPressConfiguration {

  @Bean
  @ConditionalOnProperty(name = "investment-report-publishing.enabled", havingValue = "true")
  WordPressMediaClient wordPressMediaClient(WordPressProperties properties) {
    var credentials = properties.username() + ":" + properties.appPassword();
    var basicAuth = Base64.getEncoder().encodeToString(credentials.getBytes());

    var restClient =
        RestClient.builder()
            .baseUrl(properties.apiBase())
            .requestInterceptor(
                (request, body, execution) -> {
                  request.getHeaders().set("Authorization", "Basic " + basicAuth);
                  return execution.execute(request, body);
                })
            .build();
    return new WordPressMediaClient(restClient);
  }
}
