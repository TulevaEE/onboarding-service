package ee.tuleva.onboarding.hackathon;

import static ee.tuleva.onboarding.hackathon.HackathonChallenge.INSURANCE;
import static ee.tuleva.onboarding.hackathon.HackathonParticipation.LOOKING_FOR_TEAM;
import static ee.tuleva.onboarding.hackathon.HackathonRole.PARTICIPANT;
import static ee.tuleva.onboarding.hackathon.HackathonSkill.DESIGN;
import static ee.tuleva.onboarding.hackathon.HackathonSkill.SOFTWARE_DEVELOPMENT;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class HackathonRegistrationRequestTest {

  private HackathonRegistrationRequest request(
      String email, String phoneNumber, String idea, String linkedinUrl) {
    return new HackathonRegistrationRequest(
        email, phoneNumber, PARTICIPANT, List.of(), List.of(), LOOKING_FOR_TEAM, idea, linkedinUrl);
  }

  @Test
  void blankOptionalFieldsBecomeNullRatherThanEmptyStrings() {
    var request = request("participant@example.com", "   ", "", "\t\n");

    assertThat(request.phoneNumber()).isNull();
    assertThat(request.idea()).isNull();
    assertThat(request.linkedinUrl()).isNull();
  }

  @Test
  void surroundingWhitespaceIsStripped() {
    var request =
        request(
            "  participant@example.com  ",
            " +37255555555 ",
            "  Fondiosaku tagatisel krediidiliin  ",
            " https://linkedin.com/in/example ");

    assertThat(request.email()).isEqualTo("participant@example.com");
    assertThat(request.phoneNumber()).isEqualTo("+37255555555");
    assertThat(request.idea()).isEqualTo("Fondiosaku tagatisel krediidiliin");
    assertThat(request.linkedinUrl()).isEqualTo("https://linkedin.com/in/example");
  }

  @Test
  void repeatedSkillsAndChallengesAreCollapsed() {
    var request =
        new HackathonRegistrationRequest(
            "participant@example.com",
            null,
            PARTICIPANT,
            List.of(DESIGN, SOFTWARE_DEVELOPMENT, DESIGN, DESIGN),
            List.of(INSURANCE, INSURANCE),
            LOOKING_FOR_TEAM,
            null,
            null);

    assertThat(request.skills()).containsExactly(DESIGN, SOFTWARE_DEVELOPMENT);
    assertThat(request.challenges()).containsExactly(INSURANCE);
  }

  @Test
  void missingOptionalFieldsStayNull() {
    var request = request("participant@example.com", null, null, null);

    assertThat(request.phoneNumber()).isNull();
    assertThat(request.idea()).isNull();
    assertThat(request.linkedinUrl()).isNull();
  }
}
