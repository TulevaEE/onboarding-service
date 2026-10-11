package ee.tuleva.onboarding.hackathon;

import static ee.tuleva.onboarding.hackathon.HackathonChallenge.FAIR_LENDING;
import static ee.tuleva.onboarding.hackathon.HackathonSkill.DATA_AND_AI;
import static ee.tuleva.onboarding.hackathon.HackathonSkill.DESIGN;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class HackathonIdeaRequestTest {

  private static final Instant NOW = Instant.parse("2026-09-15T10:00:00Z");

  private HackathonIdeaRequest request(
      String problem, String solution, String progress, String additionalInfo) {
    return new HackathonIdeaRequest(
        FAIR_LENDING, problem, solution, progress, List.of(DESIGN), additionalInfo);
  }

  @Test
  void blankOptionalFieldsBecomeNullRatherThanEmptyStrings() {
    var request = request("Laenu vahetamine on tülikas", "Krediidiliin", "  ", "\t\n");

    assertThat(request.progress()).isNull();
    assertThat(request.additionalInfo()).isNull();
  }

  @Test
  void surroundingWhitespaceIsStripped() {
    var request =
        request(
            "  Laenu vahetamine on tülikas ",
            " Krediidiliin  ",
            " Prototüüp on olemas ",
            " Otsin arendajat ");

    assertThat(request.problem()).isEqualTo("Laenu vahetamine on tülikas");
    assertThat(request.solution()).isEqualTo("Krediidiliin");
    assertThat(request.progress()).isEqualTo("Prototüüp on olemas");
    assertThat(request.additionalInfo()).isEqualTo("Otsin arendajat");
  }

  @Test
  void repeatedNeededSkillsAreCollapsed() {
    var request =
        new HackathonIdeaRequest(
            FAIR_LENDING,
            "Laenu vahetamine on tülikas",
            "Krediidiliin",
            null,
            List.of(DESIGN, DATA_AND_AI, DESIGN),
            null);

    assertThat(request.neededSkills()).containsExactly(DESIGN, DATA_AND_AI);
  }

  @Test
  void toIdea_keepsTheAnswersAndTheSubmitter() {
    var request = request("Laenu vahetamine on tülikas", "Krediidiliin", null, null);

    assertThat(request.toIdea(999L, NOW))
        .isEqualTo(
            HackathonIdea.builder()
                .userId(999L)
                .challenge(FAIR_LENDING)
                .problem("Laenu vahetamine on tülikas")
                .solution("Krediidiliin")
                .progress(null)
                .neededSkills(List.of(DESIGN))
                .additionalInfo(null)
                .createdTime(NOW)
                .build());
  }
}
