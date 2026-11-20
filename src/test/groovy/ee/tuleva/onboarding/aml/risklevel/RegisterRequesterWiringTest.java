package ee.tuleva.onboarding.aml.risklevel;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.aml.RegisterRequester;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RegisterRequesterWiringTest {

  @Test
  void asksAmlServiceWhoTheRequesterIs() {
    contextRunner()
        .run(
            context ->
                assertThat(context)
                    .getBean(RegisterRequester.class)
                    .isInstanceOf(AmlServiceRegisterRequester.class));
  }

  @ParameterizedTest
  @ValueSource(strings = {"dev", "staging"})
  void designatesNoRequesterInDevAndStaging(String profile) {
    contextRunner()
        .withPropertyValues("spring.profiles.active=" + profile)
        .run(
            context ->
                assertThat(context.getBean(RegisterRequester.class).designatedXRoadUserId())
                    .isEqualTo(Optional.empty()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"production", "test", "ci,test", "pg,test"})
  void asksAmlServiceInEveryOtherProfile(String profiles) {
    contextRunner()
        .withPropertyValues("spring.profiles.active=" + profiles)
        .run(
            context ->
                assertThat(context)
                    .getBean(RegisterRequester.class)
                    .isInstanceOf(AmlServiceRegisterRequester.class));
  }

  private ApplicationContextRunner contextRunner() {
    return new ApplicationContextRunner()
        .withUserConfiguration(
            AmlServiceConfiguration.class,
            AmlServiceRegisterRequesterConfiguration.class,
            DevRegisterRequester.class)
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
