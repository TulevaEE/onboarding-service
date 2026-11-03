package ee.tuleva.onboarding.nudge;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.nudge.ActingFor.CHILD;
import static ee.tuleva.onboarding.nudge.ActingFor.COMPANY;
import static ee.tuleva.onboarding.nudge.ActingFor.SELF;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.user.User;
import org.junit.jupiter.api.Test;

class NudgeAccountTest {

  private final User parent = sampleUser().build();

  @Test
  void thePersonsOwnAccountIsActingForThemselves() {
    assertThat(NudgeAccount.self(parent).actingFor(parent)).isEqualTo(SELF);
  }

  @Test
  void anotherPersonsAccountIsActingForAChild() {
    assertThat(NudgeAccount.person("51111111111").actingFor(parent)).isEqualTo(CHILD);
  }

  @Test
  void aCompanyAccountIsActingForACompany() {
    assertThat(NudgeAccount.company("12345678").actingFor(parent)).isEqualTo(COMPANY);
  }
}
