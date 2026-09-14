package ee.tuleva.onboarding.hackathon;

public class HackathonRegistrationRequiredException extends RuntimeException {

  public HackathonRegistrationRequiredException(Long userId) {
    super("Hackathon registration is required before submitting an idea: userId=" + userId);
  }
}
