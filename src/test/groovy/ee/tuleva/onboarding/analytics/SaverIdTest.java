package ee.tuleva.onboarding.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SaverIdTest {

  @Test
  void personBuildsAPersonTypeSaverIdWithTheGivenCode() {
    SaverId saverId = SaverId.person("38888888888");

    assertThat(saverId).isEqualTo(new SaverId(SaverId.Type.PERSON, "38888888888"));
  }
}
