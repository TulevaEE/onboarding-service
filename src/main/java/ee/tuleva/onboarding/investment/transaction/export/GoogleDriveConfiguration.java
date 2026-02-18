package ee.tuleva.onboarding.investment.transaction.export;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(GoogleDriveProperties.class)
class GoogleDriveConfiguration {

  @Bean
  @ConditionalOnProperty(name = "google-drive.enabled", havingValue = "true")
  GoogleDriveTokenProvider googleDriveTokenProvider(GoogleDriveProperties properties, Clock clock) {
    return new GoogleDriveTokenProvider(
        properties.serviceAccountJson(),
        RestClient.builder().baseUrl("https://oauth2.googleapis.com/token").build(),
        clock);
  }

  @Bean
  @ConditionalOnProperty(name = "google-drive.enabled", havingValue = "true")
  GoogleDriveClient googleDriveClient(GoogleDriveTokenProvider tokenProvider) {
    return new GoogleDriveClient(
        RestClient.builder()
            .baseUrl("https://www.googleapis.com/drive/v3")
            .requestInterceptor(
                (request, body, execution) -> {
                  request.getHeaders().setBearerAuth(tokenProvider.getAccessToken());
                  return execution.execute(request, body);
                })
            .build());
  }

  @Bean
  @ConditionalOnProperty(name = "google-drive.enabled", havingValue = "true")
  TransactionExportUploader transactionExportUploader(GoogleDriveClient driveClient) {
    return new TransactionExportUploader(driveClient);
  }
}
