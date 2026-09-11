package ee.tuleva.onboarding.investment.transaction.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AlertPropertiesIT {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withInitializer(new ConfigDataApplicationContextInitializer())
          .withUserConfiguration(AlertConfiguration.class);

  @Test
  void bindsToAndCcFromApplicationYaml() {
    contextRunner.run(
        context -> {
          AlertProperties alertProperties = context.getBean(AlertProperties.class);
          assertThat(alertProperties.to()).containsExactly("funds@tuleva.ee");
          assertThat(alertProperties.cc())
              .hasSize(1)
              .allSatisfy(cc -> assertThat(cc).endsWith("@tuleva.ee"));
        });
  }

  @Test
  void rejectsConstructionWhenToIsNull() {
    assertThatThrownBy(() -> new AlertProperties(null, List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("transaction-registry.alerts.to");
  }

  @Test
  void rejectsConstructionWhenToIsEmpty() {
    assertThatThrownBy(() -> new AlertProperties(List.of(), List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("transaction-registry.alerts.to");
  }

  @Test
  void allowsNullOrEmptyCc() {
    var withNullCc = new AlertProperties(List.of("ops@example.com"), null);
    assertThat(withNullCc.cc()).isEmpty();

    var withEmptyCc = new AlertProperties(List.of("ops@example.com"), List.of());
    assertThat(withEmptyCc.cc()).isEmpty();
  }
}
