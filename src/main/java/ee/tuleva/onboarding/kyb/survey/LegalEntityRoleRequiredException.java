package ee.tuleva.onboarding.kyb.survey;

class LegalEntityRoleRequiredException extends RuntimeException {

  LegalEntityRoleRequiredException(String personalCode) {
    super("Legal entity role required: personalCode=" + personalCode);
  }
}
