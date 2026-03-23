package ee.tuleva.onboarding.company;

public class CompanyAccessDeniedException extends RuntimeException {

  public CompanyAccessDeniedException(String personalCode, String registryCode) {
    super("No relationship: personalCode=" + personalCode + ", registryCode=" + registryCode);
  }
}
