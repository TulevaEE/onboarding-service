package ee.tuleva.onboarding.kyb.survey;

import lombok.Getter;

class OnboardingNotAllowedException extends RuntimeException {

  @Getter private final BlockedReason reason;

  OnboardingNotAllowedException(String registryCode, BlockedReason reason) {
    super(
        "Onboarding not allowed for company: registryCode=" + registryCode + ", reason=" + reason);
    this.reason = reason;
  }
}
