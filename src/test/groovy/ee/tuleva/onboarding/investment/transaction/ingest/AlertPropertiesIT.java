package ee.tuleva.onboarding.investment.transaction.ingest;

import static org.assertj.core.api.Assertions.assertThat;

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
}
