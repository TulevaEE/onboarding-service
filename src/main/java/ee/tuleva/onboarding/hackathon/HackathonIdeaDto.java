package ee.tuleva.onboarding.hackathon;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record HackathonIdeaDto(
    Long id,
    HackathonChallenge challenge,
    String problem,
    String solution,
    @Nullable String progress,
    List<HackathonSkill> neededSkills,
    @Nullable String additionalInfo,
    Instant createdTime) {

  public static HackathonIdeaDto from(HackathonIdea idea) {
    return new HackathonIdeaDto(
        idea.getId(),
        idea.getChallenge(),
        idea.getProblem(),
        idea.getSolution(),
        idea.getProgress(),
        idea.getNeededSkills(),
        idea.getAdditionalInfo(),
        idea.getCreatedTime());
  }
}
