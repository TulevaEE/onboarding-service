package ee.tuleva.onboarding.accounting.directo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DirectoPropertiesTest {

  private final DirectoProperties properties =
      new DirectoProperties(
          "https://directo.test/apidirect",
          Map.of("TULEVA_FONDID", "company-key", "FUND_B", "", "FUND_A", " "),
          Duration.ofMillis(1));

  @Test
  void namesTheEnvironmentVariableOfEveryMissingKey() {
    assertThat(properties.missingKeyEnvironmentVariables())
        .containsExactly("DIRECTO_API_KEY_FUND_A", "DIRECTO_API_KEY_FUND_B");
  }

  @Test
  void listsEveryConfiguredEntityInNameOrder() {
    assertThat(properties.entities()).containsExactly("FUND_A", "FUND_B", "TULEVA_FONDID");
  }

  @Test
  void givesTheKeyOfAnEntityAndRefusesAMissingOne() {
    assertThat(properties.apiKey("TULEVA_FONDID")).isEqualTo("company-key");
    assertThatThrownBy(() -> properties.apiKey("FUND_A")).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> properties.apiKey("UNCONFIGURED"))
        .isInstanceOf(IllegalStateException.class);
  }
}
