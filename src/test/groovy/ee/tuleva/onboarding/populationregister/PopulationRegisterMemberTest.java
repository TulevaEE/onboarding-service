package ee.tuleva.onboarding.populationregister;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PopulationRegisterMemberTest {

  @Test
  void readsTheMemberCodeFromTheXRoadClientId() {
    var member =
        new PopulationRegisterMember(
            new PopulationRegisterProperties("http://register.test", "EE/COM/00000000/subsystem"));

    assertThat(member.memberCode()).isEqualTo("00000000");
  }

  @Test
  void refusesAClientIdWithoutAMemberCode() {
    var member =
        new PopulationRegisterMember(
            new PopulationRegisterProperties("http://register.test", "client-id"));

    assertThatThrownBy(member::memberCode).isInstanceOf(IllegalStateException.class);
  }
}
