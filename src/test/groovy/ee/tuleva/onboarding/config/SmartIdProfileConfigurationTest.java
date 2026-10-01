package ee.tuleva.onboarding.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;
import static org.springframework.core.env.StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;

class SmartIdProfileConfigurationTest {

  @Test
  void productionTalksToTheLiveRpApiV3WithTheLiveSchemeAndCertificates() {
    ConfigurableEnvironment production = environmentFor("production");

    assertThat(production.getProperty("smartid.hostUrl"))
        .isEqualTo("https://rp-api.smart-id.com/v3/");
    assertThat(production.getProperty("smartid.scheme-name")).isEqualTo("smart-id");
    assertThat(production.getProperty("smartid.trusted-ca-certificates"))
        .isEqualTo("classpath:smart-id/live/*.pem");
  }

  @Test
  void stagingTalksToTheDemoRpApiV3WithTheDemoSchemeAndCertificates() {
    ConfigurableEnvironment staging = environmentFor("staging");

    assertThat(staging.getProperty("smartid.hostUrl"))
        .isEqualTo("https://sid.demo.sk.ee/smart-id-rp/v3/");
    assertThat(staging.getProperty("smartid.scheme-name")).isEqualTo("smart-id-demo");
    assertThat(staging.getProperty("smartid.trusted-ca-certificates"))
        .isEqualTo("classpath:smart-id/demo/*.pem");
  }

  private static ConfigurableEnvironment environmentFor(String profile) {
    var environment = new StandardEnvironment();
    environment.getPropertySources().remove(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
    environment.getPropertySources().remove(SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
    ConfigDataEnvironmentPostProcessor.applyTo(
        environment, new DefaultResourceLoader(), null, List.of(profile));
    return environment;
  }
}
