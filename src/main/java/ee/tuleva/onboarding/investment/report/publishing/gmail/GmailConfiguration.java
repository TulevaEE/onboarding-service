package ee.tuleva.onboarding.investment.report.publishing.gmail;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(GmailProperties.class)
class GmailConfiguration {

  @Bean
  @ConditionalOnProperty(name = "investment-report-publishing.enabled", havingValue = "true")
  GmailTokenProvider gmailTokenProvider(GmailProperties properties, Clock clock) {
    return new GmailTokenProvider(
        properties.serviceAccountJson(),
        properties.delegateUser(),
        RestClient.builder().baseUrl("https://oauth2.googleapis.com/token").build(),
        clock);
  }

  @Bean
  @ConditionalOnProperty(name = "investment-report-publishing.enabled", havingValue = "true")
  GmailDraftClient gmailDraftClient(GmailTokenProvider tokenProvider) {
    return new GmailDraftClient(
        RestClient.builder()
            .baseUrl("https://gmail.googleapis.com/gmail/v1")
            .requestInterceptor(
                (request, body, execution) -> {
                  request.getHeaders().setBearerAuth(tokenProvider.getAccessToken());
                  return execution.execute(request, body);
                })
            .build());
  }
}
