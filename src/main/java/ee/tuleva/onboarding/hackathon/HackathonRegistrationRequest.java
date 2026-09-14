package ee.tuleva.onboarding.hackathon;

import static ee.tuleva.onboarding.hackathon.HackathonTshirtColor.NONE;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record HackathonRegistrationRequest(
    @NotBlank @Email @Size(max = 255) String email,
    @Nullable @Size(max = 255) String phoneNumber,
    @NotNull HackathonRole role,
    @NotNull List<@NotNull HackathonSkill> skills,
    @Nullable @Size(max = 500) String otherSkills,
    @NotNull List<@NotNull HackathonChallenge> challenges,
    @NotNull HackathonParticipation participation,
    @Nullable @Size(max = 500) String idea,
    @Nullable @Size(max = 500) String linkedinUrl,
    @NotNull HackathonTshirtColor tshirtColor,
    @Nullable HackathonTshirtSize tshirtSize,
    @AssertTrue boolean termsAccepted) {

  public HackathonRegistrationRequest {
    if (email != null) {
      email = email.strip();
    }
    phoneNumber = strippedOrNull(phoneNumber);
    idea = strippedOrNull(idea);
    linkedinUrl = strippedOrNull(linkedinUrl);
    otherSkills = strippedOrNull(otherSkills);
    if (skills != null) {
      skills = skills.stream().distinct().toList();
    }
    if (challenges != null) {
      challenges = challenges.stream().distinct().toList();
    }
    if (tshirtColor == NONE) {
      tshirtSize = null;
    }
  }

  @AssertTrue
  public boolean isTshirtSizeChosenForAShirt() {
    return tshirtColor == null || tshirtColor == NONE || tshirtSize != null;
  }

  public HackathonRegistration toRegistration(Long userId, Instant now) {
    return HackathonRegistration.builder()
        .userId(userId)
        .email(email)
        .phoneNumber(phoneNumber)
        .role(role)
        .skills(skills)
        .otherSkills(otherSkills)
        .challenges(challenges)
        .participation(participation)
        .idea(idea)
        .linkedinUrl(linkedinUrl)
        .tshirtColor(tshirtColor)
        .tshirtSize(tshirtSize)
        .termsAcceptedTime(now)
        .createdTime(now)
        .updatedTime(now)
        .build();
  }

  private static @Nullable String strippedOrNull(@Nullable String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
