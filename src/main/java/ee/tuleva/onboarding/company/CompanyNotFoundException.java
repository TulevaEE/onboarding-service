package ee.tuleva.onboarding.company;

public class CompanyNotFoundException extends RuntimeException {

  public CompanyNotFoundException(String registryCode) {
    super("Company not found: registryCode=" + registryCode);
  }
}
