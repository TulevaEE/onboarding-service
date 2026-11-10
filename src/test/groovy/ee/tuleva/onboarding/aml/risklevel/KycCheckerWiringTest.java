package ee.tuleva.onboarding.aml.risklevel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ee.tuleva.onboarding.kyc.KycCheckRepository;
import ee.tuleva.onboarding.kyc.KycChecker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

class KycCheckerWiringTest {

  @Test
  void assessesThroughAmlServiceWhenAmlServiceIsEnabled() {
    contextRunner()
        .withPropertyValues("aml-service.enabled=true")
        .run(
            context ->
                assertThat(context)
                    .getBean(KycChecker.class)
                    .isInstanceOf(AmlServiceKycChecker.class));
  }

  @Test
  void assessesWithTheSqlWhenAmlServiceIsNotEnabled() {
    contextRunner()
        .withPropertyValues("aml-service.enabled=false")
        .run(
            context ->
                assertThat(context)
                    .getBean(KycChecker.class)
                    .isInstanceOf(KycCheckRepository.class));
  }

  @Test
  void assessesWithTheSqlWhenAmlServiceIsNotConfigured() {
    contextRunner()
        .run(
            context ->
                assertThat(context)
                    .getBean(KycChecker.class)
                    .isInstanceOf(KycCheckRepository.class));
  }

  private ApplicationContextRunner contextRunner() {
    return new ApplicationContextRunner()
        .withUserConfiguration(
            AmlServiceConfiguration.class,
            AmlServiceKycCheckerConfiguration.class,
            KycCheckRepository.class)
        .withBean(JdbcClient.class, () -> mock(JdbcClient.class))
        .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
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
