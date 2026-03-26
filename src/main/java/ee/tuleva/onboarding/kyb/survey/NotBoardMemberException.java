package ee.tuleva.onboarding.kyb.survey;

class NotBoardMemberException extends RuntimeException {

  NotBoardMemberException(String registryCode, String personalCode) {
    super(
        "Person is not a board member of the company: registryCode="
            + registryCode
            + ", personalCode="
            + personalCode);
  }
}
