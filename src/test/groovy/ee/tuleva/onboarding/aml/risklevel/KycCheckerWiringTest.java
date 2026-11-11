package ee.tuleva.onboarding.aml.risklevel;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.kyc.KycChecker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class KycCheckerWiringTest {

  @Test
  void assessesThroughAmlService() {
    contextRunner()
        .run(
            context ->
                assertThat(context)
                    .getBean(KycChecker.class)
                    .isInstanceOf(AmlServiceKycChecker.class));
  }

  @ParameterizedTest
  @ValueSource(strings = {"dev", "staging"})
  void answersWithTheAmlServiceMockInDevAndStaging(String profile) {
    contextRunner()
        .withPropertyValues("spring.profiles.active=" + profile)
        .run(
            context ->
                assertThat(context).getBean(KycChecker.class).isInstanceOf(DevKycChecker.class));
  }

  @ParameterizedTest
  @ValueSource(strings = {"production", "test", "ci,test", "pg,test"})
  void assessesThroughAmlServiceInEveryOtherProfile(String profiles) {
    contextRunner()
        .withPropertyValues("spring.profiles.active=" + profiles)
        .run(
            context ->
                assertThat(context)
                    .getBean(KycChecker.class)
                    .isInstanceOf(AmlServiceKycChecker.class));
  }

  private ApplicationContextRunner contextRunner() {
    return new ApplicationContextRunner()
        .withUserConfiguration(
            AmlServiceConfiguration.class,
            AmlServiceKycCheckerConfiguration.class,
            DevKycChecker.class)
        .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class))
        .withPropertyValues(
            "aml-service.url=https://aml-service.test",
            "aml-service.connect-timeout=5s",
            "aml-service.read-timeout=60s",
            "aml-service.high-risk-connect-timeout=1s",
            "aml-service.high-risk-read-timeout=2s",
            "aml-service.kyc-onboarding-connect-timeout=1s",
            "aml-service.kyc-onboarding-read-timeout=3s");
  }
}
