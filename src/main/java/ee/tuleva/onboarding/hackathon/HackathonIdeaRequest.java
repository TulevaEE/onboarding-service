package ee.tuleva.onboarding.hackathon;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record HackathonIdeaRequest(
    @NotNull HackathonChallenge challenge,
    @NotBlank @Size(max = 2000) String problem,
    @NotBlank @Size(max = 2000) String solution,
    @Nullable @Size(max = 2000) String progress,
    @NotNull List<@NotNull HackathonSkill> neededSkills,
    @Nullable @Size(max = 2000) String additionalInfo) {

  public HackathonIdeaRequest {
    if (problem != null) {
      problem = problem.strip();
    }
    if (solution != null) {
      solution = solution.strip();
    }
    progress = strippedOrNull(progress);
    additionalInfo = strippedOrNull(additionalInfo);
    if (neededSkills != null) {
      neededSkills = neededSkills.stream().distinct().toList();
    }
  }

  public HackathonIdea toIdea(Long userId, Instant now) {
    return HackathonIdea.builder()
        .userId(userId)
        .challenge(challenge)
        .problem(problem)
        .solution(solution)
        .progress(progress)
        .neededSkills(neededSkills)
        .additionalInfo(additionalInfo)
        .createdTime(now)
        .build();
  }

  private static @Nullable String strippedOrNull(@Nullable String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
