package ee.tuleva.onboarding.investment.report;

import static software.amazon.awssdk.regions.Region.EU_CENTRAL_1;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
class InvestmentReportsS3Configuration {

  @Bean
  S3Client s3Client(
      @Value("${investment.reports.s3.access-key}") String accessKey,
      @Value("${investment.reports.s3.secret-key}") String secretKey) {
    return S3Client.builder()
        .region(EU_CENTRAL_1)
        .credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
        .build();
  }
}
