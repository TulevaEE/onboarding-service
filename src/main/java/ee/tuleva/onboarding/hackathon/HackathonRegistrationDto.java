package ee.tuleva.onboarding.hackathon;

import ee.tuleva.onboarding.user.User;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record HackathonRegistrationDto(
    boolean registered,
    boolean open,
    Instant deadline,
    @Nullable String email,
    @Nullable String phoneNumber,
    @Nullable HackathonRole role,
    List<HackathonSkill> skills,
    @Nullable String otherSkills,
    List<HackathonChallenge> challenges,
    @Nullable HackathonParticipation participation,
    @Nullable String idea,
    @Nullable String linkedinUrl,
    @Nullable HackathonTshirtColor tshirtColor,
    @Nullable HackathonTshirtSize tshirtSize,
    boolean termsAccepted) {

  public static HackathonRegistrationDto from(
      HackathonRegistration registration, boolean open, Instant deadline) {
    return new HackathonRegistrationDto(
        true,
        open,
        deadline,
        registration.getEmail(),
        registration.getPhoneNumber(),
        registration.getRole(),
        registration.getSkills(),
        registration.getOtherSkills(),
        registration.getChallenges(),
        registration.getParticipation(),
        registration.getIdea(),
        registration.getLinkedinUrl(),
        registration.getTshirtColor(),
        registration.getTshirtSize(),
        registration.getTermsAcceptedTime() != null);
  }

  public static HackathonRegistrationDto prefilledFrom(User user, boolean open, Instant deadline) {
    return new HackathonRegistrationDto(
        false,
        open,
        deadline,
        user.getEmail(),
        user.getPhoneNumber(),
        null,
        List.of(),
        null,
        List.of(),
        null,
        null,
        null,
        null,
        null,
        false);
  }
}
