package ee.tuleva.onboarding.administration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class AmazonS3PortfolioAnalyticsConfiguration {

  @Value("${administration.portfolio.key}")
  private String accessKey;

  @Value("${administration.portfolio.secret}")
  private String secretKey;

  @Bean
  public S3Client amazonS3Client() {
    return S3Client.builder()
        .region(Region.EU_CENTRAL_1)
        .credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
        .build();
  }
}
