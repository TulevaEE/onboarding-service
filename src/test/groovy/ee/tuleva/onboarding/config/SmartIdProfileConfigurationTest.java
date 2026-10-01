package ee.tuleva.onboarding.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;
import static org.springframework.core.env.StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
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

  @Test
  void productionReturnsSameDeviceLoginsToTheProductionFrontend() {
    assertThat(environmentFor("production").getProperty("smartid.callback-url"))
        .isEqualTo("https://pension.tuleva.ee/login/smart-id/callback");
  }

  @Test
  void stagingReturnsSameDeviceLoginsToTheStagingFrontendRatherThanProduction() {
    assertThat(environmentFor("staging").getProperty("smartid.callback-url"))
        .isEqualTo("https://staging.tuleva.ee/login/smart-id/callback");
  }

  @ParameterizedTest
  @ValueSource(strings = {"default", "dev", "staging", "production"})
  void noProfileLogsTheSmartIdClientsRequestAndResponseBodies(String profile) {
    assertThat(environmentFor(profile).getProperty("logging.level.ee.sk.smartid")).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"default", "dev", "staging"})
  void mobileIdTalksToItsDemoHostWithItsDemoRelyingPartyOutsideProduction(String profile) {
    ConfigurableEnvironment environment =
        environmentFor(
            profile,
            Map.of(
                "SMARTID_RELYING_PARTY_UUID", "11111111-2222-4333-8444-555555555555",
                "SMARTID_RELYING_PARTY_NAME", "Production RP"));

    assertThat(mobileIdRelyingParty(environment))
        .containsExactly(
            "https://tsp.demo.sk.ee/mid-api", "00000000-0000-0000-0000-000000000000", "DEMO");
  }

  @Test
  void productionTalksToTheLiveMobileIdHostWithTheRelyingPartySecretsSmartIdUses() {
    ConfigurableEnvironment production =
        environmentFor(
            "production",
            Map.of(
                "SMARTID_RELYING_PARTY_UUID", "11111111-2222-4333-8444-555555555555",
                "SMARTID_RELYING_PARTY_NAME", "Production RP"));

    assertThat(mobileIdRelyingParty(production))
        .containsExactly(
            "https://mid.sk.ee/mid-api", "11111111-2222-4333-8444-555555555555", "Production RP");
    assertThat(production.getProperty("smartid.relyingPartyUUID"))
        .isEqualTo("11111111-2222-4333-8444-555555555555");
    assertThat(production.getProperty("smartid.relyingPartyName")).isEqualTo("Production RP");
  }

  private static List<String> mobileIdRelyingParty(ConfigurableEnvironment environment) {
    return Stream.of(
            "mobile-id.hostUrl", "mobile-id.relyingPartyUUID", "mobile-id.relyingPartyName")
        .map(environment::getProperty)
        .toList();
  }

  private static ConfigurableEnvironment environmentFor(String profile) {
    return environmentFor(profile, Map.of());
  }

  private static ConfigurableEnvironment environmentFor(
      String profile, Map<String, Object> environmentVariables) {
    var environment = new StandardEnvironment();
    environment.getPropertySources().remove(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
    environment.getPropertySources().remove(SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
    environment
        .getPropertySources()
        .addFirst(new MapPropertySource("environmentVariables", environmentVariables));
    ConfigDataEnvironmentPostProcessor.applyTo(
        environment, new DefaultResourceLoader(), null, List.of(profile));
    return environment;
  }
}
