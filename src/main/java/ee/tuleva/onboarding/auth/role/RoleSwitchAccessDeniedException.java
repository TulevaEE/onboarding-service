package ee.tuleva.onboarding.auth.role;

public class RoleSwitchAccessDeniedException extends RuntimeException {

  public RoleSwitchAccessDeniedException(String personalCode, String targetCode) {
    super("Role switch denied: personalCode=" + personalCode + ", targetCode=" + targetCode);
  }
}
