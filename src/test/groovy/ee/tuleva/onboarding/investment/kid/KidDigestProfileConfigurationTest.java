package ee.tuleva.onboarding.investment.kid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;
import static org.springframework.core.env.StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.DefaultResourceLoader;

class KidDigestProfileConfigurationTest {

  private static final String DIGEST_ENABLED = "investment.kid.digest.enabled";

  @Test
  void productionPostsTheKidDigest() {
    assertThat(digestEnabledIn(List.of("production"))).isTrue();
  }

  @Test
  void stagingKeepsTheKidDigestOutOfTheChannelProductionReadsItsIdsFrom() {
    assertThat(digestEnabledIn(List.of("staging"))).isFalse();
  }

  @Test
  void localRunsDoNotPostTheKidDigest() {
    assertThat(digestEnabledIn(List.of())).isFalse();
    assertThat(digestEnabledIn(List.of("dev"))).isFalse();
  }

  private static boolean digestEnabledIn(List<String> profiles) {
    return Binder.get(environmentFor(profiles)).bind(DIGEST_ENABLED, Boolean.class).get();
  }

  private static ConfigurableEnvironment environmentFor(List<String> profiles) {
    var environment = new StandardEnvironment();
    var propertySources = environment.getPropertySources();
    propertySources.remove(SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
    propertySources.replace(
        SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
        new SystemEnvironmentPropertySource(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, Map.of()));
    propertySources.addFirst(
        new MapPropertySource(
            "onlyTheDeployedConfiguration",
            Map.of("spring.config.location", "classpath:/application.yml")));
    ConfigDataEnvironmentPostProcessor.applyTo(
        environment, new DefaultResourceLoader(), null, profiles);
    return environment;
  }
}
