package ee.tuleva.onboarding.investment.report;

import static org.assertj.core.api.Assertions.assertThat;
import static software.amazon.awssdk.regions.Region.EU_CENTRAL_1;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.identity.spi.AwsCredentialsIdentity;
import software.amazon.awssdk.services.s3.S3Client;

class InvestmentReportsS3ConfigurationTest {

  @Test
  void buildsAFrankfurtClientWithStaticCredentialsFromTheAdministrationAnalyticsKeyAndSecret() {
    new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withUserConfiguration(InvestmentReportsS3Configuration.class)
        .withSystemProperties(
            "ADMINISTRATION_ANALYTICS_KEY=configured-key",
            "ADMINISTRATION_ANALYTICS_SECRET=configured-secret")
        .run(
            context -> {
              var configuration = context.getBean(S3Client.class).serviceClientConfiguration();
              var credentials = configuration.credentialsProvider().resolveIdentity().join();

              assertThat(configuration.region()).isEqualTo(EU_CENTRAL_1);
              assertThat(credentials)
                  .extracting(
                      AwsCredentialsIdentity::accessKeyId, AwsCredentialsIdentity::secretAccessKey)
                  .containsExactly("configured-key", "configured-secret");
            });
  }
}
