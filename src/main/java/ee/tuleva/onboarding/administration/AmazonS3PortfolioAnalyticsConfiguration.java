package ee.tuleva.onboarding.administration;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AmazonS3PortfolioAnalyticsConfiguration {
  @Value("${administration.portfolio.key}")
  private String accessKey;

  @Value("${administration.portfolio.secret}")
  private String secretKey;

  @Bean
  public AmazonS3 amazonS3Client() {
    return AmazonS3ClientBuilder.standard()
        .withRegion(Regions.EU_CENTRAL_1)
        .withCredentials(
            new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey)))
        .build();
  }
}
