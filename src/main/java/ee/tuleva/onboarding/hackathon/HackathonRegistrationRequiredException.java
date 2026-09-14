package ee.tuleva.onboarding.hackathon;

public class HackathonRegistrationRequiredException extends HackathonException {

  public HackathonRegistrationRequiredException(Long userId) {
    super(
        "HACKATHON_REGISTRATION_REQUIRED",
        "Hackathon registration is required before submitting an idea: userId=" + userId);
  }
}
