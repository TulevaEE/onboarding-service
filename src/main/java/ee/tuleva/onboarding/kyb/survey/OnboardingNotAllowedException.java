package ee.tuleva.onboarding.kyb.survey;

class OnboardingNotAllowedException extends RuntimeException {

  OnboardingNotAllowedException(String registryCode) {
    super("Onboarding not allowed for company: registryCode=" + registryCode);
  }
}
