package ee.tuleva.onboarding.investment.transaction.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AlertPropertiesIT {

  @Autowired private AlertProperties alertProperties;

  @Test
  void bindsToAndCcFromApplicationYaml() {
    assertThat(alertProperties.to()).containsExactly("funds@tuleva.ee");
    assertThat(alertProperties.cc()).containsExactly("taavi.pertman@tuleva.ee");
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
