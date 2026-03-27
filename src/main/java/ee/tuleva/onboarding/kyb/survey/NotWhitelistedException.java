package ee.tuleva.onboarding.kyb.survey;

class NotWhitelistedException extends RuntimeException {

  NotWhitelistedException(String registryCode) {
    super("Company is not whitelisted for onboarding: registryCode=" + registryCode);
  }
}
